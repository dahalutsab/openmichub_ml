"""Measures how the encoder actually behaves on this catalogue.

The ranker's most important feature is `text_similarity` — a cosine between the
query and an artist's profile. Two numbers about that cosine have to be right
or the model is fitted against a signal it never receives:

  where the band sits    raw cosines occupy a narrow, encoder-specific range.
                         Mapping it onto the 0-1 scale training uses needs the
                         real endpoints, not a guess.

  how noisy it is        the generator simulates the cosine from a latent style
                         affinity plus noise. If that noise is too small, the
                         simulated signal separates good from bad artists better
                         than the real encoder can, and the model learns to
                         over-trust it.

Both are estimated the same way: embed genre-shaped queries, compare each one
against every artist, and split the cosines by whether the artist actually plays
that genre. The gap between those two distributions is the encoder's real
discriminability.

    docker compose exec ml python -m training.calibrate

Prints the settings to paste into `app/config.py`. Re-run after changing
`embedding_model` or materially changing the catalogue.
"""

from __future__ import annotations

import argparse
import math

import numpy as np

# Where the generator's latent style affinity puts the two classes. A genre
# match draws from 0.55 + 0.45*Beta(2,2), a non-match from 0.45*Beta(2,2).
TARGET_MATCH_MEAN = 0.775
TARGET_NONMATCH_MEAN = 0.225
STYLE_SD = 0.45 * math.sqrt(0.05)  # sd of 0.45*Beta(2,2)

QUERY_TEMPLATES = [
    "{genre} act for a wedding",
    "looking for a {genre} band",
    "{genre} musician for a private party",
    "hire a {genre} performer",
    "{genre} for a corporate event",
]


def measure() -> dict:
    from app.embedder import embed, embed_one
    from app.repository import artist_document, fetch_artists

    artists = fetch_artists()
    if len(artists) < 20:
        raise RuntimeError(f"Only {len(artists)} artists; too few to calibrate against.")

    vectors = embed([artist_document(a) for a in artists])
    parents = sorted({p for a in artists for p in (a.get("parent_genres") or []) if p})
    if not parents:
        raise RuntimeError("No parent genres in the catalogue; nothing to separate.")

    matching: list[float] = []
    non_matching: list[float] = []
    for genre in parents:
        for template in QUERY_TEMPLATES:
            query = embed_one(template.format(genre=genre))
            # Vectors are unit-normalised, so the dot product is the cosine.
            for artist, cosine in zip(artists, vectors @ query):
                bucket = matching if genre in (artist.get("parent_genres") or []) else non_matching
                bucket.append(float(cosine))

    match, non_match = np.array(matching), np.array(non_matching)
    separation = float(match.mean() - non_match.mean())
    if separation <= 1e-6:
        raise RuntimeError(
            "The encoder does not separate genre-matching artists from the rest. "
            "Calibrating against this would be meaningless."
        )

    # Affine map putting the two observed means onto the two target means.
    scale = (TARGET_MATCH_MEAN - TARGET_NONMATCH_MEAN) / separation
    low = float(non_match.mean() - TARGET_NONMATCH_MEAN / scale)
    high = float(low + 1.0 / scale)

    # Pooled within-class spread, on the calibrated scale.
    pooled_sd = float(np.sqrt((match.var() + non_match.var()) / 2.0))
    discriminability = separation / pooled_sd

    # The generator adds noise on top of the latent style affinity. Choose it so
    # the simulated signal separates the two classes exactly as well as this
    # encoder does, and no better.
    target_total_sd = (TARGET_MATCH_MEAN - TARGET_NONMATCH_MEAN) / discriminability
    noise_sd = float(math.sqrt(max(target_total_sd**2 - STYLE_SD**2, 1e-6)))

    return {
        "artists": len(artists),
        "genres": parents,
        "pairs": int(match.size + non_match.size),
        "match_mean": float(match.mean()), "match_sd": float(match.std()),
        "non_match_mean": float(non_match.mean()), "non_match_sd": float(non_match.std()),
        "separation": separation,
        "discriminability": float(discriminability),
        "similarity_cos_low": low,
        "similarity_cos_high": high,
        "similarity_noise_sd": noise_sd,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Measure the encoder's similarity calibration.")
    parser.parse_args()

    result = measure()
    print(f"catalogue          {result['artists']} artists, "
          f"{len(result['genres'])} parent genres, {result['pairs']:,} query-artist pairs")
    print()
    print(f"genre-matching     mean {result['match_mean']:.4f}  sd {result['match_sd']:.4f}")
    print(f"non-matching       mean {result['non_match_mean']:.4f}  sd {result['non_match_sd']:.4f}")
    print(f"separation         {result['separation']:.4f}")
    print(f"discriminability   {result['discriminability']:.3f}  (separation / pooled sd)")
    print()
    print("Paste into app/config.py:")
    print(f"    similarity_cos_low: float = {result['similarity_cos_low']:.4f}")
    print(f"    similarity_cos_high: float = {result['similarity_cos_high']:.4f}")
    print(f"    similarity_noise_sd: float = {result['similarity_noise_sd']:.3f}")


if __name__ == "__main__":
    main()
