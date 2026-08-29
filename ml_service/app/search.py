"""Retrieval: narrows the catalogue to a candidate set the ranker can reorder.

Vector similarity answers "what is this query about", which keyword matching
cannot: "someone mellow for a restaurant opening" finds acoustic and jazz acts
without either word appearing. Hard constraints (city, budget) stay as SQL
filters, because those are requirements rather than preferences.
"""

from __future__ import annotations

import logging
from itertools import zip_longest

from app.config import get_settings
from app.db import connection
from app.embedder import embed_one
from app.repository import artist_document, fetch_artists, profile_document

log = logging.getLogger(__name__)


def rebuild_embeddings(batch_size: int = 128) -> int:
    """Re-embeds every artist. Run after a bulk import or a bio edit."""
    from app.embedder import embed

    artists = fetch_artists()
    if not artists:
        log.warning("No artists to embed")
        return 0

    written = 0
    with connection() as conn:
        for start in range(0, len(artists), batch_size):
            batch = artists[start:start + batch_size]
            documents = [artist_document(artist) for artist in batch]
            vectors = embed(documents)

            # The same profiles without the stage name, for similarity and
            # clustering. Embedded in one extra call per batch rather than one
            # per artist.
            profiles = [profile_document(artist) for artist in batch]
            profile_vectors = embed(profiles)

            with conn.cursor() as cur:
                for artist, document, vector, profile_vector in zip(
                    batch, documents, vectors, profile_vectors
                ):
                    cur.execute(
                        f"""
                        INSERT INTO {get_settings().db_schema}.artist_embedding
                            (artist_id, embedding, profile_embedding, source_text, updated_at)
                        VALUES (%s, %s, %s, %s, NOW())
                        ON CONFLICT (artist_id) DO UPDATE
                        SET embedding = EXCLUDED.embedding,
                            profile_embedding = EXCLUDED.profile_embedding,
                            source_text = EXCLUDED.source_text,
                            updated_at = NOW()
                        """,
                        (artist["artist_id"], vector, profile_vector, document),
                    )
            conn.commit()
            written += len(batch)
            log.info("Embedded %d/%d artists", written, len(artists))

    return written


def vector_candidates(query_text: str, limit: int | None = None,
                      city: str | None = None,
                      max_hourly_rate: float | None = None) -> list[dict]:
    """Nearest artists to the query, with hard filters applied first.

    Returns each candidate's cosine similarity alongside its attributes, so the
    ranker can use closeness as one signal among several rather than as the
    final answer.
    """
    query_vector = embed_one(query_text)
    return candidates_near(query_vector, limit=limit, city=city,
                           max_hourly_rate=max_hourly_rate,
                           similarity_vector=query_vector)


def candidates_near(retrieval_vector, limit: int | None = None,
                    city: str | None = None,
                    max_hourly_rate: float | None = None,
                    *, on_profile_vectors: bool = False,
                    similarity_vector=None) -> list[dict]:
    """The same retrieval, from a vector that was not necessarily typed.

    Split out because a browse surface has no words to embed: personalisation
    retrieves against a taste vector built from what someone has been doing,
    which is the only way a preference can reach the candidate pool at all —
    re-ranking cannot promote an artist that retrieval never returned.

    Two vectors, because they answer different questions. `retrieval_vector`
    decides *which* artists come back. `similarity_vector` is what the reported
    cosine is measured against, and it is the query vector or nothing: that
    number becomes the ranker's `text_similarity`, which was calibrated on
    query-to-profile cosines, and quietly handing it the distance to a taste
    vector instead would feed the model a feature that no longer means what it
    was trained on. A browse with no words reports no similarity at all, which
    the ranker already handles as a withheld signal.

    `on_profile_vectors` picks the column to search. A taste vector is built
    from profile vectors — the ones with the stage name left out — so it is
    matched against those; a typed query is matched against the search vectors,
    which include the name, because looking an act up by name has to work.
    """
    settings = get_settings()
    limit = limit or settings.candidate_pool_size
    column = ("COALESCE(ae.profile_embedding, ae.embedding)" if on_profile_vectors
              else "ae.embedding")

    filters, params = [], {"retrieval_vector": retrieval_vector, "limit": limit}
    if city:
        filters.append("LOWER(u.location) = LOWER(%(city)s)")
        params["city"] = city
    if max_hourly_rate:
        filters.append("a.hourly_rate <= %(max_rate)s")
        params["max_rate"] = max_hourly_rate
    where = ("WHERE " + " AND ".join(filters)) if filters else ""

    if similarity_vector is None:
        similarity = "NULL::float"
    else:
        similarity = "1 - (ae.embedding <=> %(similarity_vector)s)"
        params["similarity_vector"] = similarity_vector

    sql = f"""
        SELECT ae.artist_id,
               {similarity} AS similarity
        FROM {settings.db_schema}.artist_embedding ae
        JOIN artists a ON a.id = ae.artist_id
        LEFT JOIN users u ON u.id = a.user_id
        {where}
        ORDER BY {column} <=> %(retrieval_vector)s
        LIMIT %(limit)s
    """

    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        # Retrieval order is kept: it is the answer to "who is closest", and on a
        # personalised browse it is the only place the taste vector shows up.
        hits = {row[0]: (None if row[1] is None else float(row[1])) for row in cur.fetchall()}

    if not hits:
        return []

    order = {artist_id: position for position, artist_id in enumerate(hits)}
    artists = fetch_artists(list(hits))
    for artist in artists:
        artist["similarity"] = hits.get(artist["artist_id"])
    artists.sort(key=lambda a: order.get(a["artist_id"], len(order)))
    return artists


def merged_candidates(query_side, taste_side, *, taste_share: float,
                      limit: int, city: str | None = None,
                      similarity_vector=None) -> list[dict]:
    """Candidates from two sources at once, in a stated proportion.

    Personalised browse has two things to retrieve on and they do not live in
    the same distribution: what the person is asking for now is query text, and
    what they have engaged with is a centre of profile vectors. Averaging them
    into one vector and sorting by distance does not give each the share it was
    meant to have — profile vectors sit closer to every other profile vector
    than query text ever does, so that side wins the comparison outright.

    Splitting the pool by slot count instead makes the share exact: `taste_share`
    of the candidates come from the person's history and the rest from what they
    just asked for. The ranker then scores the union, and the merge order below
    only decides who survives a tie in the pool, not who ends up on top.
    """
    if query_side is None and taste_side is None:
        return []
    if taste_side is None:
        return candidates_near(query_side, limit=limit, city=city,
                               similarity_vector=similarity_vector)
    if query_side is None:
        return candidates_near(taste_side, limit=limit, city=city,
                               on_profile_vectors=True,
                               similarity_vector=similarity_vector)

    from_taste = max(1, round(limit * taste_share))
    from_query = max(1, limit - from_taste)

    asked_for = candidates_near(query_side, limit=from_query, city=city,
                                similarity_vector=similarity_vector)
    history = candidates_near(taste_side, limit=from_taste, city=city,
                              on_profile_vectors=True,
                              similarity_vector=similarity_vector)

    # Interleaved, so an overlap between the two costs the tail of both rather
    # than the whole of one.
    merged: dict[int, dict] = {}
    for pair in zip_longest(asked_for, history):
        for candidate in pair:
            if candidate is not None:
                merged.setdefault(candidate["artist_id"], candidate)
    return list(merged.values())


def requirement_query(genre: str | None, event_type: str | None,
                      city: str | None) -> str | None:
    """A query built from browse filters, phrased the way artists are embedded.

    Browse surfaces have no text box, but they are not without intent: an
    organizer filtering to Jazz for a Corporate event has said what they want as
    plainly as if they had typed it. Turning those filters back into a sentence
    gives retrieval something to match on, and gives the ranker the text feature
    that carries most of its gain — which was otherwise fed a flat constant for
    every candidate, leaving the model unable to separate them.

    A city on its own returns nothing. Location is already a hard filter and a
    feature of its own; embedding "in Pokhara" alone would add noise, not taste.
    """
    if not genre and not event_type:
        return None

    parts = []
    if genre:
        parts.append(genre)
    if event_type:
        parts.append(f"for a {event_type} event")
    if city:
        parts.append(f"in {city}")
    return " ".join(parts)


def embedding_count() -> int:
    with connection() as conn, conn.cursor() as cur:
        cur.execute(f"SELECT COUNT(*) FROM {get_settings().db_schema}.artist_embedding")
        return int(cur.fetchone()[0])
