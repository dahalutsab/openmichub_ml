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

**Intent and taste are two different things, and are kept apart.** What someone
booked over the last six months is a durable preference; what they typed into
the search box ten minutes ago is what they are doing *right now*, and the two
must not be averaged into one number. An organizer with twenty-eight bookings
who searches for a DJ would otherwise see that search enter their profile at
about two percent and vanish - which is precisely the complaint that produced
this split. So there are two vectors: a taste vector from the artists they
engaged with, and an intent vector from their recent searches, with a much
shorter half-life. Retrieval draws on both, in a proportion that is stated
rather than emergent.

Four principles hold the rest of this module together.

**Recency decays, it does not cut off.** An event's weight halves every
`taste_half_life_days`. A cut-off would make the ranking jump on the day an
event aged out of the window; a decay moves it a little every day.

**A thin history is no history.** Below `taste_min_signal` of total decayed
weight, nothing is personalised at all and the caller gets the ranking it would
have got before any of this existed. A taste profile built from two clicks is a
guess wearing the costume of data.

One search is the exception, and deliberately so: typing a sentence is a
statement of what someone wants, where opening a profile is a glance. A single
search is enough to shape what comes back next; a single profile view is not.

**Retrieval draws from two pools rather than one blended vector.** A taste
vector is an average of profile vectors; an intent vector is an average of query
vectors, and query text sits systematically further from every profile than one
profile sits from another. Averaging the two into a single vector and sorting by
distance therefore hands the ranking to the taste side whatever share the code
claims to give intent — the stated 60/40 quietly became something nearer 95/5.
So each source retrieves against its own column, and the *share* is a share of
the candidate slots, which is a thing that can be honoured exactly.

**Personalisation adjusts, it does not decide.** The trained ranker still
produces the ordering; this shifts it by at most `taste_alpha_*` of the final
score, and less when the person has just typed what they want. Somebody who
searches "dj for a club night" gets DJs, however much jazz they have booked.

**A person is an account or a browser.** A visitor who has not signed in is
identified by the random id their browser keeps, and their searches and profile
views build a profile exactly as an account's do - without bookings, which need
an account. Signing in moves that history onto the account. A request carrying
neither is served the ordinary ranking.

**What people chose alongside the same acts counts too.** The platform-wide
co-choice table in `app/signals.py` is passed in by the caller, and becomes one
more component of affinity: an artist that people who booked what this person
booked also booked. It is what a profile's text cannot say.
"""

from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime

import numpy as np

from app.config import get_settings
from app.db import connection
from app.embedder import embed, embed_one
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

# What one search contributes to the *intent* vector, before its own faster
# decay. Unrelated to the weights above, which are about durable taste: this
# vector has nothing else in it, so the number only sets how fast repeated
# searches for the same thing accumulate against the cap below.
INTENT_WEIGHT = 1.0

# Searching the same thing five times says more than searching it once, but not
# five times more. Weight per distinct text stops here.
MAX_INTENT_PER_TEXT = 3.0

# How the affinity score divides up. The parts sum to 1, so affinity is on the
# same 0-1 scale as the normalised model score it is blended with. Components
# the person has given no signal for are dropped and the rest renormalised, so
# nobody is scored against a preference they have never expressed.
W_FAMILIARITY = 0.15        # they have booked or read this artist before
W_BUDGET = 0.10             # what they usually pay
W_CITY = 0.07               # where they usually book
W_CO_CHOICE = 0.18          # people who chose what they chose also chose this

# Co-choice worth naming on the card: at least this share of the platform's
# typical strong tie (see `Signals.co_scale`).
CO_CHOICE_REASON_AT = 0.6

# How close a search or a taste match has to be before the card claims it. Set
# above the typical genre match on each calibrated scale (0.775 for a query), not
# at the middle of it: at 0.6, after one search for a DJ, Jazz and Folk acts said
# "matches what you have been searching for", and on a personalised page every
# card carried the same two lines, which is the same as carrying none.
INTENT_REASON_AT = 0.85
TASTE_REASON_AT = 0.8

# Which line wins when several apply, most specific first. Naming an act this
# person booked or one they chose it alongside says something about *this* card;
# "close to your taste" could be said of most of a personalised list.
REASON_PRIORITY = {
    "booked": 0,
    "co_choice": 1,
    "viewed": 2,
    "intent": 3,
    "genre": 4,
    "taste": 5,
    "budget": 6,
    "city": 7,
}

# What is left over is the question "does this artist fit this person", and it
# is split between the two vectors by the same `taste_intent_share` that splits
# the candidate pool. Deriving it rather than writing two sets of numbers is the
# point: the first version had intent at 0.26 against a durable side of 0.42,
# because taste was counted twice — once as a vector and again as genre
# affinity — and a fresh search lost to a history it was supposed to outrank.
W_PERSONAL = 1.0 - (W_FAMILIARITY + W_BUDGET + W_CITY + W_CO_CHOICE)

# Within the durable half, how much is the vector rather than the genre names.
# Slightly under half: genre is the coarser signal and the more legible one, and
# it is what the reason line can actually name.
DURABLE_VECTOR_SPLIT = 0.45


def _affinity_weights() -> tuple[float, float, float]:
    """(intent, taste vector, genre), from the one share that governs both."""
    share = get_settings().taste_intent_share
    durable = W_PERSONAL * (1.0 - share)
    return (W_PERSONAL * share,
            durable * DURABLE_VECTOR_SPLIT,
            durable * (1.0 - DURABLE_VECTOR_SPLIT))

# Search texts embedded per profile. Recent and distinct; the tail of a long
# history is already faint after decay and is not worth an encoder pass.
MAX_QUERY_TEXTS = 6


def _decay(age_days: float, half_life_days: float | None = None) -> float:
    """Half weight every `half_life_days`, and never quite zero.

    Two half-lives are in use. Taste decays slowly, because what someone books
    says something durable about them. Intent decays fast, because a search is
    about the event being planned this week and is stale long before the taste
    is.
    """
    half_life = max(half_life_days or get_settings().taste_half_life_days, 1e-6)
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

    user_id: int | None = None

    #: The browser's id, for a visitor who has not signed in. Never set together
    #: with `user_id`.
    visitor_id: str | None = None

    #: Unit-length taste vector: the weighted centre of the artists this person
    #: booked or read. Durable. None when there were no embeddings to build it
    #: from - a person who has only ever searched has intent and no taste.
    vector: np.ndarray | None = None

    #: Unit-length intent vector, from recent search text alone and decayed on a
    #: much shorter half-life. What they are looking for now, kept out of the
    #: taste vector so a long booking history cannot drown a fresh search.
    intent_vector: np.ndarray | None = None

    #: Weight behind the intent vector, so a search noted mid-session can be
    #: folded into it at the right strength. See `note_search`.
    intent_signal: float = 0.0

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

    #: Stage names of the artists above, so a reason can name the act it came from.
    names: dict[int, str] = field(default_factory=dict)

    #: Artists shown near the top of this person's lists repeatedly lately and
    #: never opened or booked, to how many lists showed them.
    skipped: dict[int, int] = field(default_factory=dict)

    #: Total decayed weight behind all of it, and how many rows it came from.
    signal: float = 0.0
    events: int = 0

    def seeds(self) -> dict[int, float]:
        """Every artist this person engaged with, to how strongly - for co-choice."""
        engaged = dict(self.viewed)
        for artist_id, weight in self.booked.items():
            engaged[artist_id] = engaged.get(artist_id, 0.0) + weight
        return engaged

    @property
    def usable(self) -> bool:
        """Whether there is enough history to personalise from at all.

        One search clears this on its own. It is worth less than the floor as
        durable taste - and it should be, a search is not a booking - but it is
        an explicit statement of what someone wants, and ignoring it until they
        have typed it three times is the wrong way round.
        """
        if self.intent_vector is not None:
            return True
        return self.signal >= get_settings().taste_min_signal and (
            self.vector is not None or bool(self.genre_affinity)
        )

    def describe(self) -> dict:
        """A readable summary, for the API's introspection endpoint."""
        top = lambda mapping: dict(  # noqa: E731 - a local shorthand, used twice
            sorted(mapping.items(), key=lambda kv: -kv[1])[:5])
        return {
            "userId": self.user_id,
            "visitorId": self.visitor_id,
            "usable": self.usable,
            "signal": round(self.signal, 3),
            "events": self.events,
            "topGenres": {k: round(v, 3) for k, v in top(self.genre_affinity).items()},
            "topCities": {k: round(v, 3) for k, v in top(self.city_affinity).items()},
            "typicalRate": self.typical_rate,
            "bookedArtists": len(self.booked),
            "viewedArtists": len(self.viewed),
            "hasVector": self.vector is not None,
            "hasIntent": self.intent_vector is not None,
            "intentSignal": round(self.intent_signal, 3),
            "skippedArtists": len(self.skipped),
        }


# --------------------------------------------------------------------------- #
# Reading the history
# --------------------------------------------------------------------------- #

# `{owner}` is `user_id` or `visitor_id`, chosen in code and never taken from input.
# CLICK rows are left out: each is followed by a profile view of the same artist,
# which already counts, and what a click adds - its position - is read from the
# impression log instead.
_INTERACTIONS_SQL = """
SELECT kind, artist_id, search_query, genre, city, occasion, budget_per_hour, created_date
FROM user_interaction
WHERE {owner} = %(owner)s
  AND kind <> 'CLICK'
  AND created_date > NOW() - (%(days)s * INTERVAL '1 day')
ORDER BY created_date DESC
LIMIT %(limit)s
"""

# Artists near the top of this person's recent lists, by how many separate visits
# showed them. Whether they were then opened is decided in code against the history.
#
# Counted in half-hour buckets, not per list: reloading the front page four times
# in a minute is one look, not four decisions against every act on it. The first
# version counted lists, and a browser test that reloaded a page turned the acts
# on it into "skipped" within thirty seconds.
_SHOWN_SQL = """
SELECT shown.artist_id,
       COUNT(DISTINCT FLOOR(EXTRACT(EPOCH FROM d.created_date) / 1800))
FROM discovery_impression d,
     UNNEST(d.artist_ids[1:%(top)s]) AS shown(artist_id)
WHERE d.{owner} = %(owner)s
  AND d.created_date > NOW() - (%(days)s * INTERVAL '1 day')
GROUP BY shown.artist_id
HAVING COUNT(DISTINCT FLOOR(EXTRACT(EPOCH FROM d.created_date) / 1800)) >= %(threshold)s
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


def _genre_vocabulary() -> list[str]:
    """Every genre name, or nothing if the taxonomy cannot be read.

    Read once per profile build rather than once per search row. It is cached in
    the repository anyway, but a failure here should cost one log line rather
    than one per row.
    """
    try:
        from app.repository import genre_vocabulary

        return genre_vocabulary()
    except Exception:
        log.exception("Could not read the genre taxonomy; searches will not credit a genre")
        return []


def _genre_in(text: str, vocabulary: list[str]) -> str | None:
    """The genre named in a search, if one was, using the ranker's own matcher.

    The same function `/search` uses to read a genre out of a query, so a search
    contributes the genre it would have been ranked against rather than a second
    opinion about what the words mean.
    """
    if not vocabulary:
        return None
    from app.ranker import infer_genre

    return infer_genre(text, vocabulary)


def _normalise_shares(weights: dict[str, float]) -> dict[str, float]:
    """Scales a weight map so its strongest entry is 1.0."""
    if not weights:
        return {}
    strongest = max(weights.values())
    if strongest <= 0:
        return {}
    return {name: value / strongest for name, value in weights.items()}


def build_profile(user_id: int | None = None, visitor_id: str | None = None) -> TasteProfile:
    """Reads one person's history and turns it into a taste profile.

    Bounded on both sides — a time window and a row cap — so this stays a small
    indexed read however long someone has been on the platform. An account is
    read by `user_id`, with its bookings; a browser by `visitor_id`, without.
    """
    settings = get_settings()
    owner, value = ("user_id", user_id) if user_id else ("visitor_id", visitor_id)
    profile = TasteProfile(user_id=user_id or None, visitor_id=None if user_id else visitor_id)
    if not value:
        return profile

    params = {"owner": value, "user_id": user_id,
              "days": settings.taste_window_days,
              "limit": settings.taste_max_events}

    try:
        interactions = _rows(_INTERACTIONS_SQL.format(owner=owner), params)
        bookings = _rows(_BOOKINGS_SQL, params) if user_id else []
    except Exception:
        # A missing table on an install that has not migrated yet, or a database
        # blip. Discovery still works; it is simply not personalised.
        log.exception("Could not read the history for %s %s", owner, value)
        return profile

    if interactions or bookings:
        profile = profile_from_history(profile, interactions, bookings, datetime.now())

    try:
        shown = _rows(_SHOWN_SQL.format(owner=owner), {
            "owner": value, "top": settings.exposure_top_positions,
            "days": settings.skip_window_days, "threshold": settings.skip_threshold})
    except Exception:
        # No impression log yet. Nothing is skipped rather than everything.
        log.debug("Could not read served lists for %s %s", owner, value, exc_info=True)
        shown = []
    engaged = set(profile.viewed) | set(profile.booked)
    profile.skipped = {int(artist_id): int(count) for artist_id, count in shown
                       if artist_id is not None and int(artist_id) not in engaged}
    return profile


def profile_from_history(profile: TasteProfile, interactions: list[tuple],
                         bookings: list[tuple], now: datetime) -> TasteProfile:
    """Fills a profile from history rows, as that history stood at `now`.

    Split from the read so the offline evaluation can replay a person's history
    cut at any date and score the profile it would have produced then.
    """
    settings = get_settings()
    artist_ids = {int(row[1]) for row in interactions if row[1] is not None}
    artist_ids |= {int(row[0]) for row in bookings if row[0] is not None}
    artists = {a["artist_id"]: a for a in fetch_artists(list(artist_ids))} if artist_ids else {}
    profile.names = {artist_id: a["stage_name"] for artist_id, a in artists.items()
                     if a.get("stage_name")}

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

    intent_half_life = settings.taste_intent_half_life_days
    vocabulary = _genre_vocabulary() if any(row[2] for row in interactions) else []

    for kind, artist_id, query, genre, city, _occasion, budget, created in interactions:
        age = _age_days(created, now)
        weight = INTERACTION_WEIGHT.get(kind, 0.2) * _decay(age)
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
            # Searches accumulate rather than taking the strongest: asking for
            # the same thing four times is a firmer statement than asking once,
            # up to a cap so a repeated query cannot become the whole profile.
            intent = INTENT_WEIGHT * _decay(age, intent_half_life)
            query_texts[text] = min(MAX_INTENT_PER_TEXT,
                                    query_texts.get(text, 0.0) + intent)

            # A typed query names a genre far more often than a filter does -
            # "jazz trio for a dinner" states one as plainly as the dropdown -
            # and the row has nowhere to put it, because the organizer picked no
            # filter. Reading it back out is what lets a search contribute to
            # genre affinity, and what puts a reason on the resulting cards.
            credit(genre_weight, _genre_in(text, vocabulary), weight)

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

    profile.vector = _taste_vector(profile)
    profile.intent_vector, profile.intent_signal = _intent_vector(
        profile.user_id or profile.visitor_id, query_texts)
    return profile


def _centre(contributions: list[tuple[np.ndarray, float]]) -> np.ndarray | None:
    """The weighted centre of some vectors, back on the unit sphere.

    Re-normalised so a cosine against the result means the same thing whether it
    was built from two events or two hundred.
    """
    if not contributions:
        return None
    stacked = np.vstack([vector for vector, _ in contributions])
    weights = np.array([weight for _, weight in contributions], dtype=np.float32)
    centre = (stacked * weights[:, None]).sum(axis=0)
    norm = float(np.linalg.norm(centre))
    return None if norm < 1e-9 else (centre / norm).astype(np.float32)


def _taste_vector(profile: TasteProfile) -> np.ndarray | None:
    """Where this person's engagement sits, as one direction.

    Artists they booked or read, represented by their profile vectors — the ones
    with the stage name left out, because this is about what an act sounds like
    rather than what it is called. Searches are deliberately absent: they belong
    to the intent vector, and averaging the two is what let a long history bury
    a fresh search.
    """
    engaged = dict(profile.viewed)
    for artist_id, weight in profile.booked.items():
        engaged[artist_id] = engaged.get(artist_id, 0.0) + weight
    if not engaged:
        return None

    vectors = _embeddings(list(engaged))
    return _centre([(vector, engaged[artist_id]) for artist_id, vector in vectors.items()])


def _intent_vector(user_id: int,
                   query_texts: dict[str, float]) -> tuple[np.ndarray | None, float]:
    """What this person has been searching for lately, as one direction.

    Text rather than artists: a search that never reached a profile leaves no
    other trace, and it is often the clearest thing anyone tells the platform.
    """
    if not query_texts:
        return None, 0.0

    recent = sorted(query_texts.items(), key=lambda kv: -kv[1])[:MAX_QUERY_TEXTS]
    try:
        encoded = embed([text for text, _ in recent])
    except Exception:
        # The encoder is optional here: the taste vector alone still ranks.
        log.exception("Could not embed the search history for user %s", user_id)
        return None, 0.0

    weights = [weight for _, weight in recent]
    return _centre(list(zip(encoded, weights))), float(sum(weights))


# --------------------------------------------------------------------------- #
# Cache
# --------------------------------------------------------------------------- #

_cache: dict[tuple[str, object], tuple[float, TasteProfile]] = {}
_cache_lock = threading.Lock()
# Visitors are far more numerous than accounts, so the cache is sized for them.
_CACHE_MAX_USERS = 4096


def _key(user_id: int | None, visitor_id: str | None) -> tuple[str, object] | None:
    if user_id:
        return ("u", int(user_id))
    if visitor_id:
        return ("v", visitor_id)
    return None


def profile_for(user_id: int | None, visitor_id: str | None = None) -> TasteProfile | None:
    """The cached profile for an account or a browser, rebuilt when it has expired.

    Returns None for a caller who is neither, and for anyone with nothing to
    act on, so a caller can treat "no profile" as "rank the ordinary way"
    without inspecting anything. A profile that comes back may still be too
    thin to personalise from - check `usable` - but carry artists this person
    keeps being shown and passing over, which is worth acting on by itself.
    """
    key = _key(user_id, visitor_id)
    if key is None:
        return None

    ttl = get_settings().taste_cache_ttl_seconds
    now = time.monotonic()

    with _cache_lock:
        cached = _cache.get(key)
    if cached and now - cached[0] < ttl:
        profile = cached[1]
    else:
        profile = build_profile(user_id=user_id or None,
                                visitor_id=None if user_id else visitor_id)
        with _cache_lock:
            if len(_cache) >= _CACHE_MAX_USERS:
                # Cheapest useful eviction: drop the oldest half rather than track
                # access order for what is a short-lived cache anyway.
                for stale in sorted(_cache, key=lambda k: _cache[k][0])[: _CACHE_MAX_USERS // 2]:
                    _cache.pop(stale, None)
            _cache[key] = (now, profile)

    return profile if (profile.usable or profile.skipped) else None


def forget(user_id: int | None = None, visitor_id: str | None = None) -> None:
    """Drops cached profiles: one person's, or everyone's when given nobody."""
    with _cache_lock:
        key = _key(user_id, visitor_id)
        if key is None:
            _cache.clear()
        else:
            _cache.pop(key, None)


# --------------------------------------------------------------------------- #
# Scoring
# --------------------------------------------------------------------------- #

def _similarity_to(reference: np.ndarray | None, artist_id: int,
                   vectors: dict[int, np.ndarray],
                   band: tuple[float, float]) -> float | None:
    """Cosine between one of the profile's vectors and this artist, on 0-1.

    The band is a parameter because the two profile vectors live in different
    parts of the cosine distribution, and using one scale for both is wrong in a
    way that is invisible from the ranking. A taste vector is an average of
    *profile* vectors and sits high against another profile; an intent vector is
    an average of *query* vectors and sits lower against everything, the way any
    typed query does. Scaled through the taste band, a search that matched an
    artist perfectly scored about 0.2 and never earned a reason.

    None means the observation is missing — no such vector, or no embedding for
    that artist — rather than that the artist is a poor match. The caller drops
    the component instead of scoring a zero against it.
    """
    if reference is None:
        return None
    vector = vectors.get(artist_id)
    if vector is None:
        return None
    low, high = band
    span = high - low
    if span <= 1e-9:
        return 0.5
    cosine = float(np.dot(reference, vector))
    return float(min(1.0, max(0.0, (cosine - low) / span)))


def _taste_band() -> tuple[float, float]:
    settings = get_settings()
    return settings.taste_cos_low, settings.taste_cos_high


def _intent_band() -> tuple[float, float]:
    """The query-to-profile band, the same one `text_similarity` is scaled by."""
    settings = get_settings()
    return settings.similarity_cos_low, settings.similarity_cos_high


def affinity(profile: TasteProfile, artist: dict,
             vectors: dict[int, np.ndarray],
             co_choice: dict[int, tuple[float, int]] | None = None,
             co_scale: float = 0.0) -> tuple[float, list[str]]:
    """How well one artist fits this person, and why, on a 0-1 scale.

    The reasons are not a post-hoc story: each one is emitted by the component
    that actually contributed, and only when that component was strong enough to
    have moved the score.

    `co_choice` is `Signals.co_choice(profile.seeds())`: artist -> (score, the
    seed that contributed most). Absent, or with no scale to read it against,
    the component is dropped like any other signal this person has not given.
    """
    artist_id = int(artist["artist_id"])
    reasons: list[tuple[int, str]] = []     # (priority, line) - see REASON_PRIORITY
    parts: list[tuple[float, float]] = []   # (weight, value)

    # Intent carries the largest share of the score; which reason is *shown* is
    # decided separately, by REASON_PRIORITY, at the end.
    w_intent, w_taste, w_genre = _affinity_weights()

    intent = _similarity_to(profile.intent_vector, artist_id, vectors, _intent_band())
    if intent is not None:
        parts.append((w_intent, intent))
        if intent >= INTENT_REASON_AT:
            reasons.append((REASON_PRIORITY["intent"], "Matches what you have been searching for"))

    similarity = _similarity_to(profile.vector, artist_id, vectors, _taste_band())
    if similarity is not None:
        parts.append((w_taste, similarity))
        if similarity >= TASTE_REASON_AT:
            reasons.append((REASON_PRIORITY["taste"], "Close to the acts you have booked and viewed"))

    genres = [g for g in (artist.get("sub_genres") or []) + (artist.get("parent_genres") or []) if g]
    best_genre, genre_score = None, 0.0
    for genre in genres:
        share = profile.genre_affinity.get(genre, 0.0)
        if share > genre_score:
            best_genre, genre_score = genre, share
    if profile.genre_affinity:
        parts.append((w_genre, genre_score))
        if best_genre and genre_score >= 0.5:
            reasons.append((REASON_PRIORITY["genre"], f"You keep coming back to {best_genre}"))

    if co_choice is not None and co_scale > 0 and profile.seeds():
        score, via = co_choice.get(artist_id, (0.0, 0))
        together = min(1.0, score / co_scale)
        parts.append((W_CO_CHOICE, together))
        # Naming an act the person has already booked or read is the whole
        # point of this line, and it is never this artist itself: co-choice has
        # no diagonal.
        if together >= CO_CHOICE_REASON_AT and profile.names.get(via):
            reasons.append((REASON_PRIORITY["co_choice"], f"Often picked alongside {profile.names[via]}"))

    booked = profile.booked.get(artist_id, 0.0)
    viewed = profile.viewed.get(artist_id, 0.0)
    if booked > 0:
        familiarity = 1.0
        reasons.append((REASON_PRIORITY["booked"], "You have booked them before"))
    elif viewed > 0:
        # Two visits mean more than one, and ten mean little more than three.
        familiarity = min(1.0, 0.45 + viewed)
        reasons.append((REASON_PRIORITY["viewed"], "You looked at their profile"))
    else:
        familiarity = 0.0
    parts.append((W_FAMILIARITY, familiarity))

    if profile.typical_rate:
        # The same curve the ranker uses for a stated budget, with what this
        # person usually pays standing in for one.
        fit = price_fit(profile.typical_rate, float(artist["hourly_rate"]))
        parts.append((W_BUDGET, fit))
        if fit >= 0.75:
            reasons.append((REASON_PRIORITY["budget"], "Around what you usually pay"))

    city = (artist.get("city") or "").strip()
    if profile.city_affinity:
        city_score = profile.city_affinity.get(city, 0.0)
        parts.append((W_CITY, city_score))
        if city and city_score >= 0.6:
            reasons.append((REASON_PRIORITY["city"], f"In {city}, where you usually book"))

    # Renormalised over the components that could be evaluated, so an artist is
    # not penalised for a signal this person has never given.
    total_weight = sum(weight for weight, _ in parts)
    if total_weight <= 0:
        return 0.0, []
    score = sum(weight * value for weight, value in parts) / total_weight
    # Most specific first. The card shows one line, and a line that could be said
    # of half the list tells the reader nothing about this act.
    reasons.sort(key=lambda item: item[0])
    return float(score), [line for _, line in reasons[:2]]


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
           alpha: float, co_choice: dict[int, tuple[float, int]] | None = None,
           co_scale: float = 0.0) -> tuple[np.ndarray, list[list[str]]]:
    """Blends the model's ordering with this person's affinity.

    `alpha` is personalisation's share of the final score. The model still does
    the ranking; this moves candidates within it.
    """
    if not candidates:
        return scores, []

    needs_vectors = profile.vector is not None or profile.intent_vector is not None
    vectors = _embeddings([int(c["artist_id"]) for c in candidates]) if needs_vectors else {}
    scored = [affinity(profile, candidate, vectors, co_choice, co_scale)
              for candidate in candidates]
    affinities = np.array([value for value, _ in scored], dtype=np.float64)
    reasons = [why for _, why in scored]

    blended = (1.0 - alpha) * _to_unit_scale(np.asarray(scores)) + alpha * affinities
    return blended, reasons


def skip_strength(profile: TasteProfile | None, candidates: list[dict]) -> np.ndarray:
    """0-1 per candidate: how firmly this person has passed over them.

    Zero until an artist has been near the top of `skip_threshold` of this
    person's lists without being opened, then rising to one over the next few.
    Someone who reloads the front page five times and never opens the act in
    the first slot has answered a question about that act; showing it a sixth
    time in the same place ignores the answer. It is shown lower, not hidden -
    they may simply not have got round to it.
    """
    strength = np.zeros(len(candidates), dtype=np.float64)
    if profile is None or not profile.skipped:
        return strength
    threshold = get_settings().skip_threshold
    for index, candidate in enumerate(candidates):
        shown = profile.skipped.get(int(candidate["artist_id"]), 0)
        if shown >= threshold:
            strength[index] = min(1.0, (shown - threshold + 1) / 4.0)
    return strength


def query_space_vector(profile: TasteProfile,
                       query_vector: np.ndarray | None) -> np.ndarray | None:
    """What this person is asking for now: stated filters and recent searches.

    Both are query text, so blending them is comparing like with like. The taste
    vector is deliberately not in here — it is an average of profile vectors and
    would take the comparison over, as the module notes explain; it retrieves
    from its own share of the pool instead. Intent takes the larger share of
    this side, because a search is what someone typed and a filter is what they
    clicked past.
    """
    if profile.intent_vector is None:
        return query_vector
    if query_vector is None:
        return profile.intent_vector

    share = get_settings().taste_intent_share
    return _centre([(profile.intent_vector, share), (query_vector, 1.0 - share)])


def note_search(user_id: int | None, query_text: str | None,
                visitor_id: str | None = None) -> None:
    """Folds a search into the cached profile the moment it is made.

    The API records the search on its own thread and this service rebuilds a
    profile at most every `taste_cache_ttl_seconds`, so without this the browse
    surface someone lands on straight after searching still reflects the profile
    they had *before* they searched. That is the gap between "I searched for a
    DJ" and "it is not showing me DJs", and it closes here rather than by
    rebuilding the profile on every request.

    The row still reaches the database, and the next rebuild reads it: this
    anticipates that read, it does not replace it.
    """
    key = _key(user_id, visitor_id)
    if key is None or not query_text or not query_text.strip():
        return

    with _cache_lock:
        cached = _cache.get(key)
    if cached is None:
        # Nothing cached to update, and the next build will read the row anyway.
        return

    profile = cached[1]
    try:
        vector = np.asarray(embed_one(query_text.strip()), dtype=np.float32)
    except Exception:
        log.exception("Could not embed a search for %s", key)
        return

    weight = min(MAX_INTENT_PER_TEXT, INTENT_WEIGHT)
    if profile.intent_vector is None:
        profile.intent_vector, profile.intent_signal = vector, weight
        return

    # An online update of the same weighted centre `_intent_vector` builds.
    combined = _centre([(profile.intent_vector, profile.intent_signal), (vector, weight)])
    if combined is not None:
        profile.intent_vector = combined
        profile.intent_signal += weight
