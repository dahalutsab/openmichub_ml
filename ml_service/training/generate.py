"""Synthetic booking data for training the ranker.

The platform has no booking history yet, so the ranker is bootstrapped on data
drawn from an explicit generative process. That process is stated here in full,
which is what makes the exercise meaningful: the model is not being handed noise
and asked to find structure, it is being asked to recover a known utility
function from noisy, partially-observed samples.

The process
-----------
An organizer issues a query: a genre they want, a city, a budget, an event type
and a party size. Every artist in the catalogue is a candidate. The organizer's
true satisfaction with an artist is

    u = 0.24 * genre_match          exact sub-genre, same parent genre, or neither
      + 0.16 * style_affinity       latent: does this act suit what was asked for
      + 0.18 * price_fit            peaks at ~85% of budget, falls off above it
      + 0.13 * rating_norm          the artist's average review score
      + 0.13 * location_match       same city, same province, or far
      + 0.08 * experience           bookings completed, log-scaled
      + 0.04 * event_fit            some genres suit some events
      + 0.04 * responsiveness       how reliably the artist replies
      + noise                       everything the features cannot see

`style_affinity` is deliberately latent — it is what an organizer means by
"something mellow for a restaurant", which the genre label only partly captures.
The model never sees it. What it sees is `text_similarity`: the cosine distance
between the query and the artist's bio, which is a *noisy observation* of that
latent fit. In production that number comes from the embedding model; here it is
simulated with the same noise characteristics.

This is the part that makes retrieval and ranking one system rather than two.
Without it the ranker discards everything vector search knows, and a free-text
query with no explicit genre filter ends up ordered by price and rating alone.

then bucketed into a graded relevance label 0-3.

The noise term matters. Without it the task is trivial and any model scores
perfectly, which would prove nothing. With it there is an irreducible error
floor, so the gap between the model and the baselines is the real result.

Nothing here is used at inference time. The trained model consumes exactly the
features that can be computed from live data, so it transfers to real bookings
once they exist.
"""

from __future__ import annotations

import argparse
import json
import math
from dataclasses import dataclass, asdict
from pathlib import Path

import numpy as np
import pandas as pd

# --- catalogue -------------------------------------------------------------

CITIES = {
    # city: (province, latitude, longitude)
    "Kathmandu": ("Bagmati", 27.7172, 85.3240),
    "Lalitpur": ("Bagmati", 27.6644, 85.3188),
    "Bhaktapur": ("Bagmati", 27.6710, 85.4298),
    "Pokhara": ("Gandaki", 28.2096, 83.9856),
    "Chitwan": ("Bagmati", 27.5291, 84.3542),
    "Butwal": ("Lumbini", 27.7006, 83.4484),
    "Nepalgunj": ("Lumbini", 28.0500, 81.6167),
    "Biratnagar": ("Koshi", 26.4525, 87.2718),
    "Dharan": ("Koshi", 26.8065, 87.2846),
    "Janakpur": ("Madhesh", 26.7288, 85.9266),
}

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

EVENT_TYPES = [
    "Wedding", "Corporate", "Festival", "Birthday",
    "Club Night", "Open Mic", "Charity Gala", "Restaurant",
]

# How well each parent genre suits each event, 0-1. Deliberately not uniform:
# this is the kind of structure a ranker should pick up and a rating-only
# baseline cannot.
EVENT_GENRE_FIT = {
    "Wedding":     {"Folk": 1.0, "Pop": 0.9, "Classical": 0.9, "Jazz": 0.7, "Blues": 0.5, "Rock": 0.4, "Hip-Hop": 0.3, "Electronic": 0.3},
    "Corporate":   {"Jazz": 1.0, "Classical": 0.9, "Pop": 0.7, "Folk": 0.6, "Blues": 0.6, "Electronic": 0.4, "Rock": 0.3, "Hip-Hop": 0.2},
    "Festival":    {"Rock": 1.0, "Electronic": 0.9, "Pop": 0.9, "Hip-Hop": 0.8, "Folk": 0.7, "Blues": 0.5, "Jazz": 0.5, "Classical": 0.3},
    "Birthday":    {"Pop": 1.0, "Hip-Hop": 0.8, "Rock": 0.8, "Electronic": 0.7, "Folk": 0.6, "Jazz": 0.5, "Blues": 0.4, "Classical": 0.3},
    "Club Night":  {"Electronic": 1.0, "Hip-Hop": 0.9, "Pop": 0.7, "Rock": 0.6, "Blues": 0.3, "Jazz": 0.3, "Folk": 0.2, "Classical": 0.1},
    "Open Mic":    {"Folk": 1.0, "Blues": 0.9, "Hip-Hop": 0.8, "Rock": 0.7, "Jazz": 0.7, "Pop": 0.6, "Classical": 0.4, "Electronic": 0.3},
    "Charity Gala":{"Classical": 1.0, "Jazz": 0.9, "Folk": 0.8, "Pop": 0.7, "Blues": 0.6, "Rock": 0.4, "Electronic": 0.3, "Hip-Hop": 0.3},
    "Restaurant":  {"Jazz": 1.0, "Blues": 0.9, "Folk": 0.9, "Classical": 0.8, "Pop": 0.6, "Rock": 0.3, "Electronic": 0.3, "Hip-Hop": 0.2},
}

# Weights of the true utility function. The evaluation reports how closely the
# trained model's feature importances line up with these.
TRUE_WEIGHTS = {
    "genre_match": 0.24,
    "style_affinity": 0.16,
    "price_fit": 0.18,
    "rating_norm": 0.13,
    "location_match": 0.13,
    "experience": 0.08,
    "event_fit": 0.04,
    "responsiveness": 0.04,
}

NOISE_SD = 0.12

# How faithfully the embedding reflects the latent style fit. Larger means the
# text signal is less trustworthy, and the ranker should lean on it less.
SIMILARITY_NOISE_SD = 0.10

# Fraction of queries where the organizer states no genre / no budget.
#
# This matters more than it looks. An organizer who types "something mellow for a
# restaurant" wants a particular kind of act, but never says which genre — so at
# serving time `genre_match` cannot be computed, and `price_fit` cannot either
# without a budget. If every training row carried both, the model would put most
# of its weight on features that are absent exactly when a free-text search runs,
# and rank on almost nothing.
#
# Those signals are therefore withheld from the model on a share of rows, as NaN,
# which LightGBM routes down its own branch. The organizer's true preference still
# drives the label: they know what they want, they just did not type it. The model
# learns to lean on text similarity instead when the explicit signal is missing.
GENRE_UNSPECIFIED_RATE = 0.45
BUDGET_UNSPECIFIED_RATE = 0.35


@dataclass
class GeneratorConfig:
    n_artists: int = 600
    n_queries: int = 4000
    candidates_per_query: int = 25
    noise_sd: float = NOISE_SD
    seed: int = 42


def _haversine_km(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1, lat2, lon2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 6371.0 * 2 * math.asin(math.sqrt(h))


def generate_artists(cfg: GeneratorConfig, rng: np.random.Generator) -> pd.DataFrame:
    """A catalogue of performers, in the shape the real `artists` table holds."""
    cities = list(CITIES)
    parents = list(GENRES)
    rows = []

    for artist_id in range(1, cfg.n_artists + 1):
        parent = rng.choice(parents)
        subs = GENRES[parent]
        # Most artists play one or two sub-genres of a single parent.
        n_sub = int(rng.choice([1, 2, 3], p=[0.5, 0.35, 0.15]))
        sub_genres = list(rng.choice(subs, size=min(n_sub, len(subs)), replace=False))
        city = rng.choice(cities)

        # Rate correlates with rating and experience, with real spread. A
        # log-normal keeps it positive and right-skewed, like real pricing.
        base_skill = float(np.clip(rng.normal(0.55, 0.2), 0.02, 0.99))
        hourly_rate = float(np.round(rng.lognormal(mean=math.log(2200), sigma=0.55) * (0.6 + base_skill), -1))
        hourly_rate = float(np.clip(hourly_rate, 500, 40000))

        rating = float(np.clip(rng.normal(3.2 + 1.4 * base_skill, 0.45), 1.0, 5.0))
        completed = int(np.clip(rng.poisson(2 + 28 * base_skill), 0, 400))
        response_rate = float(np.clip(rng.beta(2 + 6 * base_skill, 2), 0.05, 1.0))

        rows.append({
            "artist_id": artist_id,
            "stage_name": f"Artist {artist_id}",
            "parent_genre": parent,
            "sub_genres": sub_genres,
            "city": city,
            "province": CITIES[city][0],
            "hourly_rate": hourly_rate,
            "rating": round(rating, 2),
            "completed_bookings": completed,
            "response_rate": round(response_rate, 3),
        })

    return pd.DataFrame(rows)


def generate_queries(cfg: GeneratorConfig, rng: np.random.Generator) -> pd.DataFrame:
    """Search intents: what an organizer is actually looking for."""
    cities = list(CITIES)
    parents = list(GENRES)
    rows = []

    for query_id in range(1, cfg.n_queries + 1):
        parent = rng.choice(parents)
        wanted_sub = rng.choice(GENRES[parent])
        city = rng.choice(cities)
        event_type = rng.choice(EVENT_TYPES)

        budget = float(np.round(rng.lognormal(mean=math.log(4000), sigma=0.6), -2))
        budget = float(np.clip(budget, 800, 60000))
        hours = int(rng.choice([2, 3, 4, 5, 6], p=[0.2, 0.35, 0.25, 0.15, 0.05]))

        rows.append({
            "query_id": query_id,
            "genre_stated": bool(rng.random() > GENRE_UNSPECIFIED_RATE),
            "budget_stated": bool(rng.random() > BUDGET_UNSPECIFIED_RATE),
            "wanted_parent_genre": parent,
            "wanted_sub_genre": wanted_sub,
            "city": city,
            "province": CITIES[city][0],
            "event_type": event_type,
            "budget_per_hour": budget,
            "hours": hours,
        })

    return pd.DataFrame(rows)


def _genre_match(query: dict, artist: dict) -> float:
    if query["wanted_sub_genre"] in artist["sub_genres"]:
        return 1.0
    if query["wanted_parent_genre"] == artist["parent_genre"]:
        return 0.6
    return 0.0


def _price_fit(budget: float, rate: float) -> float:
    """Peaks slightly under budget.

    Cheap is not automatically better — organizers read a very low rate as low
    quality — and anything over budget falls away fast.
    """
    if budget <= 0:
        return 0.0
    ratio = rate / budget
    if ratio > 1.0:
        return float(max(0.0, 1.0 - 2.2 * (ratio - 1.0)))
    return float(math.exp(-((ratio - 0.85) ** 2) / (2 * 0.28 ** 2)))


def _location_match(query: dict, artist: dict) -> float:
    if query["city"] == artist["city"]:
        return 1.0
    if query["province"] == artist["province"]:
        return 0.65
    km = _haversine_km(
        (CITIES[query["city"]][1], CITIES[query["city"]][2]),
        (CITIES[artist["city"]][1], CITIES[artist["city"]][2]),
    )
    return float(max(0.0, 1.0 - km / 600.0)) * 0.5


def build_pairs(artists: pd.DataFrame, queries: pd.DataFrame,
                cfg: GeneratorConfig, rng: np.random.Generator) -> pd.DataFrame:
    """One row per (query, candidate artist), with the graded relevance label.

    Candidates are sampled with a bias toward the requested genre, which is what
    a first-pass retrieval layer would surface. Sampling purely at random would
    make almost every candidate irrelevant and the ranking task degenerate.
    """
    artist_records = artists.to_dict("records")
    by_parent: dict[str, list[dict]] = {}
    for record in artist_records:
        by_parent.setdefault(record["parent_genre"], []).append(record)

    rows = []
    for query in queries.to_dict("records"):
        same_genre = by_parent.get(query["wanted_parent_genre"], [])
        n_same = min(len(same_genre), int(cfg.candidates_per_query * 0.6))
        n_other = cfg.candidates_per_query - n_same

        chosen = []
        if n_same:
            idx = rng.choice(len(same_genre), size=n_same, replace=False)
            chosen.extend(same_genre[i] for i in idx)
        if n_other:
            idx = rng.choice(len(artist_records), size=n_other, replace=False)
            chosen.extend(artist_records[i] for i in idx)

        # Deduplicate while preserving order.
        seen, candidates = set(), []
        for artist in chosen:
            if artist["artist_id"] not in seen:
                seen.add(artist["artist_id"])
                candidates.append(artist)

        for artist in candidates:
            genre_match = _genre_match(query, artist)
            price_fit = _price_fit(query["budget_per_hour"], artist["hourly_rate"])
            rating_norm = (artist["rating"] - 1.0) / 4.0
            location_match = _location_match(query, artist)
            experience = min(1.0, math.log1p(artist["completed_bookings"]) / math.log1p(120))
            event_fit = EVENT_GENRE_FIT[query["event_type"]].get(artist["parent_genre"], 0.5)
            responsiveness = artist["response_rate"]

            # Latent: how well this act actually suits what was asked for. Genre
            # explains much of it, but not all — two folk acts differ.
            style_affinity = float(np.clip(
                0.55 * genre_match + 0.45 * rng.beta(2.0, 2.0), 0.0, 1.0))

            # Observed: what the embedding reports about that fit.
            text_similarity = float(np.clip(
                style_affinity + rng.normal(0.0, SIMILARITY_NOISE_SD), 0.0, 1.0))

            signals = {
                "genre_match": genre_match,
                "style_affinity": style_affinity,
                "price_fit": price_fit,
                "rating_norm": rating_norm,
                "location_match": location_match,
                "experience": experience,
                "event_fit": event_fit,
                "responsiveness": responsiveness,
            }
            utility = sum(TRUE_WEIGHTS[k] * v for k, v in signals.items())
            utility += rng.normal(0.0, cfg.noise_sd)

            # What the model is allowed to see, as opposed to what drove the
            # label. Withheld signals are NaN, not zero: zero means "no match",
            # NaN means "not stated", and conflating them teaches the model that
            # an unstated genre is a bad match.
            observed_genre_match = genre_match if query["genre_stated"] else float("nan")
            observed_price_fit = price_fit if query["budget_stated"] else float("nan")

            rows.append({
                "query_id": query["query_id"],
                "artist_id": artist["artist_id"],
                "event_type": query["event_type"],
                "budget_per_hour": query["budget_per_hour"],
                "hours": query["hours"],
                "query_city": query["city"],
                "wanted_parent_genre": query["wanted_parent_genre"],
                "wanted_sub_genre": query["wanted_sub_genre"],
                "utility": utility,
                "text_similarity": text_similarity,
                "genre_stated": query["genre_stated"],
                "budget_stated": query["budget_stated"],
                **signals,
                # Overwrite the two the model may not see in full.
                "genre_match": observed_genre_match,
                "price_fit": observed_price_fit,
                "true_genre_match": genre_match,
                "true_price_fit": price_fit,
            })

    pairs = pd.DataFrame(rows)

    # Graded relevance, assigned within each query rather than globally: what
    # matters to a ranker is the order inside one result list.
    def _label(group: pd.DataFrame) -> pd.Series:
        ranks = group["utility"].rank(pct=True)
        return pd.cut(ranks, bins=[0, 0.5, 0.75, 0.9, 1.0],
                      labels=[0, 1, 2, 3], include_lowest=True).astype(int)

    pairs["relevance"] = (
        pairs.groupby("query_id", group_keys=False).apply(_label, include_groups=False)
    )
    return pairs


def generate(cfg: GeneratorConfig) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    rng = np.random.default_rng(cfg.seed)
    artists = generate_artists(cfg, rng)
    queries = generate_queries(cfg, rng)
    pairs = build_pairs(artists, queries, cfg, rng)
    return artists, queries, pairs


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate the synthetic training set.")
    parser.add_argument("--artists", type=int, default=GeneratorConfig.n_artists)
    parser.add_argument("--queries", type=int, default=GeneratorConfig.n_queries)
    parser.add_argument("--candidates", type=int, default=GeneratorConfig.candidates_per_query)
    parser.add_argument("--noise", type=float, default=GeneratorConfig.noise_sd)
    parser.add_argument("--seed", type=int, default=GeneratorConfig.seed)
    parser.add_argument("--out", type=Path, default=Path("/app/models/dataset"))
    args = parser.parse_args()

    cfg = GeneratorConfig(
        n_artists=args.artists, n_queries=args.queries,
        candidates_per_query=args.candidates, noise_sd=args.noise, seed=args.seed,
    )
    artists, queries, pairs = generate(cfg)

    args.out.mkdir(parents=True, exist_ok=True)
    artists.to_parquet(args.out / "artists.parquet", index=False)
    queries.to_parquet(args.out / "queries.parquet", index=False)
    pairs.to_parquet(args.out / "pairs.parquet", index=False)
    (args.out / "generator_config.json").write_text(
        json.dumps({"config": asdict(cfg), "true_weights": TRUE_WEIGHTS}, indent=2)
    )

    print(f"artists  {len(artists):>7,}")
    print(f"queries  {len(queries):>7,}")
    print(f"pairs    {len(pairs):>7,}")
    print("\nrelevance distribution")
    print(pairs["relevance"].value_counts().sort_index().to_string())
    print(f"\nwritten to {args.out}")


if __name__ == "__main__":
    main()
