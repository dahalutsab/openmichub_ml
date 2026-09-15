"""Does the recommender put in front of people what they go on to choose?

    docker compose exec ml python -m training.evaluate_recs

A time-split replay over the platform's own history. Everything before a cutoff
date is treated as the past - it builds taste profiles, co-choice and demand
exactly as serving would have built them on that day - and the question is
whether each strategy's top ten contains what people actually chose after it.

Two populations, measured separately because they are served differently:

  organizers   accounts with at least three booking requests before the cutoff
               and at least one after. Targets are the artists they requested
               after it. Reported twice: all targets, and only acts they had
               never booked before ("new to them"), because rebooking a known
               act is easy to predict and says little about discovery.

  visitors     signed-out browsers with at least two profile views before the
               cutoff and one after. Targets are the acts they opened after it.

Metrics, all at ten: hit rate (share of people with at least one target in
their list), recall, NDCG, and two about the lists rather than the people -
catalogue coverage (share of acts that appear in anyone's list) and genres per
list.

What this is, and is not, evidence of. On the demo database the history comes
from `training/behaviour.py`, a stated model of how organizers choose, so these
numbers measure how well each strategy recovers *those* mechanisms. They rank
strategies against each other fairly - every strategy sees the same past - but
they are not a forecast of lift on real traffic. Run against a database with real
history, the same script measures the real thing.

Two simplifications, both in favour of the older strategies rather than the new:
every strategy scores the whole catalogue rather than a retrieved pool, and the
artists' completed-booking counts use today's statuses.
"""

from __future__ import annotations

import argparse
import json
import logging
import random
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path

import numpy as np

from app import blend, personalization, ranker
from app.config import get_settings
from app.db import connection
from app.personalization import TasteProfile
from app.repository import fetch_artists
from app.signals import build_signals

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(levelname)-5s %(message)s")

K = 10


def _rows(sql: str, params: dict | None = None) -> list[tuple]:
    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, params or {})
        return cur.fetchall()


@dataclass
class World:
    artists: list[dict]
    bookings: list[tuple]        # (user_id, artist_id, status, event_type, rate, requested_at)
    interactions: list[tuple]    # (user_id, visitor_id, kind, artist_id, query, genre, city,
                                 #  occasion, budget, created)
    reviews: list[tuple]         # (artist_id, rating, created)
    vectors: dict[int, np.ndarray] = field(default_factory=dict)


def load_world() -> World:
    artists = fetch_artists()
    bookings = _rows("""
        SELECT b.user_id, b.artist_id, b.status, b.event_type, a.hourly_rate,
               COALESCE(b.created_date, b.event_date::timestamp)
        FROM booking b JOIN artists a ON a.id = b.artist_id
        WHERE b.user_id IS NOT NULL""")
    interactions = _rows("""
        SELECT user_id, visitor_id, kind, artist_id, search_query, genre, city, occasion,
               budget_per_hour, created_date
        FROM user_interaction WHERE kind <> 'CLICK'""")
    reviews = _rows("SELECT artist_id, rating, created_date FROM review")
    vectors = personalization._embeddings([a["artist_id"] for a in artists])
    return World(artists, bookings, interactions, reviews, vectors)


# --------------------------------------------------------------------------- #
# The world as it stood at the cutoff
# --------------------------------------------------------------------------- #

def artists_as_of(world: World, cutoff: datetime) -> list[dict]:
    """Ratings, review counts and track record from what existed at the cutoff."""
    settings = get_settings()
    scores: dict[int, list[int]] = {}
    for artist_id, rating, created in world.reviews:
        if created is not None and created <= cutoff:
            scores.setdefault(int(artist_id), []).append(int(rating))
    all_scores = [s for values in scores.values() for s in values]
    mean = float(np.mean(all_scores)) if all_scores else 4.0

    completed: dict[int, int] = {}
    answered: dict[int, int] = {}
    total: dict[int, int] = {}
    for _, artist_id, status, _, _, when in world.bookings:
        if when is None or when > cutoff:
            continue
        artist_id = int(artist_id)
        total[artist_id] = total.get(artist_id, 0) + 1
        if status in ("COMPLETED", "CONFIRMED"):
            completed[artist_id] = completed.get(artist_id, 0) + 1
        if status != "PENDING":
            answered[artist_id] = answered.get(artist_id, 0) + 1

    prior = settings.rating_prior_reviews
    out = []
    for artist in world.artists:
        a = dict(artist)
        artist_id = a["artist_id"]
        values = scores.get(artist_id, [])
        raw = float(np.mean(values)) if values else None
        a["rating"] = raw if raw is not None else 3.0
        a["review_count"] = len(values)
        a["rating_smoothed"] = (prior * mean + len(values) * (raw if raw is not None else mean)) / (prior + len(values))
        a["completed_bookings"] = completed.get(artist_id, 0)
        a["response_rate"] = (answered.get(artist_id, 0) / total[artist_id]) if total.get(artist_id) else 0.75
        a["similarity"] = None
        out.append(a)
    return out


def signals_as_of(world: World, cutoff: datetime):
    bookings = [(f"u{u}", a, s, when) for u, a, s, _, _, when in world.bookings]
    views = [(f"u{u}" if u is not None else f"v{v}", a, when)
             for u, v, kind, a, *_rest, when in world.interactions
             if kind == "PROFILE_VIEW" and a is not None]
    return build_signals(bookings, views, [], [], cutoff)


def profile_as_of(world: World, cutoff: datetime, *, user_id=None, visitor_id=None) -> TasteProfile:
    window = timedelta(days=get_settings().taste_window_days)
    interactions = [
        (kind, a, q, g, c, o, b, when)
        for u, v, kind, a, q, g, c, o, b, when in world.interactions
        if when is not None and cutoff - window < when <= cutoff
        and ((user_id is not None and u == user_id) or (visitor_id is not None and v == visitor_id))
    ]
    bookings = [
        (a, s, e, rate, when)
        for u, a, s, e, rate, when in world.bookings
        if user_id is not None and u == user_id and when is not None and cutoff - window < when <= cutoff
    ]
    profile = TasteProfile(user_id=user_id, visitor_id=visitor_id)
    if interactions or bookings:
        profile = personalization.profile_from_history(profile, interactions, bookings, cutoff)
    return profile


# --------------------------------------------------------------------------- #
# Strategies: each returns a ranked list of artist ids
# --------------------------------------------------------------------------- #

def _model_scores(artists: list[dict], *, smoothed: bool) -> np.ndarray:
    rows = artists if smoothed else [{k: v for k, v in a.items() if k != "rating_smoothed"}
                                     for a in artists]
    frame = ranker.build_candidate_frame(rows, city=None, budget_per_hour=None,
                                         event_type=None, wanted_genre=None)
    return ranker.score(frame)


def _top(artists: list[dict], scores: np.ndarray, k: int = K, exclude: set | None = None) -> list[int]:
    order = np.argsort(-np.asarray(scores, dtype=np.float64), kind="stable")
    out = []
    for index in order:
        artist_id = artists[index]["artist_id"]
        if exclude and artist_id in exclude:
            continue
        out.append(artist_id)
        if len(out) == k:
            break
    return out


def _ordered(artists: list[dict], scores: np.ndarray, vectors, exclude: set | None = None,
             familiar: set | None = None) -> list[int]:
    """The order serving would show: variety, then the cap on already-known acts."""
    keep = [i for i, a in enumerate(artists) if not exclude or a["artist_id"] not in exclude]
    subset = [artists[i] for i in keep]
    sub_scores = np.asarray(scores, dtype=np.float64)[keep]
    order = blend.diversify(subset, sub_scores, vectors, K, personalization._taste_band())
    if familiar:
        order = blend.limit_familiar(order, subset, familiar, K)
    return [subset[i]["artist_id"] for i in order[:K]]


# The personal share the front page used before `tune_recs` chose 0.90. Fixed here
# rather than read from settings, so "before" keeps meaning before.
OLD_ALPHA_UNFILTERED = 0.60


class OldWeights:
    """Personalisation as it was before co-choice took a share of affinity."""

    def __enter__(self):
        self.saved = (personalization.W_CO_CHOICE, personalization.W_PERSONAL)
        personalization.W_CO_CHOICE = 0.0
        personalization.W_PERSONAL = 1.0 - (personalization.W_FAMILIARITY
                                            + personalization.W_BUDGET + personalization.W_CITY)

    def __exit__(self, *exc):
        personalization.W_CO_CHOICE, personalization.W_PERSONAL = self.saved


# --------------------------------------------------------------------------- #
# Metrics
# --------------------------------------------------------------------------- #

def _metrics(lists: dict, targets: dict, artists: list[dict]) -> dict:
    parents = {a["artist_id"]: tuple(a.get("parent_genres") or ()) for a in artists}
    hits, recalls, ndcgs, genres = [], [], [], []
    shown: set = set()
    for person, ranked in lists.items():
        truth = targets[person]
        if not truth:
            continue
        gains = [1.0 if artist in truth else 0.0 for artist in ranked[:K]]
        hits.append(1.0 if any(gains) else 0.0)
        recalls.append(sum(gains) / min(len(truth), K))
        dcg = sum(g / np.log2(i + 2) for i, g in enumerate(gains))
        ideal = sum(1.0 / np.log2(i + 2) for i in range(min(len(truth), K)))
        ndcgs.append(dcg / ideal if ideal else 0.0)
        shown.update(ranked[:K])
        genres.append(len({p for artist in ranked[:K] for p in parents.get(artist, ())}))
    n = len(hits)
    return {
        "people": n,
        "hit_rate@10": round(float(np.mean(hits)), 3) if n else None,
        "recall@10": round(float(np.mean(recalls)), 3) if n else None,
        "ndcg@10": round(float(np.mean(ndcgs)), 3) if n else None,
        "coverage@10": round(len(shown) / max(len(artists), 1), 3),
        "genres_per_list": round(float(np.mean(genres)), 2) if n else None,
    }


# --------------------------------------------------------------------------- #
# Runs
# --------------------------------------------------------------------------- #

def evaluate_organizers(world: World, cutoff: datetime, rng: random.Random) -> dict:
    settings = get_settings()
    artists = artists_as_of(world, cutoff)
    platform = signals_as_of(world, cutoff)

    before: dict[int, set] = {}
    after: dict[int, set] = {}
    for user, artist, _status, _event, _rate, when in world.bookings:
        if when is None:
            continue
        (before if when <= cutoff else after).setdefault(user, set()).add(int(artist))
    people = [u for u in after if len(before.get(u, ())) >= 3]
    log.info("Organizers: %d with history before %s and a booking after", len(people), cutoff.date())

    old_front = _model_scores(artists, smoothed=False)
    new_model = _model_scores(artists, smoothed=True)
    new_front_scores, _ = blend.platform_blend(artists, blend.to_unit_scale(new_model),
                                               blend.SURFACE_HOME, platform)
    popularity = np.array([a["completed_bookings"] for a in artists], dtype=np.float64)

    strategies = ["random", "most booked ever", "front page before", "front page now",
                  "personalised before", "co-choice alone", "personalised now"]
    lists = {mode: {s: {} for s in strategies} for mode in ("all", "new")}
    targets = {"all": {}, "new": {}}

    for user in people:
        booked_before = before.get(user, set())
        targets["all"][user] = after[user]
        targets["new"][user] = after[user] - booked_before

        profile = profile_as_of(world, cutoff, user_id=user)
        seeds = profile.seeds()
        co = platform.co_choice(seeds)

        with OldWeights():
            old_personal, _ = personalization.rerank(
                profile, artists, old_front, OLD_ALPHA_UNFILTERED)
        new_personal, reasons = personalization.rerank(
            profile, artists, new_model, settings.taste_alpha_browse_unfiltered,
            co, platform.co_scale)
        new_personal, _ = blend.platform_blend(artists, new_personal, blend.SURFACE_HOME,
                                               platform, reasons=reasons)
        co_alone = np.array([co.get(a["artist_id"], (0.0, 0))[0] for a in artists])

        for mode, exclude in (("all", None), ("new", booked_before)):
            shuffled = [a["artist_id"] for a in artists if not exclude or a["artist_id"] not in exclude]
            rng.shuffle(shuffled)
            lists[mode]["random"][user] = shuffled[:K]
            lists[mode]["most booked ever"][user] = _top(artists, popularity, exclude=exclude)
            lists[mode]["front page before"][user] = _top(artists, old_front, exclude=exclude)
            lists[mode]["front page now"][user] = _ordered(artists, new_front_scores, world.vectors, exclude)
            lists[mode]["personalised before"][user] = _top(artists, old_personal, exclude=exclude)
            lists[mode]["co-choice alone"][user] = _top(artists, co_alone, exclude=exclude)
            lists[mode]["personalised now"][user] = _ordered(artists, new_personal, world.vectors, exclude,
                                                              set(seeds))

    return {mode: {s: _metrics(lists[mode][s], targets[mode], artists) for s in strategies}
            for mode in ("all", "new")}


def evaluate_visitors(world: World, cutoff: datetime, limit: int) -> dict:
    settings = get_settings()
    artists = artists_as_of(world, cutoff)
    platform = signals_as_of(world, cutoff)

    before: dict[str, set] = {}
    after: dict[str, set] = {}
    for _u, visitor, kind, artist, *_rest, when in world.interactions:
        if visitor is None or kind != "PROFILE_VIEW" or artist is None or when is None:
            continue
        (before if when <= cutoff else after).setdefault(visitor, set()).add(int(artist))
    people = sorted(v for v in after if len(before.get(v, ())) >= 2)[:limit]
    log.info("Visitors: %d with views before %s and after", len(people), cutoff.date())

    old_front = _model_scores(artists, smoothed=False)
    new_model = _model_scores(artists, smoothed=True)
    front_now, _ = blend.platform_blend(artists, blend.to_unit_scale(new_model),
                                        blend.SURFACE_HOME, platform)

    strategies = ["front page before (identical for every visitor)", "front page now, unpersonalised",
                  "personalised now"]
    lists = {s: {} for s in strategies}
    targets = {}
    for visitor in people:
        seen = before[visitor]
        targets[visitor] = after[visitor] - seen
        profile = profile_as_of(world, cutoff, visitor_id=visitor)
        lists[strategies[0]][visitor] = _top(artists, old_front, exclude=seen)
        lists[strategies[1]][visitor] = _ordered(artists, front_now, world.vectors, seen)
        if profile.usable:
            co = platform.co_choice(profile.seeds())
            scores, reasons = personalization.rerank(
                profile, artists, new_model, settings.taste_alpha_browse_unfiltered, co, platform.co_scale)
            scores, _ = blend.platform_blend(artists, scores, blend.SURFACE_HOME, platform,
                                             reasons=reasons, viewer=f"v{visitor}")
        else:
            scores = front_now
        lists[strategies[2]][visitor] = _ordered(artists, scores, world.vectors, seen,
                                                 set(profile.seeds()))

    return {s: _metrics(lists[s], targets, artists) for s in strategies}


def _table(title: str, results: dict) -> str:
    columns = ["people", "hit_rate@10", "recall@10", "ndcg@10", "coverage@10", "genres_per_list"]
    lines = [f"\n{title}", "| strategy | " + " | ".join(columns) + " |",
             "|---|" + "---|" * len(columns)]
    for name, metrics in results.items():
        lines.append(f"| {name} | " + " | ".join(str(metrics[c]) for c in columns) + " |")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--organizer-cutoff-days", type=int, default=45)
    parser.add_argument("--visitor-cutoff-days", type=int, default=14)
    parser.add_argument("--visitors", type=int, default=400, help="cap on visitors evaluated")
    parser.add_argument("--seed", type=int, default=11)
    args = parser.parse_args()

    now = datetime.now()
    world = load_world()
    log.info("Loaded %d artists, %d bookings, %d interactions",
             len(world.artists), len(world.bookings), len(world.interactions))

    organizers = evaluate_organizers(world, now - timedelta(days=args.organizer_cutoff_days),
                                     random.Random(args.seed))
    visitors = evaluate_visitors(world, now - timedelta(days=args.visitor_cutoff_days), args.visitors)

    report = {"evaluated_at": now.isoformat(timespec="seconds"),
              "organizer_cutoff_days": args.organizer_cutoff_days,
              "visitor_cutoff_days": args.visitor_cutoff_days,
              "organizers": organizers, "visitors": visitors}
    out = Path(get_settings().model_dir) / "recs_eval.json"
    try:
        out.write_text(json.dumps(report, indent=2))
    except OSError:
        log.warning("Could not write %s", out)

    print(_table("Organizers - every act they requested after the cutoff", organizers["all"]))
    print(_table("Organizers - only acts new to them", organizers["new"]))
    print(_table("Signed-out visitors - acts they opened after the cutoff", visitors))


if __name__ == "__main__":
    main()
