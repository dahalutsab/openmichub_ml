"""How the demo's organizers and visitors choose, stated in full.

The first version of the seeder picked the organizer for every booking with
`rng.choice(bookers)`. Every booking went to a uniformly random organizer, so no
organizer preferred anything: organizer 129 came out "liking" Blues 1.0,
Electric Blues 0.98, Electronic 0.67, Rock 0.64 and Classical 0.60 across five
cities, which is what twenty-eight random draws look like. Personalisation was
faithfully personalising noise, and no recommender - a trained one, a good one,
YouTube's - can look sensible on data with nothing in it to find.

This module is the replacement: a small generative model of why a particular
organizer books a particular act. Every part of it is the kind of thing that
drives real booking decisions, and every part is written down here so the claim
"the recommender recovers it" can be checked against what was put in.

  occasion mix   A wedding planner books for weddings; a club promoter for club
                 nights and festivals. Six archetypes, each a distribution over
                 event types.

  genre taste    Follows from the occasions (a corporate booker leans Jazz and
                 Classical, via the same event-genre table the ranker uses), plus
                 one or two parent genres the organizer personally favours, and
                 sometimes a favourite sub-genre.

  home city      Most gigs are local. Travel willingness varies per organizer.

  budget         A personal hourly budget, log-normal around NPR 3,500. Acts far
                 above it are rarely booked.

  circle         Word of mouth. Organizers belong to one of a dozen circles - the
                 planners who share a WhatsApp group, the promoters who work the
                 same venues - and each circle knows about a dozen acts, which
                 its members book three times as readily. This is the part a
                 profile's text cannot reveal, and the part collaborative
                 filtering exists to find. It is also the part most open to the
                 charge of being planted, so its strength is a single constant.

  loyalty        Some organizers rebook the acts they already know.

  exploration    A tenth of every choice ignores all of the above.

Anonymous visitors are drawn from the same model - they are prospective
organizers - and leave profile views and searches, never bookings.

What this cannot do is make the evaluation mean more than it does. Measured on
data generated here, a recommender is being tested on whether it recovers these
mechanisms, not on whether these mechanisms are how Nepali organizers actually
behave. ml_service/README.md says the same where the numbers are reported.
"""

from __future__ import annotations

import math
import random
import uuid
from datetime import date, datetime, time, timedelta

from app.features import price_fit
from app.taxonomy import EVENT_GENRE_FIT, PROVINCE
from training import world

ARCHETYPES: dict[str, dict[str, float]] = {
    "wedding planner": {"Wedding": 0.70, "Birthday": 0.10, "Charity Gala": 0.10, "Corporate": 0.10},
    "corporate events": {"Corporate": 0.60, "Charity Gala": 0.25, "Restaurant": 0.15},
    "club promoter": {"Club Night": 0.60, "Festival": 0.30, "Birthday": 0.10},
    "venue owner": {"Restaurant": 0.55, "Open Mic": 0.30, "Birthday": 0.15},
    "festival booker": {"Festival": 0.60, "Club Night": 0.20, "Open Mic": 0.20},
    "all-rounder": dict(zip(world.EVENT_TYPES, world.EVENT_WEIGHTS)),
}
ARCHETYPE_WEIGHTS = {"wedding planner": 0.28, "corporate events": 0.16, "club promoter": 0.14,
                     "venue owner": 0.16, "festival booker": 0.08, "all-rounder": 0.18}

CIRCLES = 12
CIRCLE_ACTS = 12
CIRCLE_BOOST = 3.0          # how much readier a circle member is to book a known act
LOYALTY_BOOST = 8.0         # multiplied by an organizer's loyalty, for acts they booked before
EXPLORATION = 0.10          # share of each choice that ignores taste entirely

SEARCH_NOUN = {
    "Rock": ["band", "rock band", "live band"],
    "Jazz": ["jazz trio", "jazz band", "quartet"],
    "Folk": ["folk singer", "acoustic duo", "folk band"],
    "Pop": ["pop singer", "cover band", "band"],
    "Classical": ["classical ensemble", "sitar player", "musicians"],
    "Hip-Hop": ["rapper", "hip-hop act", "mc"],
    "Electronic": ["dj", "electronic act", "producer"],
    "Blues": ["blues band", "blues guitarist", "soul singer"],
}


def weighted(rng: random.Random, weights: dict):
    return rng.choices(list(weights), weights=list(weights.values()), k=1)[0]


def taste(rng: random.Random, city: str, *, archetype: str | None = None,
          favourites: list[str] | None = None, budget: float | None = None) -> dict:
    """One organizer's (or visitor's) latent preferences."""
    archetype = archetype or weighted(rng, ARCHETYPE_WEIGHTS)
    occasions = ARCHETYPES[archetype]

    parents = list(next(iter(EVENT_GENRE_FIT.values())))
    genre = {parent: 0.15 for parent in parents}
    for event, share in occasions.items():
        for parent, fit in EVENT_GENRE_FIT.get(event, {}).items():
            genre[parent] = genre.get(parent, 0.15) + share * fit ** 2

    if favourites is None:
        favourites = []
        for _ in range(rng.choice([1, 1, 2])):
            favourites.append(weighted(rng, genre))
    for parent in favourites:
        genre[parent] = genre.get(parent, 0.15) + 1.2
    peak = max(genre.values())
    genre = {parent: value / peak for parent, value in genre.items()}

    return {
        "archetype": archetype,
        "occasions": occasions,
        "genre": genre,
        "favourite_sub": None,          # filled once the roster exists
        "city": city,
        "province": PROVINCE.get(city),
        "travel": rng.uniform(0.08, 0.35),
        "budget": budget or math.exp(rng.gauss(math.log(3500), 0.35)),
        "loyalty": rng.uniform(0.05, 0.40),
        "circle": rng.randrange(CIRCLES),
    }


def make_circles(rng: random.Random, artists: list[dict]) -> tuple[dict[int, set], dict[int, str]]:
    """Each circle's home city and the acts it knows about.

    A circle is anchored in a city and knows acts from there more than from
    elsewhere, and better acts more than weaker ones - word of mouth travels
    along reputation - but it is a set of particular acts, not a genre.
    """
    cities = sorted({a["city"] for a in artists})
    circle_city = {circle: rng.choice(cities) for circle in range(CIRCLES)}
    known: dict[int, set] = {}
    for circle in range(CIRCLES):
        pool = {a["artist_id"]: (2.5 if a["city"] == circle_city[circle] else 1.0) * (0.3 + a["quality"])
                for a in artists}
        chosen: set = set()
        while len(chosen) < min(CIRCLE_ACTS, len(pool)):
            chosen.add(weighted(rng, {k: v for k, v in pool.items() if k not in chosen}))
        known[circle] = chosen
    return known, circle_city


def attach_roster(rng: random.Random, people: list[dict], artists: list[dict],
                  circle_city: dict[int, str]) -> None:
    """Gives each person a favourite sub-genre and a circle, once acts exist."""
    subs_by_parent: dict[str, list[str]] = {}
    for a in artists:
        subs_by_parent.setdefault(a["parent"], []).append(a["primary_sub"])
    for person in people:
        profile = person["taste"]
        if rng.random() < 0.45:
            parent = max(profile["genre"], key=profile["genre"].get)
            if subs_by_parent.get(parent):
                profile["favourite_sub"] = rng.choice(subs_by_parent[parent])
        # Circles are local more often than not.
        local = [c for c, home in circle_city.items() if home == profile["city"]]
        if local and rng.random() < 0.7:
            profile["circle"] = rng.choice(local)


def appeal(person: dict, artist: dict, known: dict[int, set],
           history: dict | None = None) -> float:
    """How readily this person would choose this act, before exploration."""
    profile = person["taste"]
    score = profile["genre"].get(artist["parent"], 0.15) ** 1.5
    if profile["favourite_sub"] and artist["primary_sub"] == profile["favourite_sub"]:
        score *= 1.6

    if artist["city"] == profile["city"]:
        score *= 1.0
    elif profile["province"] and PROVINCE.get(artist["city"]) == profile["province"]:
        score *= 0.45
    else:
        score *= profile["travel"]

    score *= max(0.08, price_fit(profile["budget"], artist["rate"]))

    if artist["artist_id"] in known.get(profile["circle"], ()):
        score *= CIRCLE_BOOST
    if history and history.get((person["user_id"], artist["artist_id"])):
        score *= 1.0 + LOYALTY_BOOST * profile["loyalty"]
    return score


def choose(rng: random.Random, people: list[dict], artist: dict, known: dict[int, set],
           history: dict) -> dict:
    """Which organizer books this act, given everyone's preferences."""
    weights = [appeal(person, artist, known, history) for person in people]
    mean = sum(weights) / len(weights)
    weights = [(1.0 - EXPLORATION) * w + EXPLORATION * mean for w in weights]
    return rng.choices(people, weights=weights, k=1)[0]


def occasion_for(rng: random.Random, person: dict, artist: dict) -> str:
    occasions = person["taste"]["occasions"]
    weights = {event: share * (0.2 + EVENT_GENRE_FIT.get(event, {}).get(artist["parent"], 0.5))
               for event, share in occasions.items()}
    return weighted(rng, weights)


def search_text(rng: random.Random, artist: dict, occasion: str) -> str:
    noun = rng.choice(SEARCH_NOUN.get(artist["parent"], ["act"]))
    style = artist["primary_sub"].lower()
    templates = [
        f"{noun} for a {occasion.lower()}",
        f"{style} {noun} for a {occasion.lower()}",
        f"{style} for {occasion.lower()} in {artist['city']}",
        f"{noun} in {artist['city']}",
    ]
    return rng.choice(templates)


def _at(rng: random.Random, day: date) -> datetime:
    return datetime.combine(day, time(rng.randint(8, 22), rng.randint(0, 59), rng.randint(0, 59)))


def seed_interactions(conn, rng: random.Random, artists: list[dict], organizers: list[dict],
                      bookings: list[dict], known: dict[int, set], circle_city: dict[int, str],
                      today: date, *,
                      visitors: int = 900, window_days: int = 170) -> dict:
    """Profile views and searches that lead up to bookings, and visitors who never book.

    Organizers: before each recent booking request, a short shortlist - the act
    they booked and a few others they considered, chosen by the same appeal -
    opened in the days beforehand, often after a search. Plus some browsing that
    led nowhere.

    Visitors: signed-out browsers drawn from the same preferences, a few sessions
    each over the last two months, opening acts and typing searches.
    """
    by_id = {a["artist_id"]: a for a in artists}
    start = today - timedelta(days=window_days)
    rows: list[tuple] = []

    def view(user_id, visitor_id, artist_id, day):
        rows.append((user_id, visitor_id, artist_id, "PROFILE_VIEW", None, None, None, None, None,
                     _at(rng, day)))

    def search(user_id, visitor_id, text, city, occasion, day):
        rows.append((user_id, visitor_id, None, "SEARCH", text, None, city, occasion, None,
                     _at(rng, day)))

    def shortlist(person, around: date, exclude: int | None, size: int) -> list[int]:
        sample = rng.sample(artists, k=min(len(artists), 60))
        weights = [appeal(person, a, known) * (0.4 + a["quality"]) for a in sample]
        picked = []
        for _ in range(size):
            if not sample:
                break
            choice = rng.choices(range(len(sample)), weights=weights, k=1)[0]
            if sample[choice]["artist_id"] != exclude:
                picked.append(sample[choice]["artist_id"])
            sample.pop(choice)
            weights.pop(choice)
        return picked

    # ---- organizers, leading up to their bookings -------------------------
    for booking in bookings:
        requested = booking["requested"].date()
        if not (start <= requested <= today):
            continue
        person, artist = booking["booker"], booking["artist"]
        lead = rng.randint(1, 10)
        day = max(start, requested - timedelta(days=lead))
        if rng.random() < 0.55:
            search(person["user_id"], None, search_text(rng, artist, booking["event_type"]),
                   None, booking["event_type"], day)
        view(person["user_id"], None, artist["artist_id"], day)
        if rng.random() < 0.4:
            view(person["user_id"], None, artist["artist_id"],
                 min(requested, day + timedelta(days=rng.randint(0, 3))))
        for other in shortlist(person, day, artist["artist_id"], rng.randint(1, 3)):
            view(person["user_id"], None, other, day)

    # ---- organizers, browsing that went nowhere ----------------------------
    for person in organizers:
        for _ in range(rng.randint(0, 4)):
            day = start + timedelta(days=rng.randint(0, window_days))
            for other in shortlist(person, day, None, rng.randint(1, 3)):
                view(person["user_id"], None, other, day)

    # ---- visitors who never signed in --------------------------------------
    cities = list({a["city"] for a in artists})
    visitor_people = []
    for _ in range(visitors):
        visitor = {"user_id": None,
                   "visitor_id": str(uuid.UUID(int=rng.getrandbits(128), version=4)),
                   "taste": taste(rng, rng.choice(cities))}
        visitor_people.append(visitor)
    attach_roster(rng, visitor_people, artists, circle_city)

    for visitor in visitor_people:
        for _ in range(rng.choice([1, 1, 1, 2, 2, 3])):
            day = today - timedelta(days=rng.randint(0, 60))
            favourite = max(visitor["taste"]["genre"], key=visitor["taste"]["genre"].get)
            if rng.random() < 0.5:
                anchor = next((a for a in rng.sample(artists, k=min(40, len(artists)))
                               if a["parent"] == favourite), rng.choice(artists))
                search(None, visitor["visitor_id"],
                       search_text(rng, anchor, weighted(rng, visitor["taste"]["occasions"])),
                       None, None, day)
            for other in shortlist(visitor, day, None, rng.randint(2, 6)):
                view(None, visitor["visitor_id"], other, day)

    with conn.cursor() as cur:
        cur.executemany(
            """
            INSERT INTO user_interaction (user_id, visitor_id, artist_id, kind, search_query,
                                          genre, city, occasion, budget_per_hour, created_date)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            rows,
        )
    conn.commit()

    return {
        "interactions": len(rows),
        "views": sum(1 for r in rows if r[3] == "PROFILE_VIEW"),
        "searches": sum(1 for r in rows if r[3] == "SEARCH"),
        "visitors": len(visitor_people),
        "artists_viewed": len({r[2] for r in rows if r[2] is not None and r[2] in by_id}),
    }
