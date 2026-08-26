"""Retrieval: narrows the catalogue to a candidate set the ranker can reorder.

Vector similarity answers "what is this query about", which keyword matching
cannot: "someone mellow for a restaurant opening" finds acoustic and jazz acts
without either word appearing. Hard constraints (city, budget) stay as SQL
filters, because those are requirements rather than preferences.
"""

from __future__ import annotations

import logging

from app.config import get_settings
from app.db import connection
from app.embedder import embed_one
from app.repository import artist_document, fetch_artists

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

            with conn.cursor() as cur:
                for artist, document, vector in zip(batch, documents, vectors):
                    cur.execute(
                        """
                        INSERT INTO artist_embedding (artist_id, embedding, source_text, updated_at)
                        VALUES (%s, %s, %s, NOW())
                        ON CONFLICT (artist_id) DO UPDATE
                        SET embedding = EXCLUDED.embedding,
                            source_text = EXCLUDED.source_text,
                            updated_at = NOW()
                        """,
                        (artist["artist_id"], vector, document),
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
    settings = get_settings()
    limit = limit or settings.candidate_pool_size
    query_vector = embed_one(query_text)

    filters, params = [], {"query_vector": query_vector, "limit": limit}
    if city:
        filters.append("LOWER(u.location) = LOWER(%(city)s)")
        params["city"] = city
    if max_hourly_rate:
        filters.append("a.hourly_rate <= %(max_rate)s")
        params["max_rate"] = max_hourly_rate
    where = ("WHERE " + " AND ".join(filters)) if filters else ""

    sql = f"""
        SELECT ae.artist_id,
               1 - (ae.embedding <=> %(query_vector)s) AS similarity
        FROM artist_embedding ae
        JOIN artists a ON a.id = ae.artist_id
        LEFT JOIN users u ON u.id = a.user_id
        {where}
        ORDER BY ae.embedding <=> %(query_vector)s
        LIMIT %(limit)s
    """

    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, params)
        hits = {row[0]: float(row[1]) for row in cur.fetchall()}

    if not hits:
        return []

    artists = fetch_artists(list(hits))
    for artist in artists:
        artist["similarity"] = hits.get(artist["artist_id"], 0.0)
    artists.sort(key=lambda a: -a["similarity"])
    return artists


def embedding_count() -> int:
    with connection() as conn, conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM artist_embedding")
        return int(cur.fetchone()[0])
