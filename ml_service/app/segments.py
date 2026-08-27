"""Serving for artist segments and nearest neighbours.

Two things read the same embeddings from different angles: which group an artist
belongs to, and which individual artists are closest to them. The first is the
fitted model; the second is a pgvector nearest-neighbour query, which needs no
model at all and stays correct between retrainings.
"""

from __future__ import annotations

import json
import logging
from pathlib import Path

from app.config import get_settings
from app.db import connection

log = logging.getLogger(__name__)

_model = None
_report: dict | None = None
# mtime of the report the cache was built from, so a retrain run outside this
# process (docker exec, a cron job) is picked up rather than served stale.
_report_mtime: float | None = None


def _model_path() -> Path:
    return Path(get_settings().model_dir) / "segments.joblib"


def _report_path() -> Path:
    return Path(get_settings().model_dir) / "segments.json"


def load() -> None:
    """Loads the fitted model and its report, if segmentation has been run."""
    global _model, _report, _report_mtime

    if _report_path().exists():
        try:
            _report = json.loads(_report_path().read_text())
            _report_mtime = _report_path().stat().st_mtime
        except (OSError, json.JSONDecodeError):
            log.warning("Segment report unreadable; ignoring")
            _report = None
            _report_mtime = None

    if _model_path().exists():
        try:
            import joblib
            _model = joblib.load(_model_path())
            log.info("Segment model loaded (k=%s)", getattr(_model, "n_clusters", "?"))
        except Exception:  # noqa: BLE001 - a bad artefact must not stop the service
            log.exception("Segment model failed to load; segmentation endpoints will be empty")
            _model = None


def reload_model() -> None:
    global _model, _report, _report_mtime
    _model = None
    _report = None
    _report_mtime = None
    load()


def _refresh_if_stale() -> None:
    """Reloads when the report on disk is newer than the one in memory.

    The trainer can be run through the API or straight from the command line.
    Without this, a command-line run leaves the service serving the previous
    run's labels against the new run's assignments — which shows up as a
    segment listing a label with a membership of zero.
    """
    path = _report_path()
    if not path.exists():
        return
    try:
        mtime = path.stat().st_mtime
    except OSError:
        return
    if _report_mtime is None or mtime > _report_mtime:
        log.info("Segment report changed on disk; reloading")
        load()


def is_ready() -> bool:
    _refresh_if_stale()
    return _report is not None


def report() -> dict:
    """The full training report: metrics per k, and a profile per segment."""
    _refresh_if_stale()
    if _report is None:
        return {"trained": False, "detail": "Segmentation has not been run yet."}
    return {"trained": True, **_report}


def overview() -> list[dict]:
    """Segments with their live membership counts and a few representative artists.

    Counts come from the table rather than the report so they stay accurate if
    artists were added after training — those rows simply have no segment yet,
    and the totals here will not silently claim otherwise.
    """
    _refresh_if_stale()
    if _report is None:
        return []

    schema = get_settings().db_schema
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT s.segment, COUNT(*) AS size
            FROM {schema}.artist_segment s
            GROUP BY s.segment
            ORDER BY s.segment
            """
        )
        sizes = {row[0]: row[1] for row in cur.fetchall()}

        # The artists closest to their centroid are the ones that best describe
        # the segment, so they are the ones worth showing.
        cur.execute(
            f"""
            SELECT segment, artist_id, stage_name FROM (
                SELECT s.segment,
                       s.artist_id,
                       a.stage_name,
                       ROW_NUMBER() OVER (PARTITION BY s.segment ORDER BY s.distance) AS rank
                FROM {schema}.artist_segment s
                JOIN artists a ON a.id = s.artist_id
            ) ranked
            WHERE rank <= 4
            ORDER BY segment, rank
            """
        )
        representatives: dict[int, list[dict]] = {}
        for segment, artist_id, stage_name in cur.fetchall():
            representatives.setdefault(segment, []).append(
                {"artistId": artist_id, "stageName": stage_name}
            )

    out = []
    for profile in _report.get("segments", []):
        segment = profile["segment"]
        out.append(
            {
                "segment": segment,
                "label": profile["label"],
                "size": sizes.get(segment, 0),
                "terms": profile["terms"],
                "topGenres": profile["top_genres"],
                "avgRating": profile["avg_rating"],
                "avgHourlyRate": profile["avg_hourly_rate"],
                "representatives": representatives.get(segment, []),
            }
        )
    return out


def segment_of(artist_id: int) -> dict | None:
    """The segment one artist belongs to, with its profile."""
    _refresh_if_stale()
    schema = get_settings().db_schema
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            f"SELECT segment, distance FROM {schema}.artist_segment WHERE artist_id = %s",
            (artist_id,),
        )
        row = cur.fetchone()

    if row is None:
        return None

    segment, distance = row
    profile = next(
        (p for p in (_report or {}).get("segments", []) if p["segment"] == segment),
        None,
    )
    return {
        "segment": int(segment),
        "distance": float(distance),
        "label": profile["label"] if profile else f"Segment {segment}",
        "terms": profile["terms"] if profile else [],
    }


def similar_artists(artist_id: int, limit: int = 6) -> list[dict]:
    """Nearest neighbours in embedding space.

    Answered by pgvector rather than the clustering model: two artists can be
    close together while sitting either side of a cluster boundary, and for a
    "more like this" strip proximity is the honest answer. `<=>` is cosine
    distance, so `1 - distance` reads as a similarity between 0 and 1.
    """
    schema = get_settings().db_schema
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            WITH target AS (
                SELECT COALESCE(profile_embedding, embedding) AS vec
                FROM {schema}.artist_embedding WHERE artist_id = %(artist_id)s
            )
            SELECT e.artist_id,
                   a.slug,
                   a.stage_name,
                   COALESCE(u.full_name, a.stage_name)  AS full_name,
                   u.profile                            AS profile_image,
                   COALESCE(a.rating, 3.0)              AS rating,
                   a.hourly_rate,
                   COALESCE(u.location, '')             AS city,
                   COALESCE(e.profile_embedding, e.embedding) <=> (SELECT vec FROM target) AS distance
            FROM {schema}.artist_embedding e
            JOIN artists a ON a.id = e.artist_id
            LEFT JOIN users u ON u.id = a.user_id
            WHERE e.artist_id <> %(artist_id)s
              AND EXISTS (SELECT 1 FROM target)
            ORDER BY distance
            LIMIT %(limit)s
            """,
            {"artist_id": artist_id, "limit": limit},
        )
        rows = cur.fetchall()

    return [
        {
            "artistId": row[0],
            # The public URL segment, so the "more like this" strip links the way
            # every other artist link does rather than falling back to bare ids.
            "slug": row[1],
            "stageName": row[2],
            "fullName": row[3],
            "profileImage": row[4],
            "rating": float(row[5]),
            "hourlyRate": float(row[6]) if row[6] is not None else None,
            "city": row[7],
            "similarity": round(1.0 - float(row[8]), 4),
        }
        for row in rows
    ]
