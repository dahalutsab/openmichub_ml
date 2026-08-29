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
from app.taxonomy import CITIES, GENRES
from training.generate import GeneratorConfig, generate_artists

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(levelname)-5s %(message)s")

SEED_DOMAIN = "seed.openmichub.local"

# bcrypt hash of "Admin@123". Every seeded account shares it — this is demo
# data, and the accounts are marked verified so they appear in listings.
SEED_PASSWORD = "Admin@123"
SEED_PASSWORD_HASH = "$2a$10$54gPf2W4sUQAMD8nr8Moqe8ylugwB/AXanMm2vbPUlGLaBCsCTNm6"

# --- name and bio generation -------------------------------------------------
#
# The catalogue is demo data, but it has to read as a real roster: a UI reviewed
# against "Sound of Bebop 104" tells you nothing about how it handles an actual
# artist name. These pieces combine into names of realistic shape and length,
# mixing Nepali and English the way working bands in Kathmandu actually do.

NAME_PREFIX = [
    "The", "The", "The", "", "", "", "Kollektiv", "Project", "Trio", "Duo",
]

NEPALI_WORDS = [
    "Himal", "Bagmati", "Machhapuchhre", "Annapurna", "Newa", "Sarangi", "Madal",
    "Bansuri", "Jhyaure", "Dohori", "Rato", "Kalo", "Sunkoshi", "Trishuli",
    "Chautari", "Bhairav", "Malashree", "Gandaki", "Tamang", "Sherpa",
]

ENGLISH_WORDS = [
    "Velvet", "Neon", "Paper", "Copper", "Midnight", "Static", "Amber", "Slow",
    "Northern", "Wild", "Quiet", "Golden", "Electric", "Hollow", "Crimson",
    "Wandering", "Silver", "Broken", "Rising", "Distant", "Lantern", "Ember",
]

NOUNS = [
    "Kings", "Collective", "Sessions", "Orchestra", "Society", "Company",
    "Union", "Brothers", "Sisters", "Assembly", "Ensemble", "Club", "Riot",
    "Choir", "Hearts", "Lights", "Rooms", "Radio", "Avenue", "Line", "Circle",
    "Tapes", "Hour", "Affair", "Habit", "Machine",
]

SOLO_FIRST = [
    "Aayush", "Bibek", "Sujata", "Nirajan", "Prasiddha", "Anmol", "Sadiksha",
    "Rohit", "Simran", "Kiran", "Manish", "Prakriti", "Sujan", "Aastha",
    "Dipesh", "Nabin", "Riya", "Sanjay", "Muna", "Deepak",
]
SOLO_LAST = [
    "Gurung", "Shrestha", "Tamang", "Rai", "Magar", "Thapa", "Karki", "Lama",
    "Bhattarai", "Adhikari", "Maharjan", "Limbu", "Sherpa", "Pradhan",
]

# Bios differ in shape, not just in the words slotted into one shape. A list
# where every entry is "{sub} performer with {years} years" produces a wall of
# identical-looking cards and makes a layout impossible to judge.
BIO_SHAPES = [
    "{opener} {sub} act out of {city}. {credit} {closer}",
    "{sub} and {sub2}, played live. {credit} Based in {city}.",
    "{opener} We play {sub}. {years} years of it, mostly around {city}. {closer}",
    "{sub} for rooms that want {mood}. {credit}",
    "{city}. {sub}. {years} years. {closer}",
    "{opener} {sub} with {mood} arrangements — weddings, corporate evenings, "
    "festivals. {credit}",
    "Started in {city} in {start_year}. {sub}, {mood}, loud enough to matter. {closer}",
]

OPENERS = [
    "", "", "", "Six-piece.", "Four-piece.", "Just the two of us.",
    "Solo, with a loop pedal.",
]

CREDITS = [
    "Regulars at Jazzmandu.", "Played Sattya, Purple Haze and the Lakeside circuit.",
    "House band at a Thamel bar for two seasons.", "{count} bookings through this platform.",
    "Toured Pokhara, Chitwan and Dharan last winter.", "Backed three album launches.",
    "", "", "Wedding season regulars.",
]

CLOSERS = [
    "We bring our own PA.", "Sets from 45 minutes to three hours.",
    "Happy to learn a first-dance song.", "Travel anywhere in the valley.",
    "", "", "Ask us about the acoustic set.", "We do requests, within reason.",
]

MOODS = [
    "warm", "high-energy", "intimate", "soulful", "upbeat", "mellow",
    "atmospheric", "raucous", "stripped-back", "cinematic",
]


def _make_stage_name(rng, sub_genre: str, index: int) -> str:
    """A believable act name. Roughly a third are solo artists."""
    roll = rng.random()

    if roll < 0.3:
        return f"{rng.choice(SOLO_FIRST)} {rng.choice(SOLO_LAST)}"

    if roll < 0.55:
        word = rng.choice(NEPALI_WORDS)
        return f"{word} {rng.choice(NOUNS)}".strip()

    prefix = rng.choice(NAME_PREFIX)
    word = rng.choice(ENGLISH_WORDS)
    noun = rng.choice(NOUNS)
    return " ".join(part for part in (prefix, word, noun) if part)


def _make_bio(rng, record: dict, sub_genre: str) -> str:
    years = int(min(max(record["completed_bookings"] // 6 + 1, 1), 22))
    subs = record["sub_genres"]
    shape = rng.choice(BIO_SHAPES)
    credit = rng.choice(CREDITS).format(count=record["completed_bookings"])

    text = shape.format(
        opener=rng.choice(OPENERS),
        sub=sub_genre,
        sub2=subs[1] if len(subs) > 1 else record["parent_genre"],
        city=record["city"],
        years=years,
        start_year=2026 - years,
        mood=rng.choice(MOODS),
        credit=credit,
        closer=rng.choice(CLOSERS),
    )
    return " ".join(text.split())


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
            cur.execute("""
                DELETE FROM transaction WHERE virtual_coin_virtual_coin_id IN (
                    SELECT virtual_coin_id FROM virtual_coin WHERE artist_id = ANY(%s))
            """, (artist_ids,))
            cur.execute("DELETE FROM payment WHERE booking_id IN "
                        "(SELECT id FROM booking WHERE artist_id = ANY(%s))", (artist_ids,))
            cur.execute("DELETE FROM ml.artist_embedding WHERE artist_id = ANY(%s)", (artist_ids,))
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
        used_names: set[str] = set()
        with conn.cursor() as cur:
            for record in catalogue.to_dict("records"):
                index = record["artist_id"]
                email = f"artist{index}@{SEED_DOMAIN}"

                cur.execute("SELECT id FROM users WHERE email = %s", (email,))
                if cur.fetchone():
                    continue

                sub = record["sub_genres"][0]
                bio = _make_bio(rng, record, sub)

                # Real rosters have the odd near-duplicate, but not fifty, so
                # retry a handful of times before falling back to a suffix.
                stage_name = _make_stage_name(rng, sub, index)
                for _ in range(6):
                    if stage_name not in used_names:
                        break
                    stage_name = _make_stage_name(rng, sub, index)
                if stage_name in used_names:
                    stage_name = f"{stage_name} II"
                used_names.add(stage_name)

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


def seed_financials(seed_value: int = 42) -> dict:
    """Gives the money screens something to render.

    Artists and bookings alone leave every financial dashboard empty, which
    makes them impossible to review and impossible to demo. This settles a share
    of the existing confirmed bookings: a payment, the matching credit on the
    artist's ledger, and a wallet balance that agrees with it.

    Amounts follow the same rules the application enforces — a 5% platform fee,
    half up front on partial payments — so the totals on screen are consistent
    with what the booking flow would actually have produced.
    """
    rng = np.random.default_rng(seed_value)
    payments = transactions = 0

    with connection() as conn, conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM payment")
        if cur.fetchone()[0] > 0:
            log.info("Financial records already present; leaving them alone.")
            return {"payments": 0, "transactions": 0}

        cur.execute("""
            SELECT b.id, b.artist_id, b.user_id, b.total_amount, v.virtual_coin_id
            FROM booking b
            JOIN virtual_coin v ON v.artist_id = b.artist_id
            WHERE b.status = 'CONFIRMED'
        """)
        bookings = cur.fetchall()

        balances: dict[int, float] = {}

        for booking_id, artist_id, user_id, total, wallet_id in bookings:
            # Not every confirmed booking has been paid for yet.
            if rng.random() > 0.72:
                continue

            full = bool(rng.random() > 0.35)
            total = float(total or 0)
            if total <= 0:
                continue

            received = total if full else total / 2
            system_charges = round(total * 0.05, 2)
            earnings = round(received - system_charges, 2)
            if earnings <= 0:
                continue

            cur.execute(
                """
                INSERT INTO payment (pidx, booking_id, user_info_entity_id, total_amount,
                                     received_amount, system_charges, payment_status,
                                     payment_type, payment_method, transaction_code,
                                     product_code, payment_time, created_date)
                VALUES (%s, %s, %s, %s, %s, %s, 'COMPLETED', %s, 'KHALTI', %s,
                        'artist_booking', CURRENT_TIME, NOW() - (random() * 240)::int * INTERVAL '1 day')
                """,
                (f"seed-{booking_id}", booking_id, user_id, total, received, system_charges,
                 "FULL" if full else "PARTIAL", f"seed-tc-{booking_id}"),
            )
            payments += 1

            cur.execute(
                """
                INSERT INTO transaction (virtual_coin_virtual_coin_id, booking_id, amount,
                                         transaction_type, transaction_purpose, status, created_date)
                VALUES (%s, %s, %s, 'CREDIT', 'BOOKING_PAYMENT', 'APPROVED',
                        NOW() - (random() * 240)::int * INTERVAL '1 day')
                """,
                (wallet_id, booking_id, earnings),
            )
            transactions += 1
            balances[wallet_id] = balances.get(wallet_id, 0.0) + earnings

        # A handful of artists have already withdrawn some of it.
        for wallet_id, balance in list(balances.items()):
            if rng.random() > 0.25 or balance < 2000:
                continue
            amount = round(balance * float(rng.uniform(0.2, 0.6)), 2)
            cur.execute(
                """
                INSERT INTO transaction (virtual_coin_virtual_coin_id, amount, transaction_type,
                                         transaction_purpose, status, created_date)
                VALUES (%s, %s, 'DEBIT', 'WITHDRAWAL_REQUEST', %s,
                        NOW() - (random() * 90)::int * INTERVAL '1 day')
                """,
                (wallet_id, amount, "APPROVED" if rng.random() > 0.4 else "PENDING"),
            )
            transactions += 1
            balances[wallet_id] = balance - amount

        # The wallet must agree with its ledger.
        for wallet_id, balance in balances.items():
            cur.execute("UPDATE virtual_coin SET balance = %s WHERE virtual_coin_id = %s",
                        (round(balance, 2), wallet_id))

        conn.commit()

    log.info("Seeded %d payments and %d transactions", payments, transactions)
    return {"payments": payments, "transactions": transactions}


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
    ledger = seed_financials(args.seed)

    print(f"Seeded {created} artists (password: {SEED_PASSWORD}).")
    print(f"Seeded {ledger['payments']} payments and {ledger['transactions']} "
          f"transactions so the admin and artist dashboards have something to show.")
    print("\nRebuild embeddings next:")
    print("  curl -X POST http://localhost:8000/embeddings/rebuild")


if __name__ == "__main__":
    main()
