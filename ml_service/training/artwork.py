"""Generated cover art for demo artists.

No photographs. A seeded catalogue must not carry pictures of real people —
there is no licence for it and no consent behind it — and downloading faces from
an image host to fill a demo database would be both. So each artist gets
abstract artwork instead: a genre-keyed gradient, a geometric motif and their
monogram.

It is deterministic in the artist's slug, so re-running the seeder does not
reshuffle everyone's picture, and it is drawn offline with Pillow, so nothing
here touches the network.
"""

from __future__ import annotations

import hashlib
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SIZE = 640

# A palette per genre family, so a blues act does not look like a techno act.
# Each entry is (top-left, bottom-right, accent).
PALETTES = {
    "Rock":       ((28, 26, 38), (120, 34, 44), (233, 96, 74)),
    "Jazz":       ((22, 26, 34), (74, 52, 110), (222, 176, 92)),
    "Folk":       ((32, 34, 26), (92, 84, 46), (214, 186, 118)),
    "Pop":        ((36, 24, 48), (176, 52, 118), (250, 196, 92)),
    "Classical":  ((24, 28, 38), (58, 72, 112), (206, 190, 150)),
    "Hip-Hop":    ((20, 20, 22), (56, 56, 62), (238, 196, 60)),
    "Electronic": ((14, 22, 34), (22, 88, 118), (86, 226, 214)),
    "Blues":      ((18, 24, 40), (34, 62, 106), (110, 158, 216)),
}
DEFAULT_PALETTE = ((26, 26, 32), (70, 70, 86), (200, 200, 210))


def _rng_from(seed_text: str) -> "list[int]":
    """A short deterministic byte stream, so the same artist always gets the same art."""
    return list(hashlib.sha256(seed_text.encode("utf-8")).digest())


def _gradient(top: tuple, bottom: tuple, angle_bytes: int) -> Image.Image:
    """A smooth diagonal wash between two colours."""
    base = Image.new("RGB", (SIZE, SIZE), top)
    draw = ImageDraw.Draw(base)

    # A diagonal rather than a straight vertical: it reads less like a default.
    # Each line is drawn well past both edges, because shifting a line by its
    # lean and starting it at x=0 left an unpainted wedge in two corners.
    lean = (angle_bytes / 255.0) * 0.6 - 0.3
    overhang = round(abs(lean) * SIZE) + 2
    for y in range(SIZE):
        t = y / (SIZE - 1)
        colour = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        offset = round(lean * (y - SIZE / 2))
        draw.line([(offset - overhang, y), (SIZE + offset + overhang, y)], fill=colour)
    return base


def _motif(image: Image.Image, accent: tuple, seed: list[int]) -> None:
    """Concentric arcs, bars or a starburst — whichever the seed picks."""
    draw = ImageDraw.Draw(image, "RGBA")
    kind = seed[0] % 3
    tint = accent + (64,)

    if kind == 0:  # concentric rings, off-centre
        cx = SIZE * (0.25 + (seed[1] / 255) * 0.5)
        cy = SIZE * (0.25 + (seed[2] / 255) * 0.5)
        for ring in range(6, 0, -1):
            r = ring * (SIZE / 11)
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=tint, width=3)

    elif kind == 1:  # vertical bars of varying height, like a level meter
        bars = 9 + seed[1] % 5
        gap = SIZE / bars
        for b in range(bars):
            height = (0.2 + ((seed[(b % 28) + 3] / 255) * 0.65)) * SIZE
            x0 = b * gap + gap * 0.22
            draw.rectangle([x0, SIZE - height, x0 + gap * 0.56, SIZE], fill=tint)

    else:  # starburst of thin rays
        cx, cy = SIZE * 0.5, SIZE * 0.55
        rays = 14 + seed[1] % 10
        for r in range(rays):
            angle = (2 * math.pi * r / rays) + (seed[2] / 255)
            draw.line(
                [cx, cy, cx + math.cos(angle) * SIZE, cy + math.sin(angle) * SIZE],
                fill=accent + (48,), width=2,
            )


def _monogram(image: Image.Image, initials: str, accent: tuple) -> None:
    draw = ImageDraw.Draw(image)

    # The container ships no TrueType fonts at all, so truetype() never matched
    # and this silently fell back to the default bitmap face at about eleven
    # pixels — a monogram invisible at any size the UI actually shows. Pillow 10
    # can scale the built-in face, which is enough for two letters.
    font = None
    for candidate in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        if Path(candidate).exists():
            font = ImageFont.truetype(candidate, 188)
            break
    if font is None:
        try:
            font = ImageFont.load_default(size=190)
        except TypeError:      # Pillow < 10.1 cannot size the default face
            font = ImageFont.load_default()

    box = draw.textbbox((0, 0), initials, font=font)
    x = (SIZE - (box[2] - box[0])) / 2 - box[0]
    y = (SIZE - (box[3] - box[1])) / 2 - box[1] - SIZE * 0.02

    # A soft shadow first, so the letters hold up over a busy motif.
    draw.text((x + 4, y + 4), initials, font=font, fill=(0, 0, 0, 120))
    draw.text((x, y), initials, font=font, fill=accent)


def cover_art(stage_name: str, slug: str, genre: str, out_path: Path) -> None:
    """Writes one artist's picture. Deterministic in the slug."""
    seed = _rng_from(slug)
    top, bottom, accent = PALETTES.get(genre, DEFAULT_PALETTE)

    image = _gradient(top, bottom, seed[4])
    _motif(image, accent, seed)

    initials = "".join(word[0] for word in stage_name.split() if word[0].isalnum())[:2].upper()
    _monogram(image, initials or "♪", accent)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    image.save(out_path, format="JPEG", quality=82, optimize=True)
