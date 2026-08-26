"""Reads the artist catalogue in the shape the model needs.

Everything here comes from the tables the API already maintains. Two of the
signals the model uses are not stored directly and are derived instead:

  completed_bookings  confirmed bookings, the platform's proxy for experience
  response_rate       share of requests the artist answered rather than let
                      lapse, defaulting to a neutral prior for new artists
"""

from __future__ import annotations

import logging

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
    SELECT artist_id,
           COUNT(*) FILTER (WHERE status = 'CONFIRMED')                        AS completed,
           COUNT(*) FILTER (WHERE status IN ('CONFIRMED', 'DECLINED'))          AS answered,
           COUNT(*)                                                            AS total
    FROM booking
    GROUP BY artist_id
)
SELECT a.id                                   AS artist_id,
       a.stage_name,
       COALESCE(a.bio, '')                    AS bio,
       a.hourly_rate,
       COALESCE(a.rating, 3.0)                AS rating,
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
LEFT JOIN users u          ON u.id = a.user_id
LEFT JOIN genres gz        ON gz.artist_id = a.id
LEFT JOIN booking_stats bs ON bs.artist_id = a.id
"""


def fetch_artists(artist_ids: list[int] | None = None) -> list[dict]:
    """The whole catalogue, or just the given ids."""
    sql = _ARTIST_QUERY
    params: dict = {"default_response": DEFAULT_RESPONSE_RATE}

    if artist_ids is not None:
        if not artist_ids:
            return []
        sql += " WHERE a.id = ANY(%(ids)s)"
        params["ids"] = artist_ids

    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        columns = [description[0] for description in cur.description]
        return [dict(zip(columns, row)) for row in cur.fetchall()]


def artist_document(artist: dict) -> str:
    """The text an artist is embedded from.

    Genres and city are folded into the sentence rather than left as filters
    only, so a query like "acoustic folk singer in Pokhara" matches on meaning
    even when the words never appear in the artist's own bio.
    """
    parts = [artist["stage_name"]]
    if artist.get("parent_genres"):
        parts.append("plays " + ", ".join(g for g in artist["parent_genres"] if g))
    if artist.get("sub_genres"):
        parts.append("styles: " + ", ".join(s for s in artist["sub_genres"] if s))
    if artist.get("city"):
        parts.append(f"based in {artist['city']}")
    if artist.get("bio"):
        parts.append(artist["bio"])
    return ". ".join(part for part in parts if part)


_VOCAB_QUERY = """
SELECT DISTINCT name FROM category WHERE active
UNION
SELECT DISTINCT name FROM genre WHERE active
"""


def genre_vocabulary() -> list[str]:
    """Every genre and sub-genre name the catalogue knows about."""
    with connection() as conn, conn.cursor() as cur:
        cur.execute(_VOCAB_QUERY)
        return [row[0] for row in cur.fetchall() if row[0]]
