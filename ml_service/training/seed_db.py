"""Fills an empty database with a demo catalogue.

Explicitly opt-in, never automatic: it writes into the same `users`, `artists`
and `booking` tables the live application uses. It exists so search and ranking
can be demonstrated before the platform has real artists, and so the ranker's
behaviour can be inspected against a catalogue whose ground truth is known.

    python -m training.seed_db --artists 200

Every seeded account is marked with the email domain @seed.openmichub.local, so
`--clear` can remove exactly what this wrote and nothing else.
"""

from __future__ import annotations

import argparse
import logging
import math

import numpy as np

from app.db import connection
from training.generate import CITIES, GENRES, GeneratorConfig, generate_artists

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(levelname)-5s %(message)s")

SEED_DOMAIN = "seed.openmichub.local"

# bcrypt hash of "SeedArtist@123". Seeded accounts are demo data; they are
# verified so they appear in listings, and they all share this password.
SEED_PASSWORD_HASH = "$2a$10$54gPf2W4sUQAMD8nr8Moqe8ylugwB/AXanMm2vbPUlGLaBCsCTNm6"

BIO_TEMPLATES = [
    "{sub} performer with {years} years on stage. Known for {mood} sets that keep a room moving.",
    "{city}-based {sub} act. Plays weddings, corporate evenings and festivals across Nepal.",
    "{mood} {sub} music, live. {years} years of gigs, from small rooms to main stages.",
    "Award-nominated {sub} artist from {city}. Sets built around {mood} arrangements.",
    "{sub} specialist. {years} years performing; comfortable with crowds of any size.",
]
MOODS = ["warm", "high-energy", "intimate", "soulful", "upbeat", "mellow", "atmospheric", "raucous"]


def _ensure_genre_rows(conn) -> dict[str, int]:
    """Creates the genre and category rows, returning category name to id."""
    category_ids: dict[str, int] = {}
    with conn.cursor() as cur:
        for parent, subs in GENRES.items():
            cur.execute("SELECT id FROM genre WHERE name = %s", (parent,))
            row = cur.fetchone()
            if row:
                genre_id = row[0]
            else:
                cur.execute(
                    "INSERT INTO genre (name, description, slug, active, created_date) "
                    "VALUES (%s, %s, %s, true, NOW()) RETURNING id",
                    (parent, f"{parent} music", parent.lower().replace(" ", "-")),
                )
                genre_id = cur.fetchone()[0]

            for sub in subs:
                cur.execute("SELECT id FROM category WHERE name = %s", (sub,))
                row = cur.fetchone()
                if row:
                    category_id = row[0]
                else:
                    cur.execute(
                        "INSERT INTO category (name, description, active) VALUES (%s, %s, true) RETURNING id",
                        (sub, f"{sub} within {parent}"),
                    )
                    category_id = cur.fetchone()[0]

                cur.execute(
                    "INSERT INTO genre_categories (genre_id, categories_id) VALUES (%s, %s) "
                    "ON CONFLICT DO NOTHING",
                    (genre_id, category_id),
                )
                category_ids[sub] = category_id
    return category_ids


def _artist_role_id(conn) -> int:
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM roles WHERE name = 'ARTIST'")
        row = cur.fetchone()
        if not row:
            raise RuntimeError("The ARTIST role is missing. Start the API once so it seeds roles.")
        return row[0]


def clear(conn) -> int:
    """Removes only what this script created."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM users WHERE email LIKE %s", (f"%@{SEED_DOMAIN}",)
        )
        user_ids = [row[0] for row in cur.fetchall()]
        if not user_ids:
            return 0

        cur.execute("SELECT id FROM artists WHERE user_id = ANY(%s)", (user_ids,))
        artist_ids = [row[0] for row in cur.fetchall()]

        if artist_ids:
            cur.execute("DELETE FROM artist_embedding WHERE artist_id = ANY(%s)", (artist_ids,))
            cur.execute("DELETE FROM booking WHERE artist_id = ANY(%s)", (artist_ids,))
            cur.execute("DELETE FROM artists_genres WHERE artist_id = ANY(%s)", (artist_ids,))
            cur.execute("DELETE FROM virtual_coin WHERE artist_id = ANY(%s)", (artist_ids,))
            cur.execute("DELETE FROM artists WHERE id = ANY(%s)", (artist_ids,))

        cur.execute("DELETE FROM users_roles WHERE user_entity_id = ANY(%s)", (user_ids,))
        cur.execute("DELETE FROM users WHERE id = ANY(%s)", (user_ids,))
    conn.commit()
    return len(user_ids)


def seed(n_artists: int, seed_value: int = 42) -> int:
    rng = np.random.default_rng(seed_value)
    cfg = GeneratorConfig(n_artists=n_artists, seed=seed_value)
    catalogue = generate_artists(cfg, rng)

    with connection() as conn:
        category_ids = _ensure_genre_rows(conn)
        role_id = _artist_role_id(conn)
        conn.commit()

        created = 0
        with conn.cursor() as cur:
            for record in catalogue.to_dict("records"):
                index = record["artist_id"]
                email = f"artist{index}@{SEED_DOMAIN}"

                cur.execute("SELECT id FROM users WHERE email = %s", (email,))
                if cur.fetchone():
                    continue

                sub = record["sub_genres"][0]
                years = int(np.clip(record["completed_bookings"] // 6 + 1, 1, 20))
                bio = rng.choice(BIO_TEMPLATES).format(
                    sub=sub, city=record["city"], years=years, mood=rng.choice(MOODS),
                )
                stage_name = f"{rng.choice(['The', 'Kollektiv', 'Sound of', 'Echoes of', ''])} {sub} {index}".strip()

                cur.execute(
                    """
                    INSERT INTO users (full_name, email, password, phone_number, location,
                                       is_verified, is_active, created_date)
                    VALUES (%s, %s, %s, %s, %s, true, true, NOW())
                    RETURNING id
                    """,
                    (f"Seed Artist {index}", email, SEED_PASSWORD_HASH,
                     f"98{index:08d}", record["city"]),
                )
                user_id = cur.fetchone()[0]
                cur.execute(
                    "INSERT INTO users_roles (user_entity_id, roles_id) VALUES (%s, %s)",
                    (user_id, role_id),
                )

                cur.execute(
                    """
                    INSERT INTO artists (stage_name, bio, hourly_rate, rating, user_id, created_date)
                    VALUES (%s, %s, %s, %s, %s, NOW())
                    RETURNING id
                    """,
                    (stage_name[:255], bio[:255], float(record["hourly_rate"]),
                     float(record["rating"]), user_id),
                )
                artist_id = cur.fetchone()[0]

                for sub_genre in record["sub_genres"]:
                    if sub_genre in category_ids:
                        cur.execute(
                            "INSERT INTO artists_genres (artist_id, genres_id) VALUES (%s, %s) "
                            "ON CONFLICT DO NOTHING",
                            (artist_id, category_ids[sub_genre]),
                        )

                cur.execute(
                    "INSERT INTO virtual_coin (balance, artist_id, created_date) VALUES (0.0, %s, NOW())",
                    (artist_id,),
                )

                # A history of confirmed bookings, so the experience feature has
                # something real to read rather than a constant.
                for _ in range(min(int(record["completed_bookings"]), 40)):
                    cur.execute(
                        """
                        INSERT INTO booking (artist_id, user_id, event_date, start_time, end_time,
                                             venue, event_type, status, total_amount, created_date)
                        VALUES (%s, %s, CURRENT_DATE - (random() * 300)::int, '18:00', '21:00',
                                %s, %s, 'CONFIRMED', %s, NOW())
                        """,
                        (artist_id, user_id, f"{record['city']} Venue",
                         str(rng.choice(["Wedding", "Corporate", "Festival", "Restaurant"])),
                         float(record["hourly_rate"]) * 3),
                    )

                created += 1
                if created % 50 == 0:
                    conn.commit()
                    log.info("Seeded %d/%d artists", created, len(catalogue))

        conn.commit()
    return created


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed a demo artist catalogue.")
    parser.add_argument("--artists", type=int, default=200)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--clear", action="store_true",
                        help="Remove previously seeded demo data and exit")
    args = parser.parse_args()

    if args.clear:
        with connection() as conn:
            removed = clear(conn)
        print(f"Removed {removed} seeded accounts.")
        return

    created = seed(args.artists, args.seed)
    print(f"Seeded {created} artists. Rebuild embeddings next:")
    print("  curl -X POST http://localhost:8000/embeddings/rebuild")


if __name__ == "__main__":
    main()
