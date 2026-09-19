"""Replaces the database contents with a demo platform that behaves like a used one.

    python -m training.seed_world --artists 300 --wipe

The previous seeder produced a catalogue and a pile of bookings, which was
enough to exercise search but not enough to look real. What gave it away was
never the artist names — it was the shape of the data around them:

  * nobody had a picture
  * 99.6% of bookings were CONFIRMED, because status was assigned without
    reference to the event date
  * four reviews across five thousand bookings, and artist ratings that were
    invented rather than derived from them
  * no posts, no published availability
  * every account created on the same afternoon

So this seeds a *history* instead of a snapshot. Accounts join over two and a
half years. Bookings are placed in the past and the future, and their status
follows from when they happen: a gig last March is COMPLETED or CANCELLED, one
next month is CONFIRMED or still PENDING. Reviews are written for gigs that
actually happened, and an artist's rating is the mean of the reviews they were
given — not a number chosen first and decorated afterwards.

Everything it writes is demo data and it says so: accounts live on
@seed.openmichub.local and @demo.openmichub.local.
"""

from __future__ import annotations

import argparse
import logging
import random
import unicodedata
from datetime import date, datetime, time, timedelta
from pathlib import Path

from app.db import connection
from training import behaviour, world
from training.artwork import cover_art

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(levelname)-5s %(message)s")

SEED_DOMAIN = "seed.openmichub.local"
DEMO_DOMAIN = "demo.openmichub.local"

# bcrypt hash of "Admin@123", shared by every seeded account. Demo data only.
PASSWORD = "Admin@123"
PASSWORD_HASH = "$2a$10$54gPf2W4sUQAMD8nr8Moqe8ylugwB/AXanMm2vbPUlGLaBCsCTNm6"

# Where generated pictures land. Matches the API's upload root, which it serves
# at /media/<relative path> and stores in users.profile as that relative path.
UPLOAD_ROOT = Path("/app/uploads")
PROFILE_DIR = "profiles"

GENRES = {
    "Rock": ["Classic Rock", "Indie Rock", "Alternative", "Punk"],
    "Jazz": ["Bebop", "Smooth Jazz", "Fusion", "Swing"],
    "Folk": ["Nepali Folk", "Acoustic Folk", "Storytelling", "Bluegrass"],
    "Pop": ["Nepali Pop", "Synth Pop", "Acoustic Pop", "Dance Pop"],
    "Classical": ["Hindustani", "Sitar", "Flute", "Chamber"],
    "Hip-Hop": ["Nepali Rap", "Boom Bap", "Trap", "Lyrical"],
    "Electronic": ["House", "Techno", "Ambient", "Drum and Bass"],
    "Blues": ["Delta Blues", "Electric Blues", "Soul Blues"],
}

CITY_WEIGHTS = {
    "Kathmandu": 0.34, "Lalitpur": 0.13, "Pokhara": 0.12, "Bhaktapur": 0.09,
    "Chitwan": 0.08, "Biratnagar": 0.06, "Butwal": 0.06, "Dharan": 0.05,
    "Janakpur": 0.04, "Nepalgunj": 0.03,
}

# The catalogue is two and a half years old on the day it is seeded.
HISTORY_MONTHS = 30


# --------------------------------------------------------------------------- #
# helpers
# --------------------------------------------------------------------------- #

def slugify(name: str) -> str:
    folded = unicodedata.normalize("NFD", name)
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    out, prev_dash = [], False
    for ch in folded.lower():
        if ch.isalnum():
            out.append(ch)
            prev_dash = False
        elif not prev_dash:
            out.append("-")
            prev_dash = True
    return "".join(out).strip("-")[:120]


def weighted_choice(rng: random.Random, weights: dict) -> str:
    return rng.choices(list(weights), weights=list(weights.values()), k=1)[0]


def stamp(rng: random.Random, day: date) -> datetime:
    """A plausible clock time on a given day, so created_date is not all midnight."""
    return datetime.combine(day, time(rng.randint(8, 22), rng.randint(0, 59), rng.randint(0, 59)))


# --------------------------------------------------------------------------- #
# wipe
# --------------------------------------------------------------------------- #

def wipe(conn) -> None:
    """Empties every table the platform writes to, leaving reference data intact.

    Genres, categories and roles survive: they are configuration the application
    seeds itself and expects to find, not user data. Everything else goes,
    including the ML tables, whose embeddings would otherwise point at artists
    that no longer exist.
    """
    log.info("Clearing existing data")
    with conn.cursor() as cur:
        cur.execute("""
            TRUNCATE TABLE
                post_like, posts_images, posts, review, transaction, payment,
                booking, availability_time, artist_availability,
                artist_unavailability, chats, otp, virtual_coin,
                artists_genres, artists, users_roles, users
            RESTART IDENTITY CASCADE
        """)
        # Generated pictures are named after the artist slug, so a second run
        # with different names leaves the old files orphaned in the volume. They
        # are unreachable but they accumulate, so they go with the rows.
        gallery = UPLOAD_ROOT / PROFILE_DIR
        if gallery.is_dir():
            removed = 0
            for stale in gallery.glob("*.jpg"):
                stale.unlink()
                removed += 1
            if removed:
                log.info("Removed %d generated pictures from the previous seed", removed)

        # Committed before the optional tables below, each of which may not exist
        # yet: a rollback after a failed TRUNCATE would otherwise undo this one too.
        conn.commit()

        # ML tables live in their own schema and are keyed by artist id. The
        # discovery logs are listed too: a signed-out visitor's rows belong to no
        # user, so truncating users does not cascade to them.
        for table in ("ml.artist_embedding", "ml.artist_segment",
                      "user_interaction", "discovery_impression"):
            try:
                cur.execute(f"TRUNCATE TABLE {table} RESTART IDENTITY CASCADE")
                conn.commit()
            except Exception:
                conn.rollback()
    conn.commit()


# --------------------------------------------------------------------------- #
# reference data
# --------------------------------------------------------------------------- #

def genre_category_ids(conn) -> dict:
    """{sub-genre name: category id}, creating anything the app has not seeded."""
    ids = {}
    with conn.cursor() as cur:
        for parent, subs in GENRES.items():
            cur.execute("SELECT id FROM genre WHERE name = %s", (parent,))
            row = cur.fetchone()
            if row:
                genre_id = row[0]
            else:
                cur.execute(
                    "INSERT INTO genre (name, active, created_by, created_date) "
                    "VALUES (%s, true, 'seed', now()) RETURNING id", (parent,))
                genre_id = cur.fetchone()[0]

            for sub in subs:
                cur.execute("SELECT id FROM category WHERE name = %s", (sub,))
                row = cur.fetchone()
                if row:
                    category_id = row[0]
                else:
                    cur.execute(
                        # category is not Auditable: it has no created_* columns.
                        "INSERT INTO category (name, active) "
                        "VALUES (%s, true) RETURNING id", (sub,))
                    category_id = cur.fetchone()[0]
                cur.execute(
                    "INSERT INTO genre_categories (genre_id, categories_id) VALUES (%s, %s) "
                    "ON CONFLICT DO NOTHING", (genre_id, category_id))
                ids[sub] = category_id
    conn.commit()
    return ids


def role_ids(conn) -> dict:
    with conn.cursor() as cur:
        cur.execute("SELECT name, id FROM roles")
        return dict(cur.fetchall())


# --------------------------------------------------------------------------- #
# people
# --------------------------------------------------------------------------- #

def insert_user(cur, *, email, full_name, city, phone, joined, profile=None,
                verified=True, active=True) -> int:
    cur.execute(
        """
        INSERT INTO users (email, full_name, location, phone_number, password,
                           profile, is_active, is_verified,
                           created_by, created_date, last_modified_by, last_modified_date)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'seed', %s, 'seed', %s)
        RETURNING id
        """,
        (email, full_name, city, phone, PASSWORD_HASH, profile, active, verified,
         joined, joined),
    )
    return cur.fetchone()[0]


def grant_role(cur, user_id: int, role_id: int) -> None:
    cur.execute(
        "INSERT INTO users_roles (user_entity_id, roles_id) VALUES (%s, %s) "
        "ON CONFLICT DO NOTHING", (user_id, role_id))


def person_name(rng: random.Random, used: set) -> str:
    for _ in range(200):
        name = f"{rng.choice(world.NEPALI_GIVEN)} {rng.choice(world.NEPALI_FAMILY)}"
        if name not in used:
            used.add(name)
            return name
    # Exhausted the combinations; fall back to a middle initial rather than loop.
    name = (f"{rng.choice(world.NEPALI_GIVEN)} {rng.choice('ABCDKMNPRS')}. "
            f"{rng.choice(world.NEPALI_FAMILY)}")
    used.add(name)
    return name


def stage_name(rng: random.Random, real_name: str, used: set) -> str:
    """Some acts trade under a band name, some under the performer's own."""
    for _ in range(300):
        roll = rng.random()
        if roll < 0.34:
            candidate = real_name
        elif roll < 0.72:
            prefix = rng.choice(world.BAND_PREFIX)
            candidate = " ".join(filter(None, [
                prefix, rng.choice(world.BAND_NOUNS), rng.choice(world.BAND_TAIL)]))
        else:
            candidate = f"{rng.choice(world.BAND_NOUNS)} {rng.choice(world.BAND_TAIL)}"
        if candidate not in used:
            used.add(candidate)
            return candidate
    raise RuntimeError("ran out of distinct stage names")


def write_bio(rng: random.Random, *, sub_genre, city, province, years, gigs) -> str:
    text = rng.choice(world.BIO_TEMPLATES).format(
        sub=sub_genre.lower(), city=city, province=province, years=years, gigs=gigs,
        adjective=rng.choice(world.BIO_ADJECTIVES), mood=rng.choice(world.BIO_MOODS),
        mood_short=rng.choice(world.BIO_MOODS_SHORT),
        instrument=rng.choice(world.INSTRUMENTS),
        venue_kind=rng.choice(world.VENUE_KINDS), small=rng.choice(world.SMALL_VENUES),
        big=rng.choice(world.BIG_VENUES),
        event_lower=rng.choice(world.EVENT_TYPES).lower(),
        start_year=date.today().year - years,
        year_ahead=rng.choice(world.MONTHS),
    )
    return text[:250]


def seed_artists(conn, rng, n_artists, categories, roles, today, *, with_art=True) -> list[dict]:
    """Creates the roster: user, artist row, genres and a picture."""
    log.info("Creating %d artists", n_artists)
    artists, used_names, used_stage, used_slugs = [], set(), set(), set()
    sub_to_parent = {sub: parent for parent, subs in GENRES.items() for sub in subs}
    all_subs = list(sub_to_parent)

    with conn.cursor() as cur:
        for index in range(n_artists):
            real_name = person_name(rng, used_names)
            name = stage_name(rng, real_name, used_stage)

            slug = slugify(name) or f"artist-{index + 1}"
            if slug in used_slugs:
                slug = f"{slug}-{index + 1}"
            used_slugs.add(slug)

            city = weighted_choice(rng, CITY_WEIGHTS)
            # Joined at some point in the platform's history, weighted towards
            # recent — the way a growing catalogue actually fills up.
            age_days = int((rng.random() ** 1.6) * HISTORY_MONTHS * 30)
            joined_on = today - timedelta(days=age_days)
            joined = stamp(rng, joined_on)

            primary_sub = all_subs[index % len(all_subs)] if index < len(all_subs) \
                else rng.choice(all_subs)
            parent = sub_to_parent[primary_sub]
            subs = {primary_sub}
            for _ in range(rng.randint(0, 2)):
                subs.add(rng.choice(GENRES[parent]))
            if rng.random() < 0.22:                      # a few acts cross over
                subs.add(rng.choice(all_subs))

            # Latent quality drives the rate now and the reviews later, so a
            # pricier act really is the better-reviewed one more often than not.
            quality = min(1.0, max(0.0, rng.gauss(0.62, 0.18)))
            rate = round(((900 + quality * 5200) * rng.uniform(0.85, 1.15)) / 50) * 50

            profile_path = None
            if with_art:
                relative = f"{PROFILE_DIR}/{slug}.jpg"
                cover_art(name, slug, parent, UPLOAD_ROOT / relative)
                profile_path = relative

            phone = f"977{rng.randint(9800000000, 9899999999)}"
            user_id = insert_user(cur, email=f"{slug}@{SEED_DOMAIN}", full_name=real_name,
                                  city=city, phone=phone, joined=joined, profile=profile_path)
            grant_role(cur, user_id, roles["ARTIST"])

            years = max(1, age_days // 365 + rng.randint(1, 6))
            bio = write_bio(rng, sub_genre=primary_sub, city=city, province="Bagmati",
                            years=years, gigs=rng.choice([20, 40, 60, 100]))

            cur.execute(
                """
                INSERT INTO artists (stage_name, slug, bio, hourly_rate, rating, user_id,
                                     created_by, created_date, last_modified_by, last_modified_date)
                VALUES (%s, %s, %s, %s, NULL, %s, 'seed', %s, 'seed', %s)
                RETURNING id
                """,
                (name, slug, bio, float(rate), user_id, joined, joined),
            )
            artist_id = cur.fetchone()[0]

            for sub in subs:
                cur.execute(
                    "INSERT INTO artists_genres (artist_id, genres_id) VALUES (%s, %s) "
                    "ON CONFLICT DO NOTHING", (artist_id, categories[sub]))

            cur.execute(
                "INSERT INTO virtual_coin (artist_id, balance, created_by, created_date,"
                " last_modified_by, last_modified_date) VALUES (%s, 0, 'seed', %s, 'seed', %s)"
                " RETURNING virtual_coin_id", (artist_id, joined, joined))
            coin_id = cur.fetchone()[0]

            artists.append({
                "artist_id": artist_id, "user_id": user_id, "coin_id": coin_id,
                "stage_name": name, "slug": slug, "city": city, "rate": float(rate),
                "quality": quality, "joined": joined, "parent": parent,
                "primary_sub": primary_sub,
            })

            if (index + 1) % 50 == 0:
                conn.commit()
                log.info("  %d/%d", index + 1, n_artists)

    conn.commit()
    return artists


DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]


def seed_availability(conn, rng, artists) -> int:
    """Published weekly slots. Not everyone bothers, which is itself realistic."""
    log.info("Publishing availability")
    written = 0
    with conn.cursor() as cur:
        for artist in artists:
            if rng.random() > 0.72:          # about a quarter never fill it in
                continue
            # Weekend-heavy, the way gigging actually works.
            days = set(rng.sample(["FRIDAY", "SATURDAY"], k=rng.randint(1, 2)))
            days.update(rng.sample(DAYS, k=rng.randint(0, 3)))

            for day in days:
                cur.execute("SELECT nextval(\'artist_availability_seq\')")
                availability_id = cur.fetchone()[0]
                cur.execute(
                    "INSERT INTO artist_availability (id, day_of_week, artist_id, created_by,"
                    " created_date, last_modified_by, last_modified_date)"
                    " VALUES (%s, %s, %s, \'seed\', %s, \'seed\', %s)",
                    (availability_id, day, artist["artist_id"], artist["joined"], artist["joined"]))

                for _ in range(rng.randint(1, 2)):
                    start_hour = rng.choice([11, 14, 17, 18, 19, 20, 21])
                    end_hour = min(23, start_hour + rng.choice([2, 3, 3, 4]))
                    cur.execute("SELECT nextval(\'availability_time_seq\')")
                    slot_id = cur.fetchone()[0]
                    cur.execute(
                        "INSERT INTO availability_time (id, start_time, end_time, availability_id,"
                        " created_by, created_date, last_modified_by, last_modified_date)"
                        " VALUES (%s, %s, %s, %s, \'seed\', %s, \'seed\', %s)",
                        (slot_id, time(start_hour, 0), time(end_hour, 0), availability_id,
                         artist["joined"], artist["joined"]))
                    written += 1
    conn.commit()
    return written


# --------------------------------------------------------------------------- #
# the booking history
# --------------------------------------------------------------------------- #

def booking_status(rng, event_day: date, today: date) -> str:
    """What state a booking is in, decided by when the gig is.

    This is the part the old seeder got wrong. Status was drawn independently of
    the date, which produced 99.6% CONFIRMED — including for gigs two years past,
    which should long since have been played or called off. Deriving it from the
    date is what makes the dashboards and the response-rate figures mean anything.
    """
    days_out = (event_day - today).days

    if days_out < 0:                       # already happened
        roll = rng.random()
        if roll < 0.88:
            return "COMPLETED"
        if roll < 0.96:
            return "CANCELLED"
        return "DECLINED"

    if days_out <= 21:                     # imminent: mostly settled
        roll = rng.random()
        if roll < 0.86:
            return "CONFIRMED"
        if roll < 0.94:
            return "PENDING"
        return "CANCELLED"

    roll = rng.random()                    # further out: more still in flight
    if roll < 0.62:
        return "CONFIRMED"
    if roll < 0.88:
        return "PENDING"
    if roll < 0.95:
        return "DECLINED"
    return "CANCELLED"


def seed_bookings(conn, rng, artists, bookers, today, known, per_artist=(0, 26)) -> list[dict]:
    """Places gigs across the platform's history and a few months into the future.

    How many gigs an act gets follows its quality. *Who* books each one follows
    the organizers' preferences - genre, city, budget, circle, loyalty - rather
    than a uniform draw; see training/behaviour.py for why that changed.
    """
    log.info("Booking gigs")
    bookings = []
    history: dict[tuple, int] = {}
    low, high = per_artist

    with conn.cursor() as cur:
        for artist in artists:
            # Busier artists are the better ones, with a long tail of quiet acts.
            expected = low + (artist["quality"] ** 1.3) * (high - low)
            count = max(0, int(rng.gauss(expected, 3.5)))

            # Only gigs after they joined, and up to three months ahead.
            earliest = artist["joined"].date() + timedelta(days=rng.randint(1, 25))
            latest = today + timedelta(days=90)
            if earliest >= latest:
                continue

            span = (latest - earliest).days
            for _ in range(count):
                event_day = earliest + timedelta(days=rng.randint(0, span))
                # Gigs cluster on Friday and Saturday.
                if rng.random() < 0.62:
                    shift = (4 - event_day.weekday()) % 7
                    event_day = event_day + timedelta(days=rng.choice([shift, shift + 1]))
                    if event_day > latest:
                        continue

                status = booking_status(rng, event_day, today)
                start_hour = rng.choice([12, 15, 17, 18, 19, 19, 20, 20, 21])
                hours = rng.choice([2, 3, 3, 3, 4, 5])
                end_hour = min(23, start_hour + hours)
                billed_hours = end_hour - start_hour
                if billed_hours <= 0:
                    continue

                booker = behaviour.choose(rng, bookers, artist, known, history)
                history[(booker["user_id"], artist["artist_id"])] = \
                    history.get((booker["user_id"], artist["artist_id"]), 0) + 1
                event_type = behaviour.occasion_for(rng, booker, artist)
                # Played where the organizer is most of the time, otherwise at home.
                city = booker["city"] if rng.random() < 0.7 else artist["city"]
                venue = rng.choice(world.VENUES.get(city, world.VENUES["Kathmandu"]))

                total = round(artist["rate"] * billed_hours, 2)

                # Requested somewhere between three months and a week beforehand,
                # and never later than today: a request cannot have been made in
                # the future, and a row dated there would count as brand new in
                # every recency-weighted signal for months.
                lead = rng.randint(7, 95)
                requested_on = min(today, max(earliest, event_day - timedelta(days=lead)))
                requested = stamp(rng, requested_on)
                if requested > datetime.now():
                    requested = datetime.now() - timedelta(minutes=rng.randint(5, 600))

                cur.execute(
                    """
                    INSERT INTO booking (event_date, start_time, end_time, event_type, venue,
                                         status, total_amount, artist_id, user_id,
                                         created_by, created_date, last_modified_by,
                                         last_modified_date)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'seed', %s, 'seed', %s)
                    RETURNING id
                    """,
                    (event_day, time(start_hour, 0), time(end_hour, 0), event_type, venue,
                     status, total, artist["artist_id"], booker["user_id"], requested, requested),
                )
                bookings.append({
                    "id": cur.fetchone()[0], "artist": artist, "booker": booker,
                    "event_day": event_day, "status": status, "total": total,
                    "requested": requested, "hours": billed_hours, "event_type": event_type,
                })

            if len(bookings) % 500 < count:
                conn.commit()

    conn.commit()
    log.info("  %d bookings", len(bookings))
    return bookings


SYSTEM_FEE_RATE = 0.10


def seed_money(conn, rng, bookings, today) -> dict:
    """Payments and the ledger behind them, consistent with each booking's state.

    A gig that was played is paid for; one still awaiting an answer is not. Every
    payment writes a matching CREDIT into the artist's wallet, and the wallet
    balance is the sum of what actually moved rather than a number invented
    alongside it.
    """
    log.info("Settling payments")
    counts = {"payments": 0, "transactions": 0, "withdrawals": 0}
    credited: dict[int, float] = {}

    with conn.cursor() as cur:
        for booking in bookings:
            status = booking["status"]
            if status not in ("COMPLETED", "CONFIRMED"):
                continue
            # A confirmed but unplayed gig is often only part-paid.
            if status == "CONFIRMED" and rng.random() < 0.35:
                continue

            partial = status == "CONFIRMED" and rng.random() < 0.45
            total = booking["total"]
            received = round(total / 2, 2) if partial else total
            paid_on = booking["requested"] + timedelta(days=rng.randint(0, 6))
            if paid_on.date() > today:
                paid_on = stamp(rng, today)

            cur.execute(
                """
                INSERT INTO payment (total_amount, received_amount, system_charges,
                                     payment_method, payment_status, payment_type,
                                     payment_time, product_code, transaction_code,
                                     booking_id, user_info_entity_id,
                                     created_by, created_date, last_modified_by,
                                     last_modified_date)
                VALUES (%s, %s, %s, 'KHALTI', 'COMPLETED', %s, %s, 'artist_booking', %s,
                        %s, %s, 'seed', %s, 'seed', %s)
                """,
                (total, received, round(total * SYSTEM_FEE_RATE, 2),
                 "PARTIAL" if partial else "FULL", paid_on.time(),
                 f"seed-{booking['id']}", booking["id"], booking["booker"]["user_id"],
                 paid_on, paid_on),
            )
            counts["payments"] += 1

            artist_share = round(received * (1 - SYSTEM_FEE_RATE), 2)
            coin_id = booking["artist"]["coin_id"]
            credited[coin_id] = credited.get(coin_id, 0.0) + artist_share

            cur.execute(
                """
                INSERT INTO transaction (amount, status, transaction_purpose, transaction_type,
                                         booking_id, virtual_coin_virtual_coin_id,
                                         created_by, created_date, last_modified_by,
                                         last_modified_date)
                VALUES (%s, 'APPROVED', 'BOOKING_PAYMENT', 'CREDIT', %s, %s,
                        'seed', %s, 'seed', %s)
                """,
                (artist_share, booking["id"], coin_id, paid_on, paid_on),
            )
            counts["transactions"] += 1

        # Some artists have drawn earnings out. Never more than they hold.
        for coin_id, balance in credited.items():
            if balance < 5000 or rng.random() > 0.42:
                continue
            taken = round(balance * rng.uniform(0.2, 0.7), 2)
            when = stamp(rng, today - timedelta(days=rng.randint(1, 120)))
            approved = rng.random() < 0.8
            cur.execute(
                """
                INSERT INTO transaction (amount, status, transaction_purpose, transaction_type,
                                         virtual_coin_virtual_coin_id,
                                         created_by, created_date, last_modified_by,
                                         last_modified_date)
                VALUES (%s, %s, 'WITHDRAWAL_REQUEST', 'DEBIT', %s, 'seed', %s, 'seed', %s)
                """,
                (taken, "APPROVED" if approved else "PENDING", coin_id, when, when),
            )
            counts["withdrawals"] += 1
            if approved:
                credited[coin_id] = balance - taken

        for coin_id, balance in credited.items():
            cur.execute("UPDATE virtual_coin SET balance = %s WHERE virtual_coin_id = %s",
                        (round(balance, 2), coin_id))

    conn.commit()
    return counts


# --------------------------------------------------------------------------- #
# reviews, and the ratings that follow from them
# --------------------------------------------------------------------------- #

def _review_text(rng, score: int) -> str:
    if score == 5:
        return rng.choice(world.REVIEWS_GREAT)
    if score == 4:
        return rng.choice(world.REVIEWS_GOOD)
    if score == 3:
        return rng.choice(world.REVIEWS_MIXED)
    return rng.choice(world.REVIEWS_POOR)


def seed_reviews(conn, rng, bookings) -> int:
    """Reviews for gigs that actually happened, then ratings derived from them.

    The old data had the causality backwards: an artist was handed a rating and
    the review table stayed empty, so a profile could show 3.65 stars above the
    words "no reviews yet". Here the reviews come first and the rating is their
    mean, which is also what the application computes when a real review lands.
    """
    log.info("Writing reviews")
    scores: dict[int, list[int]] = {}
    written = 0

    with conn.cursor() as cur:
        for booking in bookings:
            if booking["status"] != "COMPLETED":
                continue
            if rng.random() > 0.63:              # not everyone writes one
                continue

            # Quality shifts the distribution rather than fixing the score, so a
            # good act still collects the occasional bad night.
            quality = booking["artist"]["quality"]
            # J-shaped, the way review distributions actually are: most people
            # who bother to write are pleased, and the unhappy tail is short but
            # real. Quality moves the whole curve rather than setting the score.
            roll = rng.random() * 0.62 + quality * 0.38 + rng.gauss(0, 0.10)
            if roll > 0.56:
                score = 5
            elif roll > 0.34:
                score = 4
            elif roll > 0.20:
                score = 3
            elif roll > 0.10:
                score = 2
            else:
                score = 1

            written_on = stamp(rng, booking["event_day"] + timedelta(days=rng.randint(1, 14)))
            comment = _review_text(rng, score) if rng.random() < 0.86 else None

            cur.execute(
                """
                INSERT INTO review (rating, comment, artist_id, booking_id, reviewer_id,
                                    created_by, created_date, last_modified_by, last_modified_date)
                VALUES (%s, %s, %s, %s, %s, 'seed', %s, 'seed', %s)
                """,
                (score, comment, booking["artist"]["artist_id"], booking["id"],
                 booking["booker"]["user_id"], written_on, written_on),
            )
            scores.setdefault(booking["artist"]["artist_id"], []).append(score)
            written += 1

        for artist_id, values in scores.items():
            cur.execute("UPDATE artists SET rating = %s WHERE id = %s",
                        (round(sum(values) / len(values), 2), artist_id))

    conn.commit()
    log.info("  %d reviews across %d artists", written, len(scores))
    return written


def seed_posts(conn, rng, artists, today) -> int:
    """A feed. Active acts post; most do not, and that gap is the realistic part."""
    log.info("Writing posts")
    written = 0
    with conn.cursor() as cur:
        for artist in artists:
            if rng.random() > 0.38:
                continue
            for _ in range(rng.randint(1, 4)):
                title, body = rng.choice(world.POST_TEMPLATES)
                when = stamp(rng, today - timedelta(days=rng.randint(1, 400)))
                if when.date() < artist["joined"].date():
                    continue
                venue = rng.choice(world.VENUES.get(artist["city"], world.VENUES["Kathmandu"]))
                month = rng.choice(world.MONTHS)

                cur.execute(
                    """
                    INSERT INTO posts (title, content, likes_count, artist_id,
                                       created_by, created_date, last_modified_by,
                                       last_modified_date)
                    VALUES (%s, %s, %s, %s, 'seed', %s, 'seed', %s)
                    """,
                    (title.format(venue=venue, city=artist["city"], month=month),
                     body.format(venue=venue, city=artist["city"], month=month),
                     max(0, int(rng.gauss(14, 12))), artist["artist_id"], when, when),
                )
                written += 1
    conn.commit()
    log.info("  %d posts", written)
    return written


# --------------------------------------------------------------------------- #
# entry point
# --------------------------------------------------------------------------- #

def seed_bookers(conn, rng, n_bookers, roles, today) -> list[dict]:
    """Organizers. Bookings need someone on the other side of them."""
    log.info("Creating %d organizers", n_bookers)
    bookers, used = [], set()
    with conn.cursor() as cur:
        for index in range(n_bookers):
            name = person_name(rng, used)
            city = weighted_choice(rng, CITY_WEIGHTS)
            joined = stamp(rng, today - timedelta(days=int(rng.random() * HISTORY_MONTHS * 30)))
            handle = slugify(name) or f"organizer-{index + 1}"
            user_id = insert_user(
                cur, email=f"{handle}.{index + 1}@{SEED_DOMAIN}", full_name=name, city=city,
                phone=f"977{rng.randint(9800000000, 9899999999)}", joined=joined)
            grant_role(cur, user_id, roles["ORGANIZER"])
            # What this organizer actually wants. See training/behaviour.py.
            bookers.append({"user_id": user_id, "name": name, "city": city,
                            "taste": behaviour.taste(rng, city)})
    conn.commit()
    return bookers


def seed_staff(conn, roles, today) -> None:
    """The accounts a person signs in with, on stable addresses the README names."""
    log.info("Creating staff and demo logins")
    when = datetime.combine(today - timedelta(days=HISTORY_MONTHS * 30), time(9, 0))
    with conn.cursor() as cur:
        for email, name, role in (
            ("admin@openmichub.com", "Platform Owner", "SUPER_ADMIN"),
            (f"admin@{DEMO_DOMAIN}", "Demo Administrator", "ADMIN"),
            (f"booker@{DEMO_DOMAIN}", "Demo Organizer", "ORGANIZER"),
        ):
            user_id = insert_user(cur, email=email, full_name=name, city="Kathmandu",
                                  phone=None, joined=when)
            grant_role(cur, user_id, roles[role])
    conn.commit()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Replace the database with a demo platform that has a history.")
    parser.add_argument("--artists", type=int, default=300)
    parser.add_argument("--bookers", type=int, default=140)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--visitors", type=int, default=900,
                        help="signed-out visitors who browse and search but never book")
    parser.add_argument("--no-art", action="store_true",
                        help="skip generating profile pictures")
    parser.add_argument("--wipe", action="store_true",
                        help="required: this deletes every row the platform owns")
    args = parser.parse_args()

    if not args.wipe:
        parser.error("refusing to run without --wipe: this replaces all platform data")

    rng = random.Random(args.seed)
    today = date.today()

    with connection() as conn:
        wipe(conn)
        categories = genre_category_ids(conn)
        roles = role_ids(conn)

        seed_staff(conn, roles, today)
        bookers = seed_bookers(conn, rng, args.bookers, roles, today)
        # The demo organizer books too, so its dashboard is not empty.
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM users WHERE email = %s", (f"booker@{DEMO_DOMAIN}",))
            # A legible taste, so signing in as the demo organizer shows what
            # personalisation does: corporate dinners and weddings, Jazz and
            # Folk, in Kathmandu, at a mid-range budget.
            bookers.append({"user_id": cur.fetchone()[0], "name": "Demo Organizer",
                            "city": "Kathmandu",
                            "taste": behaviour.taste(rng, "Kathmandu", archetype="corporate events",
                                                     favourites=["Jazz", "Folk"], budget=4200.0)})

        artists = seed_artists(conn, rng, args.artists, categories, roles, today,
                               with_art=not args.no_art)
        known, circle_city = behaviour.make_circles(rng, artists)
        behaviour.attach_roster(rng, bookers, artists, circle_city)
        slots = seed_availability(conn, rng, artists)
        bookings = seed_bookings(conn, rng, artists, bookers, today, known)
        money = seed_money(conn, rng, bookings, today)
        reviews = seed_reviews(conn, rng, bookings)
        posts = seed_posts(conn, rng, artists, today)
        activity = behaviour.seed_interactions(conn, rng, artists, bookers, bookings,
                                               known, circle_city, today,
                                               visitors=args.visitors)

    log.info("")
    log.info("Done: %d artists, %d organizers, %d bookings, %d payments, "
             "%d transactions, %d reviews, %d posts, %d availability slots",
             len(artists), len(bookers), len(bookings), money["payments"],
             money["transactions"] + money["withdrawals"], reviews, posts, slots)
    log.info("      %d profile views and %d searches, %d from signed-out visitors",
             activity["views"], activity["searches"], activity["visitors"])
    log.info("Every seeded account signs in with: %s", PASSWORD)
    log.info("Next: rebuild embeddings and retrain segmentation, or artists will be "
             "unsearchable and unsegmented. Then GET /signals?refresh=true on the ML "
             "service, so co-choice and demand read the new history.")


if __name__ == "__main__":
    main()
