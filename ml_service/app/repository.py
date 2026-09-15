"""Reads the artist catalogue in the shape the model needs.

Everything here comes from the tables the API already maintains. Two of the
signals the model uses are not stored directly and are derived instead:

  completed_bookings  confirmed bookings, the platform's proxy for experience
  response_rate       share of requests the artist answered rather than let
                      lapse, defaulting to a neutral prior for new artists
"""

from __future__ import annotations

import logging
import threading
import time
from typing import NamedTuple

from app.db import connection

log = logging.getLogger(__name__)

# Neutral prior for an artist with no request history, so newcomers are not
# punished for the absence of data.
DEFAULT_RESPONSE_RATE = 0.75

_ARTIST_QUERY = """
WITH genres AS (
    SELECT ag.artist_id,
           ARRAY_AGG(DISTINCT c.name)  AS sub_genres,
           ARRAY_AGG(DISTINCT g.name)  AS parent_genres
    FROM artists_genres ag
    JOIN category c            ON c.id = ag.genres_id
    LEFT JOIN genre_categories gc ON gc.categories_id = c.id
    LEFT JOIN genre g          ON g.id = gc.genre_id
    GROUP BY ag.artist_id
),
booking_stats AS (
    -- A played gig is COMPLETED, not CONFIRMED. Counting only CONFIRMED made an
    -- act with forty gigs behind it and two ahead look like it had played two,
    -- and made it look as if it had ignored the other forty requests.
    SELECT artist_id,
           COUNT(*) FILTER (WHERE status IN ('COMPLETED', 'CONFIRMED'))        AS completed,
           COUNT(*) FILTER (WHERE status <> 'PENDING')                          AS answered,
           COUNT(*)                                                            AS total
    FROM booking
    GROUP BY artist_id
),
review_stats AS (
    SELECT artist_id, COUNT(*) AS reviews
    FROM review
    GROUP BY artist_id
),
prior AS (
    SELECT COALESCE(AVG(rating), 4.0)::float AS mean FROM review
)
SELECT a.id                                   AS artist_id,
       a.slug,
       a.stage_name,
       COALESCE(a.bio, '')                    AS bio,
       a.hourly_rate,
       COALESCE(a.rating, 3.0)                AS rating,
       COALESCE(rs.reviews, 0)                AS review_count,
       -- The rating shrunk towards the catalogue mean, as if every act had a
       -- few extra reviews at that mean. What ranking reads; `rating` is what
       -- is displayed. Unrated acts sit at the mean rather than at 3.0.
       (%(prior_reviews)s * prior.mean + COALESCE(rs.reviews, 0) * COALESCE(a.rating, prior.mean))
           / (%(prior_reviews)s + COALESCE(rs.reviews, 0)) AS rating_smoothed,
       COALESCE(u.location, '')               AS city,
       COALESCE(u.full_name, a.stage_name)    AS full_name,
       u.profile                              AS profile_image,
       COALESCE(gz.sub_genres, ARRAY[]::text[])    AS sub_genres,
       COALESCE(gz.parent_genres, ARRAY[]::text[]) AS parent_genres,
       COALESCE(bs.completed, 0)              AS completed_bookings,
       CASE WHEN COALESCE(bs.total, 0) = 0 THEN %(default_response)s
            ELSE bs.answered::float / bs.total
       END                                    AS response_rate
FROM artists a
CROSS JOIN prior
LEFT JOIN users u          ON u.id = a.user_id
LEFT JOIN genres gz        ON gz.artist_id = a.id
LEFT JOIN booking_stats bs ON bs.artist_id = a.id
LEFT JOIN review_stats rs  ON rs.artist_id = a.id
"""


def fetch_artists(artist_ids: list[int] | None = None) -> list[dict]:
    """The whole catalogue, or just the given ids."""
    sql = _ARTIST_QUERY
    from app.config import get_settings

    params: dict = {"default_response": DEFAULT_RESPONSE_RATE,
                    "prior_reviews": get_settings().rating_prior_reviews}

    if artist_ids is not None:
        if not artist_ids:
            return []
        sql += " WHERE a.id = ANY(%(ids)s)"
        params["ids"] = artist_ids

    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        columns = [description[0] for description in cur.description]
        return [dict(zip(columns, row)) for row in cur.fetchall()]


def profile_document(artist: dict) -> str:
    """The same profile with the stage name left out.

    Used for similarity and clustering. Including the name there means two acts
    that merely share a word look alike, which is a naming coincidence rather
    than a musical one.
    """
    return _document(artist, include_name=False)


def artist_document(artist: dict) -> str:
    """The text an artist is embedded from.

    Genres and city are folded into the sentence rather than left as filters
    only, so a query like "acoustic folk singer in Pokhara" matches on meaning
    even when the words never appear in the artist's own bio.
    """
    return _document(artist, include_name=True)


def _document(artist: dict, include_name: bool) -> str:
    parts = [artist["stage_name"]] if include_name else []
    if artist.get("parent_genres"):
        parts.append("plays " + ", ".join(g for g in artist["parent_genres"] if g))
    if artist.get("sub_genres"):
        parts.append("styles: " + ", ".join(s for s in artist["sub_genres"] if s))
    if artist.get("city"):
        parts.append(f"based in {artist['city']}")
    if artist.get("bio"):
        parts.append(artist["bio"])
    return ". ".join(part for part in parts if part)


_TAXONOMY_QUERY = """
SELECT g.name AS parent, c.name AS sub
FROM genre_categories gc
JOIN genre g    ON g.id = gc.genre_id
JOIN category c ON c.id = gc.categories_id
WHERE g.active AND c.active
"""

_ORPHAN_SUBS_QUERY = """
SELECT c.name FROM category c
WHERE c.active AND NOT EXISTS (
    SELECT 1 FROM genre_categories gc WHERE gc.categories_id = c.id
)
"""

# The taxonomy changes when an admin edits it, which is rare, and it was being
# re-read from the database on every single search. Cached with an expiry rather
# than forever, so an edit shows up without a restart.
_TAXONOMY_TTL_SECONDS = 300.0
_taxonomy_cache: dict | None = None
_taxonomy_cached_at = 0.0
_taxonomy_lock = threading.Lock()


class Taxonomy(NamedTuple):
    """The genre tree, in the two shapes the ranker needs."""

    parent_of: dict[str, str]   # sub-genre -> its parent
    parents: frozenset[str]     # every parent genre name
    vocabulary: list[str]       # every name, parent and sub alike

    def resolve(self, wanted: str | None) -> tuple[str | None, str | None]:
        """Splits a requested genre into (sub-genre, parent genre).

        The organizer picks one name from one list and the model wants both
        halves. "Bebop" is a sub-genre of Jazz, so a Jazz act that does not list
        Bebop should still score a partial match; "Jazz" is a parent, so nothing
        can score an exact sub-genre match against it.

        Passing the same string as both — which is what this code used to do —
        meant a sub-genre request scored any other act in the same parent genre
        at zero, indistinguishable from a completely unrelated one.
        """
        if not wanted:
            return None, None
        name = wanted.strip()
        if not name:
            return None, None
        if name in self.parents:
            return None, name
        parent = self.parent_of.get(name)
        if parent:
            return name, parent
        # Unknown to the catalogue: treat it as a sub-genre and let the match
        # fall through to zero rather than inventing a parent for it.
        return name, None


def genre_taxonomy() -> Taxonomy:
    """The genre tree, cached briefly."""
    global _taxonomy_cache, _taxonomy_cached_at

    now = time.monotonic()
    cached = _taxonomy_cache
    if cached is not None and now - _taxonomy_cached_at < _TAXONOMY_TTL_SECONDS:
        return cached["taxonomy"]

    with _taxonomy_lock:
        # Another thread may have refreshed it while this one waited.
        cached = _taxonomy_cache
        if cached is not None and time.monotonic() - _taxonomy_cached_at < _TAXONOMY_TTL_SECONDS:
            return cached["taxonomy"]

        with connection() as conn, conn.cursor() as cur:
            cur.execute(_TAXONOMY_QUERY)
            rows = cur.fetchall()
            cur.execute(_ORPHAN_SUBS_QUERY)
            orphans = [row[0] for row in cur.fetchall() if row[0]]

        parent_of = {sub: parent for parent, sub in rows if sub and parent}
        parents = frozenset(parent for parent, _ in rows if parent)
        vocabulary = sorted(set(parent_of) | parents | set(orphans))

        taxonomy = Taxonomy(parent_of=parent_of, parents=parents, vocabulary=vocabulary)
        _taxonomy_cache = {"taxonomy": taxonomy}
        _taxonomy_cached_at = time.monotonic()
        log.info("Genre taxonomy loaded: %d parents, %d sub-genres",
                 len(parents), len(parent_of))
        return taxonomy


def genre_vocabulary() -> list[str]:
    """Every genre and sub-genre name the catalogue knows about."""
    return genre_taxonomy().vocabulary
