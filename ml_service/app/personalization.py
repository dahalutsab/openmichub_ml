"""Personalisation: what one person's own history says about the ranking.

Everything else in this service ranks a *request*. Genre, budget, city, the
words in the box — all of it describes the event being planned, and two people
planning the same event get the same list. That is the right default and it
throws away most of what the platform knows: someone who has booked three jazz
trios, opened nine jazz profiles and never once looked at a DJ has said what
they like far more clearly than any single search box can.

What is read, and what each part is worth:

  bookings        `booking`, already recorded. The strongest signal there is —
                  money changed hands — and the sparsest.
  profile views   `user_interaction`, written by the API when a signed-in
                  visitor opens a profile. The commonest signal, weak alone.
  searches        `user_interaction`, with the text. What someone is looking
                  for *now*, which a months-old booking cannot say.
  browse filters  `user_interaction`. A stated genre or budget with no words.

Three principles hold the rest of this module together.

**Recency decays, it does not cut off.** An event's weight halves every
`taste_half_life_days`. A cut-off would make the ranking jump on the day an
event aged out of the window; a decay moves it a little every day.

**A thin history is no history.** Below `taste_min_signal` of total decayed
weight, nothing is personalised at all and the caller gets the ranking it would
have got before any of this existed. A taste profile built from two clicks is a
guess wearing the costume of data.

**Personalisation adjusts, it does not decide.** The trained ranker still
produces the ordering; this shifts it by at most `taste_alpha_*` of the final
score, and less when the person has just typed what they want. Somebody who
searches "dj for a club night" gets DJs, however much jazz they have booked.

Anonymous visitors have no history here — the API records nothing for them —
and are served exactly as they were before.
"""

from __future__ import annotations

import logging
import math
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime

import numpy as np

from app.config import get_settings
from app.db import connection
from app.embedder import embed
from app.features import price_fit
from app.repository import fetch_artists

log = logging.getLogger(__name__)


# What each sort of event is worth before recency is applied.
#
# The ordering is the point rather than the exact numbers: paying an artist says
# more than opening their page, and opening their page says more than a search
# that may have been abandoned. A confirmed booking is the anchor at 1.0 and
# everything else is priced against it.
BOOKING_WEIGHT = {
    "CONFIRMED": 1.0,
    "COMPLETED": 1.0,
    # Requested and not yet answered. The choice was still made by this person.
    "PENDING": 0.6,
    # The artist said no, so this says nothing about them and everything about
    # who this organizer wanted.
    "DECLINED": 0.45,
    "CANCELLED": 0.3,
    "NO_SHOW": 0.2,
}

INTERACTION_WEIGHT = {
    "PROFILE_VIEW": 0.45,
    "SEARCH": 0.35,
    "BROWSE": 0.25,
}

# How the affinity score divides up. These sum to 1, so affinity is on the same
# 0-1 scale as the normalised model score it is blended with.
W_TASTE_SIMILARITY = 0.35   # this artist against the profile as a whole
W_GENRE = 0.25              # genres this person keeps returning to
W_FAMILIARITY = 0.18        # they have booked or read this artist before
W_BUDGET = 0.12             # what they usually pay
W_CITY = 0.10               # where they usually book

# Search texts embedded per profile. Recent and distinct; the tail of a long
# history is already faint after decay and is not worth an encoder pass.
MAX_QUERY_TEXTS = 6


def _decay(age_days: float) -> float:
    """Half weight every `taste_half_life_days`, and never quite zero."""
    half_life = max(get_settings().taste_half_life_days, 1e-6)
    return float(0.5 ** (max(age_days, 0.0) / half_life))


def _age_days(when: datetime | None, now: datetime) -> float:
    if when is None:
        # No timestamp: treat it as old rather than as new, so a row with a
        # missing date cannot outrank one that is genuinely recent.
        return float(get_settings().taste_window_days)
    return max((now - when).total_seconds() / 86400.0, 0.0)


@dataclass
class TasteProfile:
    """One person's history, in the shapes the ranking needs.

    Built from a bounded window and cached briefly; see `profile_for`.
    """

    user_id: int

    #: Unit-length preference vector: the weighted centre of the artists this
    #: person engaged with and the searches they typed. None when there were no
    #: embeddings to build it from.
    vector: np.ndarray | None = None

    #: Genre and city names to a 0-1 share of this person's attention, scaled so
    #: the strongest is 1.0. Relative, deliberately: what matters is which genre
    #: they favour, not how many events they generated.
    genre_affinity: dict[str, float] = field(default_factory=dict)
    city_affinity: dict[str, float] = field(default_factory=dict)

    #: What they typically pay per hour, from booked artists' rates and stated
    #: budgets. None when they have never indicated one.
    typical_rate: float | None = None

    #: Artists they have booked, and artists they have read, to decayed weight.
    booked: dict[int, float] = field(default_factory=dict)
    viewed: dict[int, float] = field(default_factory=dict)

    #: Total decayed weight behind all of it, and how many rows it came from.
    signal: float = 0.0
    events: int = 0

    @property
    def usable(self) -> bool:
        """Whether there is enough history to personalise from at all."""
        return self.signal >= get_settings().taste_min_signal and (
            self.vector is not None or bool(self.genre_affinity)
        )

    def describe(self) -> dict:
        """A readable summary, for the API's introspection endpoint."""
        top = lambda mapping: dict(  # noqa: E731 - a local shorthand, used twice
            sorted(mapping.items(), key=lambda kv: -kv[1])[:5])
        return {
            "userId": self.user_id,
            "usable": self.usable,
            "signal": round(self.signal, 3),
            "events": self.events,
            "topGenres": {k: round(v, 3) for k, v in top(self.genre_affinity).items()},
            "topCities": {k: round(v, 3) for k, v in top(self.city_affinity).items()},
            "typicalRate": self.typical_rate,
            "bookedArtists": len(self.booked),
            "viewedArtists": len(self.viewed),
            "hasVector": self.vector is not None,
        }


# --------------------------------------------------------------------------- #
# Reading the history
# --------------------------------------------------------------------------- #

_INTERACTIONS_SQL = """
SELECT kind, artist_id, search_query, genre, city, occasion, budget_per_hour, created_date
FROM user_interaction
WHERE user_id = %(user_id)s
  AND created_date > NOW() - (%(days)s * INTERVAL '1 day')
ORDER BY created_date DESC
LIMIT %(limit)s
"""

_BOOKINGS_SQL = """
SELECT b.artist_id,
       b.status,
       b.event_type,
       a.hourly_rate,
       COALESCE(b.created_date, b.event_date::timestamp) AS happened_at
FROM booking b
JOIN artists a ON a.id = b.artist_id
WHERE b.user_id = %(user_id)s
  AND COALESCE(b.created_date, b.event_date::timestamp) > NOW() - (%(days)s * INTERVAL '1 day')
ORDER BY happened_at DESC
LIMIT %(limit)s
"""

_EMBEDDING_SQL = """
SELECT artist_id, COALESCE(profile_embedding, embedding)
FROM {schema}.artist_embedding
WHERE artist_id = ANY(%(ids)s)
"""


def _rows(sql: str, params: dict) -> list[tuple]:
    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        return cur.fetchall()


def _embeddings(artist_ids: list[int]) -> dict[int, np.ndarray]:
    """Profile vectors for the given artists, keyed by id.

    Falls back to the search vector where an artist has no profile vector yet,
    which is the case on an install that has not been re-embedded since the
    second column was added.
    """
    if not artist_ids:
        return {}
    sql = _EMBEDDING_SQL.format(schema=get_settings().db_schema)
    return {int(row[0]): np.asarray(row[1], dtype=np.float32)
            for row in _rows(sql, {"ids": artist_ids}) if row[1] is not None}


def _normalise_shares(weights: dict[str, float]) -> dict[str, float]:
    """Scales a weight map so its strongest entry is 1.0."""
    if not weights:
        return {}
    strongest = max(weights.values())
    if strongest <= 0:
        return {}
    return {name: value / strongest for name, value in weights.items()}


def build_profile(user_id: int) -> TasteProfile:
    """Reads one person's history and turns it into a taste profile.

    Bounded on both sides — a time window and a row cap — so this stays a small
    indexed read however long someone has been on the platform.
    """
    settings = get_settings()
    profile = TasteProfile(user_id=user_id)
    params = {"user_id": user_id,
              "days": settings.taste_window_days,
              "limit": settings.taste_max_events}

    try:
        interactions = _rows(_INTERACTIONS_SQL, params)
        bookings = _rows(_BOOKINGS_SQL, params)
    except Exception:
        # A missing table on an install that has not migrated yet, or a database
        # blip. Discovery still works; it is simply not personalised.
        log.exception("Could not read the history for user %s", user_id)
        return profile

    if not interactions and not bookings:
        return profile

    now = datetime.now()
    artist_ids = {int(row[1]) for row in interactions if row[1] is not None}
    artist_ids |= {int(row[0]) for row in bookings if row[0] is not None}
    artists = {a["artist_id"]: a for a in fetch_artists(list(artist_ids))} if artist_ids else {}

    genre_weight: dict[str, float] = {}
    city_weight: dict[str, float] = {}
    rate_samples: list[tuple[float, float]] = []   # (rate, weight)
    query_texts: dict[str, float] = {}

    def credit(mapping: dict[str, float], name: str | None, weight: float) -> None:
        if name and (cleaned := name.strip()):
            mapping[cleaned] = mapping.get(cleaned, 0.0) + weight

    def credit_artist(artist_id: int, weight: float) -> None:
        """Spreads an artist-level event across the things that artist is."""
        artist = artists.get(artist_id)
        if not artist:
            return
        for genre in (artist.get("sub_genres") or []) + (artist.get("parent_genres") or []):
            credit(genre_weight, genre, weight)
        credit(city_weight, artist.get("city"), weight)
        rate_samples.append((float(artist["hourly_rate"]), weight))

    for kind, artist_id, query, genre, city, _occasion, budget, created in interactions:
        weight = INTERACTION_WEIGHT.get(kind, 0.2) * _decay(_age_days(created, now))
        if weight <= 0:
            continue
        profile.signal += weight
        profile.events += 1

        if artist_id is not None:
            artist_id = int(artist_id)
            profile.viewed[artist_id] = profile.viewed.get(artist_id, 0.0) + weight
            credit_artist(artist_id, weight)

        # What they typed or filtered on, which is a preference even when it
        # matched nothing.
        credit(genre_weight, genre, weight)
        credit(city_weight, city, weight)
        if budget:
            rate_samples.append((float(budget), weight))
        if query and (text := query.strip()):
            query_texts[text] = max(query_texts.get(text, 0.0), weight)

    for artist_id, status, _event_type, hourly_rate, happened_at in bookings:
        if artist_id is None:
            continue
        weight = BOOKING_WEIGHT.get(status, 0.4) * _decay(_age_days(happened_at, now))
        if weight <= 0:
            continue
        artist_id = int(artist_id)
        profile.signal += weight
        profile.events += 1
        profile.booked[artist_id] = profile.booked.get(artist_id, 0.0) + weight
        credit_artist(artist_id, weight)
        # The rate actually paid, which is worth more than a stated budget.
        if hourly_rate:
            rate_samples.append((float(hourly_rate), weight))

    profile.genre_affinity = _normalise_shares(genre_weight)
    profile.city_affinity = _normalise_shares(city_weight)

    if rate_samples:
        total = sum(weight for _, weight in rate_samples)
        if total > 0:
            profile.typical_rate = sum(rate * weight for rate, weight in rate_samples) / total

    profile.vector = _preference_vector(profile, query_texts)
    return profile


def _preference_vector(profile: TasteProfile, query_texts: dict[str, float]) -> np.ndarray | None:
    """The weighted centre of what this person engaged with.

    Artists they booked or read are represented by their profile vectors — the
    ones with the stage name left out, because this is about what an act sounds
    like rather than what it is called. Searches are represented by the text
    itself, which is the only record of an intent that never reached a profile.

    The result is re-normalised to unit length so a cosine against it means the
    same thing whether it was built from two events or two hundred.
    """
    contributions: list[tuple[np.ndarray, float]] = []

    engaged = dict(profile.viewed)
    for artist_id, weight in profile.booked.items():
        engaged[artist_id] = engaged.get(artist_id, 0.0) + weight

    if engaged:
        vectors = _embeddings(list(engaged))
        contributions.extend((vector, engaged[artist_id])
                             for artist_id, vector in vectors.items())

    if query_texts:
        recent = sorted(query_texts.items(), key=lambda kv: -kv[1])[:MAX_QUERY_TEXTS]
        try:
            encoded = embed([text for text, _ in recent])
            contributions.extend((encoded[i], weight)
                                 for i, (_, weight) in enumerate(recent))
        except Exception:
            # The encoder is optional here: the artist vectors alone still make
            # a usable profile.
            log.exception("Could not embed the search history for user %s", profile.user_id)

    if not contributions:
        return None

    stacked = np.vstack([vector for vector, _ in contributions])
    weights = np.array([weight for _, weight in contributions], dtype=np.float32)
    centre = (stacked * weights[:, None]).sum(axis=0)
    norm = float(np.linalg.norm(centre))
    return None if norm < 1e-9 else (centre / norm).astype(np.float32)


# --------------------------------------------------------------------------- #
# Cache
# --------------------------------------------------------------------------- #

_cache: dict[int, tuple[float, TasteProfile]] = {}
_cache_lock = threading.Lock()
_CACHE_MAX_USERS = 512


def profile_for(user_id: int | None) -> TasteProfile | None:
    """The cached taste profile for a user, rebuilt when it has expired.

    Returns None for an anonymous caller and for anyone whose history is too
    thin to personalise from, so a caller can treat "no profile" as "rank the
    ordinary way" without inspecting anything.
    """
    if not user_id:
        return None

    ttl = get_settings().taste_cache_ttl_seconds
    now = time.monotonic()

    with _cache_lock:
        cached = _cache.get(user_id)
        if cached and now - cached[0] < ttl:
            return cached[1] if cached[1].usable else None

    profile = build_profile(user_id)

    with _cache_lock:
        if len(_cache) >= _CACHE_MAX_USERS:
            # Cheapest useful eviction: drop the oldest half rather than track
            # access order for what is a short-lived cache anyway.
            for stale in sorted(_cache, key=lambda uid: _cache[uid][0])[: _CACHE_MAX_USERS // 2]:
                _cache.pop(stale, None)
        _cache[user_id] = (now, profile)

    return profile if profile.usable else None


def forget(user_id: int | None = None) -> None:
    """Drops cached profiles. Used by the tests and after a bulk import."""
    with _cache_lock:
        if user_id is None:
            _cache.clear()
        else:
            _cache.pop(user_id, None)


# --------------------------------------------------------------------------- #
# Scoring
# --------------------------------------------------------------------------- #

def _taste_similarity(profile: TasteProfile, artist_id: int,
                      vectors: dict[int, np.ndarray]) -> float | None:
    """Cosine between the preference vector and this artist, on a 0-1 scale."""
    if profile.vector is None:
        return None
    vector = vectors.get(artist_id)
    if vector is None:
        return None
    cosine = float(np.dot(profile.vector, vector))
    settings = get_settings()
    span = settings.taste_cos_high - settings.taste_cos_low
    if span <= 1e-9:
        return 0.5
    return float(min(1.0, max(0.0, (cosine - settings.taste_cos_low) / span)))


def affinity(profile: TasteProfile, artist: dict,
             vectors: dict[int, np.ndarray]) -> tuple[float, list[str]]:
    """How well one artist fits this person, and why, on a 0-1 scale.

    The reasons are not a post-hoc story: each one is emitted by the component
    that actually contributed, and only when that component was strong enough to
    have moved the score.
    """
    artist_id = int(artist["artist_id"])
    reasons: list[str] = []
    parts: list[tuple[float, float]] = []   # (weight, value)

    similarity = _taste_similarity(profile, artist_id, vectors)
    if similarity is not None:
        parts.append((W_TASTE_SIMILARITY, similarity))
        if similarity >= 0.6:
            reasons.append("Close to the acts you have been looking at")

    genres = [g for g in (artist.get("sub_genres") or []) + (artist.get("parent_genres") or []) if g]
    best_genre, genre_score = None, 0.0
    for genre in genres:
        share = profile.genre_affinity.get(genre, 0.0)
        if share > genre_score:
            best_genre, genre_score = genre, share
    if profile.genre_affinity:
        parts.append((W_GENRE, genre_score))
        if best_genre and genre_score >= 0.5:
            reasons.append(f"You keep coming back to {best_genre}")

    booked = profile.booked.get(artist_id, 0.0)
    viewed = profile.viewed.get(artist_id, 0.0)
    if booked > 0:
        familiarity = 1.0
        reasons.append("You have booked them before")
    elif viewed > 0:
        # Two visits mean more than one, and ten mean little more than three.
        familiarity = min(1.0, 0.45 + viewed)
        reasons.append("You looked at their profile")
    else:
        familiarity = 0.0
    parts.append((W_FAMILIARITY, familiarity))

    if profile.typical_rate:
        # The same curve the ranker uses for a stated budget, with what this
        # person usually pays standing in for one.
        fit = price_fit(profile.typical_rate, float(artist["hourly_rate"]))
        parts.append((W_BUDGET, fit))
        if fit >= 0.75:
            reasons.append("Around what you usually pay")

    city = (artist.get("city") or "").strip()
    if profile.city_affinity:
        city_score = profile.city_affinity.get(city, 0.0)
        parts.append((W_CITY, city_score))
        if city and city_score >= 0.6:
            reasons.append(f"In {city}, where you usually book")

    # Renormalised over the components that could be evaluated, so an artist is
    # not penalised for a signal this person has never given.
    total_weight = sum(weight for weight, _ in parts)
    if total_weight <= 0:
        return 0.0, []
    score = sum(weight * value for weight, value in parts) / total_weight
    return float(score), reasons[:2]


def _to_unit_scale(scores: np.ndarray) -> np.ndarray:
    """Maps ranking scores onto 0-1 so they can be blended with an affinity.

    A logistic on the standardised score rather than a min-max: min-max would
    make the top candidate exactly 1.0 and the last exactly 0.0 on every single
    request, which throws away how far apart they actually were and lets one
    outlier compress everything else into a corner. This keeps the gaps, and
    keeps them comparable between a tight list and a spread one.
    """
    if scores.size == 0:
        return scores
    spread = float(scores.std())
    if spread < 1e-9:
        return np.full_like(scores, 0.5, dtype=np.float64)
    z = (scores.astype(np.float64) - float(scores.mean())) / spread
    return 1.0 / (1.0 + np.exp(-z))


def rerank(profile: TasteProfile, candidates: list[dict], scores: np.ndarray,
           alpha: float) -> tuple[np.ndarray, list[list[str]]]:
    """Blends the model's ordering with this person's affinity.

    `alpha` is personalisation's share of the final score. The model still does
    the ranking; this moves candidates within it.
    """
    if not candidates:
        return scores, []

    vectors = _embeddings([int(c["artist_id"]) for c in candidates]) if profile.vector is not None else {}
    scored = [affinity(profile, candidate, vectors) for candidate in candidates]
    affinities = np.array([value for value, _ in scored], dtype=np.float64)
    reasons = [why for _, why in scored]

    blended = (1.0 - alpha) * _to_unit_scale(np.asarray(scores)) + alpha * affinities
    return blended, reasons


def retrieval_vector(profile: TasteProfile, query_vector: np.ndarray | None) -> np.ndarray | None:
    """The vector to retrieve candidates with on a browse surface.

    Re-ranking can only reorder what retrieval found, so on a surface with no
    words — where the alternative is a generic pool — the taste profile is
    allowed into retrieval itself. Stated filters keep the majority share when
    there are any: they are about this event, and the profile is about the
    person.
    """
    if profile.vector is None:
        return query_vector
    if query_vector is None:
        return profile.vector

    share = get_settings().taste_retrieval_share
    blended = (1.0 - share) * np.asarray(query_vector, dtype=np.float32) + share * profile.vector
    norm = float(np.linalg.norm(blended))
    return query_vector if norm < 1e-9 else (blended / norm).astype(np.float32)
