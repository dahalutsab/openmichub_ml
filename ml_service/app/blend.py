"""The last stage of ranking: from scores to the order a person actually sees.

By the time a list gets here it has been retrieved, scored by the trained model
and, for someone with a history, blended with their affinity. Four things are
left, and none of them is about the request:

  demand       who organizers are booking and opening this month
  exploration  a small, bounded chance for acts that have barely been shown
  skips        acts this person keeps being shown near the top and never opens
  variety      not filling the first screen with eight near-identical acts

Each is a stated share of the final score, set per surface in `config.py`. The
front page, where nothing distinguishes one act from another for the request,
leans on demand hardest; a typed search barely at all.
"""

from __future__ import annotations

import hashlib
from datetime import date

import numpy as np

from app.config import get_settings
from app.signals import Signals

SURFACE_HOME = "home"
SURFACE_BROWSE = "browse"
SURFACE_SEARCH = "search"

# Demand strong enough to say so on the card, for anyone.
DEMAND_REASON_AT = 0.8

# The widest a per-visitor, per-day nudge can move a score. Enough to reorder
# acts the model genuinely could not separate, so two visitors - or one visitor
# on two days - do not see an identical front page; far too small to lift a weak
# act over a strong one.
HOME_JITTER = 0.02


def to_unit_scale(scores: np.ndarray) -> np.ndarray:
    """A logistic on the standardised score, as `personalization` blends with."""
    from app.personalization import _to_unit_scale

    return _to_unit_scale(np.asarray(scores, dtype=np.float64))


def _jitter(seed: str, artist_id: int) -> float:
    digest = hashlib.blake2b(f"{seed}:{artist_id}".encode(), digest_size=8).digest()
    return int.from_bytes(digest, "big") / 2**64


def platform_blend(candidates: list[dict], scores: np.ndarray, surface: str,
                   signals: Signals, skips: np.ndarray | None = None,
                   reasons: list[list[str]] | None = None,
                   viewer: str | None = None,
                   today: date | None = None) -> tuple[np.ndarray, list[list[str]]]:
    """Folds demand, exploration and skips into already-blended 0-1 scores.

    `scores` must already be on a 0-1 scale. Returns new scores and the reasons
    list, extended with a demand line where one applies and there is room.
    """
    settings = get_settings()
    count = len(candidates)
    reasons = [list(r) for r in reasons] if reasons else [[] for _ in range(count)]
    if count == 0:
        return np.asarray(scores, dtype=np.float64), reasons

    ids = [int(c["artist_id"]) for c in candidates]
    demand = np.array([signals.demand.get(i, 0.0) for i in ids], dtype=np.float64)

    w_demand = {SURFACE_HOME: settings.demand_weight_home,
                SURFACE_BROWSE: settings.demand_weight_browse,
                SURFACE_SEARCH: settings.demand_weight_search}[surface]
    # Exploration only where nobody asked for anything in particular. On a search,
    # an act that has rarely been shown has usually rarely been shown for a reason.
    w_explore = 0.0 if surface == SURFACE_SEARCH else settings.exploration_weight

    if signals.exposure:
        half = settings.exploration_half_exposure
        exposure = np.array([signals.exposure.get(i, 0) for i in ids], dtype=np.float64)
        explore = half / (half + exposure)
    else:
        # Nothing has been shown to anyone yet: every act is equally unexplored,
        # and a bonus everyone gets is no bonus. Its share goes back to the score.
        explore, w_explore = np.zeros(count), 0.0

    base = np.asarray(scores, dtype=np.float64)
    final = (1.0 - w_demand - w_explore) * base + w_demand * demand + w_explore * explore

    if skips is not None and skips.size == count:
        final = final - settings.skip_penalty * skips

    if surface == SURFACE_HOME and viewer:
        seed = f"{viewer}:{(today or date.today()).isoformat()}"
        final = final + HOME_JITTER * (np.array([_jitter(seed, i) for i in ids]) - 0.5)

    for index, value in enumerate(demand):
        if value >= DEMAND_REASON_AT and len(reasons[index]) < 2:
            reasons[index].append("In demand this month")

    return final, reasons


def limit_familiar(order: list[int], candidates: list[dict], familiar: set[int],
                   limit: int) -> list[int]:
    """Keeps acts this person already booked or opened to a share of the first screen.

    Personalisation rightly scores a known act highly - familiarity is real
    evidence, and rebooking is common - but a front page made entirely of acts
    someone has already found is a page that finds them nothing. Past the share,
    known acts move down behind the next unfamiliar ones; they are not removed.
    """
    share = get_settings().familiar_share_first_screen
    if not familiar or share >= 1.0:
        return order
    cap = max(1, int(round(limit * share)))
    head: list[int] = []
    deferred: list[int] = []
    known = 0
    for position in order:
        if len(head) >= limit:
            break
        if int(candidates[position]["artist_id"]) in familiar:
            if known >= cap:
                deferred.append(position)
                continue
            known += 1
        head.append(position)
    placed = set(head) | set(deferred)
    return head + deferred + [p for p in order if p not in placed]


def diversify(candidates: list[dict], scores: np.ndarray,
              vectors: dict[int, np.ndarray], limit: int,
              band: tuple[float, float]) -> list[int]:
    """An order over `candidates` that trades a little score for variety.

    Maximal marginal relevance: each next pick maximises
        lambda * score - (1 - lambda) * (closest likeness to anything already picked)
    over the strongest few candidates, with likeness the profile-vector cosine
    mapped through the same band taste similarity uses. Returns indices: the
    diversified top `limit`, then everything else by score.

    An artist with no vector is never penalised for likeness it cannot be
    measured on.
    """
    order = list(np.argsort(-np.asarray(scores, dtype=np.float64), kind="stable"))
    lam = get_settings().diversity_lambda
    if lam >= 1.0 or len(order) <= 2 or not vectors:
        return order

    low, high = band
    span = max(high - low, 1e-9)
    pool = order[: max(limit * 3, limit)]
    rest = order[len(pool):]

    picked: list[int] = []
    picked_vectors: list[np.ndarray] = []
    remaining = list(pool)
    while remaining and len(picked) < limit:
        best_index, best_value = None, -np.inf
        for position in remaining:
            vector = vectors.get(int(candidates[position]["artist_id"]))
            likeness = 0.0
            if vector is not None and picked_vectors:
                cosine = max(float(np.dot(vector, other)) for other in picked_vectors)
                likeness = min(1.0, max(0.0, (cosine - low) / span))
            value = lam * float(scores[position]) - (1.0 - lam) * likeness
            if value > best_value:
                best_index, best_value = position, value
        picked.append(best_index)
        remaining.remove(best_index)
        vector = vectors.get(int(candidates[best_index]["artist_id"]))
        if vector is not None:
            picked_vectors.append(vector)

    leftover = sorted(remaining, key=lambda p: -float(scores[p]))
    return picked + leftover + rest
