"""Chooses the blend's stated weights on history the evaluation does not report on.

    docker compose exec ml python -m training.tune_recs

`evaluate_recs` reports on what people chose after a cutoff 45 days ago. Tuning
the weights against those same choices would report a number the weights were
fitted to, so this tunes on an earlier window instead: history before a cutoff
90 days ago, targets chosen between 90 and 45 days ago. Whatever wins here is
then written into `config.py` / `personalization.py` by hand and measured on the
reported window by `evaluate_recs`, which it has never seen.

A small grid, not a search: four knobs, a handful of values each, every one of
them a policy someone could argue with rather than a parameter to be squeezed.
"""

from __future__ import annotations

import argparse
import itertools
import logging
from datetime import datetime, timedelta

import numpy as np

from app import blend, personalization
from app.config import get_settings
from training.evaluate_recs import (
    _metrics, _model_scores, _ordered, artists_as_of, load_world,
    profile_as_of, signals_as_of,
)

log = logging.getLogger(__name__)


def prepare(world, cutoff: datetime, until: datetime,
            visitor_cutoff: datetime, visitor_until: datetime):
    """Organizers and visitors on their own windows, each with the world as of its cutoff.

    Visitors get a later window because signed-out browsing is recent by nature -
    unclaimed history is deleted after 90 days - but it still ends before the
    cutoff `evaluate_recs` reports visitors on.
    """
    artists = artists_as_of(world, cutoff)
    platform = signals_as_of(world, cutoff)

    before: dict[int, set] = {}
    after: dict[int, set] = {}
    for user, artist, _s, _e, _r, when in world.bookings:
        if when is None:
            continue
        if when <= cutoff:
            before.setdefault(user, set()).add(int(artist))
        elif when <= until:
            after.setdefault(user, set()).add(int(artist))
    people = [u for u in after if len(before.get(u, ())) >= 3]

    profiles = {u: profile_as_of(world, cutoff, user_id=u) for u in people}
    co = {u: platform.co_choice(profiles[u].seeds()) for u in people}
    model = _model_scores(artists, smoothed=True)

    visitor_artists = artists_as_of(world, visitor_cutoff)
    visitor_platform = signals_as_of(world, visitor_cutoff)
    visitor_before: dict[str, set] = {}
    visitor_after: dict[str, set] = {}
    for _u, visitor, kind, artist, *_rest, when in world.interactions:
        if visitor is None or kind != "PROFILE_VIEW" or artist is None or when is None:
            continue
        if when <= visitor_cutoff:
            visitor_before.setdefault(visitor, set()).add(int(artist))
        elif when <= visitor_until:
            visitor_after.setdefault(visitor, set()).add(int(artist))
    visitors = sorted(v for v in visitor_after if len(visitor_before.get(v, ())) >= 2)[:300]
    visitor_profiles = {v: profile_as_of(world, visitor_cutoff, visitor_id=v) for v in visitors}
    visitor_co = {v: visitor_platform.co_choice(visitor_profiles[v].seeds()) for v in visitors}

    return dict(artists=artists, platform=platform, people=people, before=before, after=after,
                profiles=profiles, co=co, model=model, visitors=visitors,
                visitor_artists=visitor_artists, visitor_platform=visitor_platform,
                visitor_model=_model_scores(visitor_artists, smoothed=True),
                visitor_before=visitor_before, visitor_after=visitor_after,
                visitor_profiles=visitor_profiles, visitor_co=visitor_co)


def run(world, data, *, demand: float, diversity: float, co_weight: float, alpha: float) -> dict:
    settings = get_settings()
    settings.demand_weight_home = demand
    settings.diversity_lambda = diversity
    saved = (personalization.W_CO_CHOICE, personalization.W_PERSONAL)
    personalization.W_CO_CHOICE = co_weight
    personalization.W_PERSONAL = 1.0 - (personalization.W_FAMILIARITY + personalization.W_BUDGET
                                        + personalization.W_CITY + co_weight)
    artists, platform = data["artists"], data["platform"]
    try:
        lists_all, lists_new, targets_all, targets_new = {}, {}, {}, {}
        for user in data["people"]:
            profile = data["profiles"][user]
            scores, reasons = personalization.rerank(profile, artists, data["model"], alpha,
                                                     data["co"][user], platform.co_scale)
            scores, _ = blend.platform_blend(artists, scores, blend.SURFACE_HOME, platform,
                                             reasons=reasons)
            booked = data["before"].get(user, set())
            lists_all[user] = _ordered(artists, scores, world.vectors, None, set(profile.seeds()))
            lists_new[user] = _ordered(artists, scores, world.vectors, booked, set(profile.seeds()))
            targets_all[user] = data["after"][user]
            targets_new[user] = data["after"][user] - booked

        lists_visitors, targets_visitors = {}, {}
        v_artists, v_platform = data["visitor_artists"], data["visitor_platform"]
        for visitor in data["visitors"]:
            profile = data["visitor_profiles"][visitor]
            seen = data["visitor_before"][visitor]
            if profile.usable:
                scores, reasons = personalization.rerank(profile, v_artists, data["visitor_model"], alpha,
                                                         data["visitor_co"][visitor], v_platform.co_scale)
                scores, _ = blend.platform_blend(v_artists, scores, blend.SURFACE_HOME, v_platform,
                                                 reasons=reasons)
            else:
                scores, _ = blend.platform_blend(v_artists, blend.to_unit_scale(data["visitor_model"]),
                                                 blend.SURFACE_HOME, v_platform)
            lists_visitors[visitor] = _ordered(v_artists, scores, world.vectors, seen)
            targets_visitors[visitor] = data["visitor_after"][visitor] - seen

        return {
            "organizers": _metrics(lists_all, targets_all, artists),
            "organizers_new": _metrics(lists_new, targets_new, artists),
            "visitors": _metrics(lists_visitors, targets_visitors, v_artists),
        }
    finally:
        personalization.W_CO_CHOICE, personalization.W_PERSONAL = saved


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cutoff-days", type=int, default=90)
    parser.add_argument("--until-days", type=int, default=45)
    parser.add_argument("--visitor-cutoff-days", type=int, default=30)
    parser.add_argument("--visitor-until-days", type=int, default=14)
    parser.add_argument("--demand", type=float, nargs="+", default=[0.0, 0.05, 0.10, 0.25])
    parser.add_argument("--diversity", type=float, nargs="+", default=[1.0, 0.9, 0.8])
    parser.add_argument("--co-weight", type=float, nargs="+", default=[0.18, 0.35, 0.50])
    parser.add_argument("--alpha", type=float, nargs="+", default=[0.6, 0.8])
    args = parser.parse_args()

    now = datetime.now()
    world = load_world()
    data = prepare(world, now - timedelta(days=args.cutoff_days), now - timedelta(days=args.until_days),
                   now - timedelta(days=args.visitor_cutoff_days),
                   now - timedelta(days=args.visitor_until_days))
    log.info("Tuning on %d organizers and %d visitors", len(data["people"]), len(data["visitors"]))

    grid = itertools.product(args.demand,       # demand on the front page
                             args.diversity,    # diversity lambda (1.0 = off)
                             args.co_weight,    # co-choice share of affinity
                             args.alpha)        # personal share of the final score
    rows = []
    for demand, diversity, co_weight, alpha in grid:
        result = run(world, data, demand=demand, diversity=diversity, co_weight=co_weight, alpha=alpha)
        # One number to sort by: NDCG averaged across the three populations, so a
        # setting cannot win by serving organizers at visitors' expense.
        score = np.mean([result["organizers"]["ndcg@10"], result["organizers_new"]["ndcg@10"],
                         result["visitors"]["ndcg@10"]])
        rows.append((score, demand, diversity, co_weight, alpha, result))

    rows.sort(key=lambda r: -r[0])
    print("| mean ndcg | demand | lambda | co weight | alpha | org ndcg | org-new ndcg | visitor ndcg | org genres |")
    print("|---|---|---|---|---|---|---|---|---|")
    for score, demand, diversity, co_weight, alpha, r in rows[:12] + rows[-3:]:
        print(f"| {score:.3f} | {demand} | {diversity} | {co_weight} | {alpha} | "
              f"{r['organizers']['ndcg@10']} | {r['organizers_new']['ndcg@10']} | "
              f"{r['visitors']['ndcg@10']} | {r['organizers']['genres_per_list']} |")


if __name__ == "__main__":
    main()
