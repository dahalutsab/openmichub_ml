"""What the whole platform's behaviour says, as opposed to one person's.

`personalization.py` reads one person's history. This reads everyone's, and
turns it into three things no single history can provide:

  co-choice     artists the same people chose together. Item-to-item
                collaborative filtering over bookings and profile views: two
                acts are close when the people who booked or opened one also
                booked or opened the other. It sees what a profile's text never
                says - the circuit a wedding planner actually hires from, the
                act everyone books after the headliner - and it is the part of a
                "because you watched" recommender that content similarity
                cannot imitate.

  demand        who is being booked and opened *now*. Decays in weeks, so it
                answers "what is in demand this month" rather than "who has the
                most bookings ever", which would freeze the front page on the
                same veterans for good.

  exposure      how often each artist has been shown near the top of a list
                lately. Used to give an act that has barely been seen a small,
                bounded chance against acts that are shown on every visit, which
                is the only way a newcomer ever collects the evidence it needs.

Rebuilt from the database at most every `signals_ttl_seconds`, in one pass over
a bounded window. The builder is a pure function of the rows, so the offline
evaluation replays it against history cut at any date.

Nothing here is fitted by gradient descent. Co-choice is a similarity computed
from counts, and the rest are decayed sums. What each counts for is stated below
and in `config.py`.
"""

from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime

import numpy as np
from scipy import sparse

from app.config import get_settings
from app.db import connection

log = logging.getLogger(__name__)


# What one event says about an artist, before recency. Priced against a
# confirmed booking, as in `personalization.py`, but not the same table: that one
# is about one person's taste, and this is about which artists belong together.
# A declined request still says the organizer wanted *this* act alongside the
# others they chose, so it counts nearly in full here.
CO_BOOKING_WEIGHT = {
    "COMPLETED": 1.0,
    "CONFIRMED": 1.0,
    "PENDING": 0.8,
    "DECLINED": 0.7,
    "CANCELLED": 0.5,
    "NO_SHOW": 0.3,
}
CO_VIEW_WEIGHT = 0.3

# A co-occurrence seen once is a coincidence. Similarities are shrunk towards
# zero by support / (support + CO_SHRINKAGE), so a pair chosen together by two
# people counts for less than one chosen together by twenty, whatever the cosine.
CO_SHRINKAGE = 4.0
CO_MIN_SUPPORT = 2

# Neighbours kept per artist. Enough to fill a strip and to score a candidate
# pool; storing the full matrix would be quadratic in the catalogue for nothing.
CO_NEIGHBOURS = 40

# Demand: what a booking request counts for against a profile view.
DEMAND_BOOKING = 1.0
DEMAND_VIEW = 0.25
DEMAND_CLICK = 0.25


@dataclass
class Signals:
    """Platform-wide signals at one moment."""

    #: artist -> [(other artist, similarity)], strongest first.
    neighbours: dict[int, list[tuple[int, float]]] = field(default_factory=dict)

    #: A typical strong co-choice similarity, used to put a person's co-choice
    #: score on 0-1 with a fixed scale rather than a per-request min-max.
    co_scale: float = 0.0

    #: artist -> 0-1, the busiest artist this month at 1.0.
    demand: dict[int, float] = field(default_factory=dict)

    #: artist -> how many lists showed them near the top in the exposure window.
    exposure: dict[int, int] = field(default_factory=dict)

    #: How many people the co-choice matrix was built from.
    actors: int = 0

    def similar(self, artist_id: int, limit: int) -> list[tuple[int, float]]:
        return self.neighbours.get(int(artist_id), [])[:limit]

    def co_choice(self, seeds: dict[int, float]) -> dict[int, tuple[float, int]]:
        """Scores every neighbour of someone's chosen artists.

        `seeds` is artist -> how strongly this person engaged with them. The
        score for a candidate is the weighted mean of its similarity to each
        seed, so an artist close to many of the things someone chose beats one
        close to a single thing - and a person whose choices are scattered
        across the catalogue gets weak scores everywhere, which is the honest
        answer for them. Returned with the seed that contributed most, so the
        reason on the card can name it.
        """
        total = sum(weight for weight in seeds.values() if weight > 0)
        if total <= 0 or not self.neighbours:
            return {}

        scores: dict[int, float] = {}
        best: dict[int, tuple[float, int]] = {}
        for seed, weight in seeds.items():
            if weight <= 0:
                continue
            for other, similarity in self.neighbours.get(int(seed), []):
                contribution = weight * similarity
                scores[other] = scores.get(other, 0.0) + contribution
                if contribution > best.get(other, (0.0, 0))[0]:
                    best[other] = (contribution, int(seed))

        return {artist: (score / total, best[artist][1]) for artist, score in scores.items()}

    def describe(self) -> dict:
        linked = sum(1 for items in self.neighbours.values() if items)
        top_demand = sorted(self.demand.items(), key=lambda kv: -kv[1])[:5]
        return {
            "actors": self.actors,
            "artistsWithNeighbours": linked,
            "coScale": round(self.co_scale, 4),
            "artistsInDemand": sum(1 for value in self.demand.values() if value > 0),
            "topDemand": {str(k): round(v, 3) for k, v in top_demand},
            "artistsShown": len(self.exposure),
        }


# --------------------------------------------------------------------------- #
# Building
# --------------------------------------------------------------------------- #

def _decay(age_days: float, half_life_days: float) -> float:
    return float(0.5 ** (max(age_days, 0.0) / max(half_life_days, 1e-6)))


def _age(when: datetime | None, now: datetime) -> float | None:
    if when is None:
        return None
    return (now - when).total_seconds() / 86400.0


def build_signals(bookings: list[tuple], views: list[tuple],
                  exposure_rows: list[tuple], clicks: list[tuple],
                  now: datetime) -> Signals:
    """Signals from raw rows, as they stood at `now`.

    bookings       (actor, artist_id, status, requested_at)
    views          (actor, artist_id, viewed_at)      profile views
    exposure_rows  (artist_id, times shown)           already windowed
    clicks         (artist_id, clicked_at)            only clicks on served lists

    Rows dated after `now` are ignored, so the same function replays history.
    `actor` is any hashable key for a person - an account or a browser.
    """
    settings = get_settings()
    signals = Signals()

    # ---- co-choice ---------------------------------------------------------
    cells: dict[tuple, float] = {}
    demand: dict[int, float] = {}

    for actor, artist_id, status, when in bookings:
        age = _age(when, now)
        if artist_id is None or actor is None or age is None or age < 0:
            continue
        artist_id = int(artist_id)
        key = (actor, artist_id)
        cells[key] = cells.get(key, 0.0) + (
            CO_BOOKING_WEIGHT.get(status, 0.5) * _decay(age, settings.co_half_life_days))
        if age <= settings.demand_window_days:
            demand[artist_id] = demand.get(artist_id, 0.0) + (
                DEMAND_BOOKING * _decay(age, settings.demand_half_life_days))

    for actor, artist_id, when in views:
        age = _age(when, now)
        if artist_id is None or actor is None or age is None or age < 0:
            continue
        artist_id = int(artist_id)
        key = (actor, artist_id)
        cells[key] = cells.get(key, 0.0) + CO_VIEW_WEIGHT * _decay(age, settings.co_view_half_life_days)
        if age <= settings.demand_window_days:
            demand[artist_id] = demand.get(artist_id, 0.0) + (
                DEMAND_VIEW * _decay(age, settings.demand_half_life_days))

    for artist_id, when in clicks:
        age = _age(when, now)
        if artist_id is None or age is None or age < 0 or age > settings.demand_window_days:
            continue
        demand[int(artist_id)] = demand.get(int(artist_id), 0.0) + (
            DEMAND_CLICK * _decay(age, settings.demand_half_life_days))

    signals.neighbours, signals.co_scale, signals.actors = _co_choice(cells)

    # ---- demand ------------------------------------------------------------
    if demand:
        # Log-scaled, so the busiest act does not flatten everyone else to zero,
        # and relative to the busiest, so the number reads the same on a quiet
        # platform and a busy one.
        peak = max(demand.values())
        if peak > 0:
            signals.demand = {artist: float(np.log1p(value) / np.log1p(peak))
                              for artist, value in demand.items()}

    signals.exposure = {int(artist): int(count) for artist, count in exposure_rows
                        if artist is not None}
    return signals


def _co_choice(cells: dict[tuple, float]) -> tuple[dict[int, list[tuple[int, float]]], float, int]:
    """Item-item cosine over a people x artists matrix, shrunk by support.

    Sparse throughout: at a few hundred artists this is a millisecond, and the
    same code holds at tens of thousands because only co-occurring pairs are
    ever materialised.
    """
    if not cells:
        return {}, 0.0, 0

    actors = {actor: index for index, actor in enumerate({actor for actor, _ in cells})}
    artists = sorted({artist for _, artist in cells})
    column = {artist: index for index, artist in enumerate(artists)}

    rows = np.fromiter((actors[actor] for actor, _ in cells), dtype=np.int32, count=len(cells))
    cols = np.fromiter((column[artist] for _, artist in cells), dtype=np.int32, count=len(cells))
    # Damped: booking the same act ten times is a stronger tie than once, but not
    # ten times stronger, and without this a single loyal organizer dominates
    # every similarity their favourite act takes part in.
    values = np.log1p(np.fromiter(cells.values(), dtype=np.float64, count=len(cells)))

    matrix = sparse.csr_matrix((values, (rows, cols)), shape=(len(actors), len(artists)))
    norms = np.sqrt(np.asarray(matrix.multiply(matrix).sum(axis=0)).ravel())
    norms[norms == 0] = 1.0
    normalised = matrix @ sparse.diags(1.0 / norms)

    cosine = (normalised.T @ normalised).tocsr()
    present = matrix.copy()
    present.data = np.ones_like(present.data)
    support = (present.T @ present).tocsr()

    # Same sparsity pattern for both products, so the data arrays line up once
    # sorted; multiply() handles it without relying on that.
    shrink = support.copy().astype(np.float64)
    shrink.data = np.where(shrink.data >= CO_MIN_SUPPORT,
                           shrink.data / (shrink.data + CO_SHRINKAGE), 0.0)
    similarity = cosine.multiply(shrink).tocsr()
    similarity.setdiag(0.0)
    similarity.eliminate_zeros()

    neighbours: dict[int, list[tuple[int, float]]] = {}
    tops: list[float] = []
    for index, artist in enumerate(artists):
        start, end = similarity.indptr[index], similarity.indptr[index + 1]
        if start == end:
            continue
        others = similarity.indices[start:end]
        scores = similarity.data[start:end]
        order = np.argsort(-scores)[:CO_NEIGHBOURS]
        neighbours[artist] = [(artists[others[i]], float(scores[i])) for i in order]
        tops.append(float(scores[order[0]]))

    # What a strong tie looks like on this platform: the median artist's closest
    # neighbour. Fixed per build, so a co-choice score of 0.8 means the same thing
    # for every person and every request until the next rebuild.
    scale = float(np.median(tops)) if tops else 0.0
    return neighbours, scale, len(actors)


# --------------------------------------------------------------------------- #
# Reading
# --------------------------------------------------------------------------- #

_BOOKINGS_SQL = """
SELECT 'u' || b.user_id::text,
       b.artist_id,
       b.status,
       COALESCE(b.created_date, b.event_date::timestamp)
FROM booking b
WHERE b.user_id IS NOT NULL
  AND COALESCE(b.created_date, b.event_date::timestamp) > NOW() - (%(days)s * INTERVAL '1 day')
"""

_VIEWS_SQL = """
SELECT COALESCE('u' || user_id::text, 'v' || visitor_id),
       artist_id,
       created_date
FROM user_interaction
WHERE kind = 'PROFILE_VIEW'
  AND artist_id IS NOT NULL
  AND created_date > NOW() - (%(days)s * INTERVAL '1 day')
"""

# The top of each list only: an artist at position twenty was very likely never
# seen, and counting it as exposure would hand it no exploration bonus it had
# not earned.
_EXPOSURE_SQL = """
SELECT shown.artist_id, COUNT(*)
FROM discovery_impression d,
     UNNEST(d.artist_ids[1:%(top)s]) AS shown(artist_id)
WHERE d.created_date > NOW() - (%(days)s * INTERVAL '1 day')
GROUP BY shown.artist_id
"""

_CLICKS_SQL = """
SELECT i.artist_id, i.created_date
FROM user_interaction i
JOIN discovery_impression d ON d.request_id = i.request_id
WHERE i.kind = 'CLICK'
  AND i.created_date > NOW() - (%(days)s * INTERVAL '1 day')
"""


def _read(sql: str, params: dict) -> list[tuple]:
    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        return cur.fetchall()


def load_signals() -> Signals:
    """Builds signals from the database. Each part fails on its own."""
    settings = get_settings()
    parts: dict[str, list[tuple]] = {}
    for name, sql, params in (
        ("bookings", _BOOKINGS_SQL, {"days": settings.co_window_days}),
        ("views", _VIEWS_SQL, {"days": settings.co_window_days}),
        ("exposure", _EXPOSURE_SQL, {"days": settings.exposure_window_days,
                                     "top": settings.exposure_top_positions}),
        ("clicks", _CLICKS_SQL, {"days": settings.demand_window_days}),
    ):
        try:
            parts[name] = _read(sql, params)
        except Exception:
            # The impression table does not exist until the API has migrated;
            # co-choice and demand still work without it.
            log.warning("Could not read %s for platform signals", name, exc_info=True)
            parts[name] = []

    started = time.monotonic()
    signals = build_signals(parts["bookings"], parts["views"], parts["exposure"],
                            parts["clicks"], datetime.now())
    log.info("Platform signals rebuilt in %.0f ms: %d people, %d linked artists",
             (time.monotonic() - started) * 1000, signals.actors, len(signals.neighbours))
    return signals


_cached: tuple[float, Signals] | None = None
_lock = threading.Lock()
_refreshing = threading.Event()


def _rebuild() -> Signals:
    global _cached
    try:
        signals = load_signals()
    except Exception:
        log.exception("Platform signals unavailable; ranking without them")
        signals = _cached[1] if _cached is not None else Signals()
    _cached = (time.monotonic(), signals)
    return signals


def _rebuild_in_background() -> None:
    try:
        _rebuild()
    finally:
        _refreshing.clear()


def current() -> Signals:
    """The cached signals. Never raises, and never makes a request wait for a rebuild
    once one set exists: stale signals are served while a fresh set is built on
    another thread. Only the very first call builds inline, because there is
    nothing else to serve."""
    ttl = get_settings().signals_ttl_seconds
    cached = _cached
    if cached is not None:
        if time.monotonic() - cached[0] >= ttl and not _refreshing.is_set():
            _refreshing.set()
            threading.Thread(target=_rebuild_in_background, name="signals-refresh",
                             daemon=True).start()
        return cached[1]

    with _lock:
        if _cached is not None:
            return _cached[1]
        return _rebuild()


def reset() -> None:
    """Forgets the cached signals. Used by tests and after a reseed."""
    global _cached
    with _lock:
        _cached = None
