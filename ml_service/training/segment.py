"""Artist segmentation.

Groups the catalogue into segments by what artists actually are, using the same
embeddings search retrieves against. Unsupervised on purpose: the platform has
no labelled artist "types", and inventing labels by hand would only encode
whatever taxonomy the person writing them happened to hold.

This is the one model here that is fit to real rows rather than to a synthetic
generator. Nothing about it is derived from a value that was injected into the
data — the embeddings come from the bios artists wrote, and the clusters are
whatever structure those bios actually contain.

    python -m training.segment            # choose k automatically, write results
    python -m training.segment --k 6      # force a specific k

What it produces:

  ml.artist_segment   one row per artist: segment, distance to its centroid
  models/segments.joblib  the fitted model, for assigning a new artist later
  models/segments.json    the full report: metrics per k, cluster profiles
"""

from __future__ import annotations

import argparse
import json
import logging
import re
from collections import Counter
from dataclasses import dataclass, asdict
from pathlib import Path

import numpy as np
from sklearn.cluster import KMeans
from sklearn.metrics import calinski_harabasz_score, davies_bouldin_score, silhouette_score

from app.config import get_settings
from app.db import connection

log = logging.getLogger(__name__)

# Range of k to consider. Below two there is nothing to compare; much past a
# dozen the segments stop being something a person can hold in their head, which
# is the point of segmenting at all.
K_MIN = 2
K_MAX = 12

# Terms that describe every artist and so distinguish none of them.
STOPWORDS = {
    "the", "and", "for", "with", "our", "out", "off", "we", "us", "i", "a", "an",
    "of", "to", "in", "on", "at", "by", "from", "up", "or", "as", "is", "are",
    "be", "been", "it", "its", "that", "this", "these", "those", "you", "your",
    "play", "plays", "playing", "played", "act", "acts", "years", "year", "piece",
    "around", "mostly", "started", "bring", "own", "anywhere", "travel", "sets",
    "minutes", "hours", "hour", "three", "enough", "matter", "loud", "long",
    "since", "back", "over", "into", "any", "all", "can", "will", "have", "has",
}

WORD = re.compile(r"[a-z][a-z'-]+")


@dataclass
class SweepPoint:
    """One candidate k and how good the partition was at that k."""
    k: int
    silhouette: float
    davies_bouldin: float
    calinski_harabasz: float
    inertia: float
    smallest_cluster: int


@dataclass
class SegmentProfile:
    segment: int
    size: int
    label: str
    terms: list[str]
    top_genres: list[str]
    avg_rating: float
    avg_hourly_rate: float
    example_artists: list[str]


def load_embeddings() -> tuple[np.ndarray, list[dict]]:
    """Reads every stored embedding along with the artist facts used to describe a cluster."""
    schema = get_settings().db_schema
    with connection() as conn, conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT e.artist_id,
                   -- Name-free vector: clustering on the search embedding put
                   -- acts together because their stage names shared a word.
                   COALESCE(e.profile_embedding, e.embedding) AS embedding,
                   e.source_text,
                   a.stage_name,
                   COALESCE(a.rating, 3.0)      AS rating,
                   COALESCE(a.hourly_rate, 0)   AS hourly_rate,
                   COALESCE(
                       (SELECT ARRAY_AGG(DISTINCT c.name)
                        FROM artists_genres ag
                        JOIN category c ON c.id = ag.genres_id
                        WHERE ag.artist_id = a.id),
                       ARRAY[]::text[]
                   ) AS genres
            FROM {schema}.artist_embedding e
            JOIN artists a ON a.id = e.artist_id
            ORDER BY e.artist_id
            """
        )
        rows = cur.fetchall()

    if not rows:
        return np.empty((0, 0), dtype=np.float32), []

    vectors = np.asarray([row[1] for row in rows], dtype=np.float32)
    meta = [
        {
            "artist_id": row[0],
            "source_text": row[2],
            "stage_name": row[3],
            "rating": float(row[4]),
            "hourly_rate": float(row[5]),
            "genres": list(row[6] or []),
        }
        for row in rows
    ]
    return vectors, meta


def sweep(vectors: np.ndarray, k_min: int = K_MIN, k_max: int = K_MAX) -> list[SweepPoint]:
    """Scores every candidate k.

    Three indices rather than one because they disagree in useful ways.
    Silhouette rewards separation, Davies-Bouldin punishes clusters that bleed
    into each other, and Calinski-Harabasz favours compactness — a k that all
    three like is a partition worth trusting more than one that only wins on the
    metric it was selected by.

    Cosine is the metric throughout: the vectors are L2-normalised, so distance
    on the unit sphere is what the embedding space actually means.
    """
    points: list[SweepPoint] = []
    upper = min(k_max, len(vectors) - 1)

    for k in range(k_min, upper + 1):
        model = KMeans(n_clusters=k, n_init=10, random_state=42)
        labels = model.fit_predict(vectors)

        # A partition that produced a singleton is degenerate whatever the
        # indices say, so its size is recorded and weighed below.
        smallest = int(Counter(labels).most_common()[-1][1])

        points.append(
            SweepPoint(
                k=k,
                silhouette=float(silhouette_score(vectors, labels, metric="cosine")),
                davies_bouldin=float(davies_bouldin_score(vectors, labels)),
                calinski_harabasz=float(calinski_harabasz_score(vectors, labels)),
                inertia=float(model.inertia_),
                smallest_cluster=smallest,
            )
        )
        log.info(
            "k=%2d  silhouette=%.4f  davies_bouldin=%.4f  smallest=%d",
            k, points[-1].silhouette, points[-1].davies_bouldin, smallest,
        )

    return points


def choose_k(points: list[SweepPoint], min_cluster_size: int = 3) -> int:
    """Best silhouette among partitions that did not strand a handful of artists alone.

    A segment of one is not a segment; it is an outlier with a name. Filtering
    those out first stops the sweep from picking a k that scores well precisely
    because it isolated the weird rows.
    """
    usable = [p for p in points if p.smallest_cluster >= min_cluster_size]
    if not usable:
        usable = points
    return max(usable, key=lambda p: p.silhouette).k


def distinctive_terms(texts: list[str], background: Counter, limit: int = 6) -> list[str]:
    """Terms common in this cluster and uncommon everywhere else.

    Raw frequency would return the same filler for every segment. Scoring each
    term by how much its rate inside the cluster exceeds its rate across the
    whole catalogue is what makes the labels differ from one another.
    """
    inside = Counter()
    for text in texts:
        inside.update(w for w in WORD.findall(text.lower()) if w not in STOPWORDS and len(w) > 2)

    if not inside:
        return []

    inside_total = sum(inside.values())
    background_total = sum(background.values()) or 1

    scored = []
    for term, count in inside.items():
        if count < 2:
            continue
        inside_rate = count / inside_total
        background_rate = background[term] / background_total
        # +1e-6 keeps a term that appears nowhere else from dividing by zero.
        scored.append((inside_rate / (background_rate + 1e-6), term))

    scored.sort(reverse=True)
    return [term for _, term in scored[:limit]]


def profile_clusters(labels: np.ndarray, meta: list[dict]) -> list[SegmentProfile]:
    """Turns each cluster into something a person can read."""
    background = Counter()
    for row in meta:
        background.update(
            w for w in WORD.findall(row["source_text"].lower())
            if w not in STOPWORDS and len(w) > 2
        )

    grouped = {
        segment: [row for row, label in zip(meta, labels) if label == segment]
        for segment in sorted(set(labels.tolist()))
    }

    # Labels are handed out largest segment first, and each one has to be unique.
    # Two segments both called "Techno" is worse than useless in a picker — the
    # reader cannot tell which is which, and the distinction the model actually
    # found is exactly what the name should carry.
    order = sorted(grouped, key=lambda s: len(grouped[s]), reverse=True)
    taken: set[str] = set()
    chosen_labels: dict[int, str] = {}
    details: dict[int, tuple[list[str], list[str]]] = {}

    for segment in order:
        members = grouped[segment]

        genres = Counter()
        for row in members:
            genres.update(row["genres"])
        top_genres = [name for name, _ in genres.most_common(4)]
        terms = distinctive_terms([row["source_text"] for row in members], background)
        details[segment] = (top_genres, terms)

        label = None
        for candidate in top_genres:
            if candidate not in taken:
                label = candidate
                break

        if label is None:
            # Every genre here already names another segment, so qualify the
            # dominant one with the term that sets this group apart.
            head = top_genres[0] if top_genres else "Mixed"
            qualifier = next((t for t in terms if t.lower() not in head.lower()), None)
            label = f"{head} · {qualifier.title()}" if qualifier else f"{head} (variant)"

        if label in taken:
            label = f"{label} {segment}"

        taken.add(label)
        chosen_labels[segment] = label

    profiles: list[SegmentProfile] = []
    for segment in sorted(grouped):
        members = grouped[segment]
        top_genres, terms = details[segment]
        profiles.append(
            SegmentProfile(
                segment=int(segment),
                size=len(members),
                label=chosen_labels[segment],
                terms=terms,
                top_genres=top_genres,
                avg_rating=round(float(np.mean([r["rating"] for r in members])), 2),
                avg_hourly_rate=round(float(np.mean([r["hourly_rate"] for r in members])), 2),
                example_artists=[r["stage_name"] for r in members[:4]],
            )
        )

    return profiles


def persist(labels: np.ndarray, distances: np.ndarray, meta: list[dict]) -> None:
    """Writes assignments into this service's own schema."""
    schema = get_settings().db_schema
    with connection() as conn:
        conn.execute(
            f"""
            CREATE TABLE IF NOT EXISTS {schema}.artist_segment (
                artist_id   BIGINT PRIMARY KEY,
                segment     INT NOT NULL,
                distance    REAL NOT NULL,
                updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """
        )
        with conn.cursor() as cur:
            for row, label, distance in zip(meta, labels, distances):
                cur.execute(
                    f"""
                    INSERT INTO {schema}.artist_segment (artist_id, segment, distance, updated_at)
                    VALUES (%s, %s, %s, NOW())
                    ON CONFLICT (artist_id) DO UPDATE
                    SET segment = EXCLUDED.segment,
                        distance = EXCLUDED.distance,
                        updated_at = NOW()
                    """,
                    (row["artist_id"], int(label), float(distance)),
                )
        conn.commit()


def run(k: int | None = None) -> dict:
    settings = get_settings()

    vectors, meta = load_embeddings()
    if len(vectors) < K_MIN + 1:
        raise RuntimeError(
            f"Need at least {K_MIN + 1} embedded artists to segment, found {len(vectors)}. "
            "Run POST /embeddings/rebuild first."
        )

    points = sweep(vectors)
    chosen = k or choose_k(points)
    log.info("Chose k=%d", chosen)

    model = KMeans(n_clusters=chosen, n_init=10, random_state=42)
    labels = model.fit_predict(vectors)

    # Distance to the assigned centroid: how typical of its segment an artist is.
    # Serving uses it to pick representative members rather than arbitrary ones.
    distances = np.linalg.norm(vectors - model.cluster_centers_[labels], axis=1)

    profiles = profile_clusters(labels, meta)
    persist(labels, distances, meta)

    model_dir = Path(settings.model_dir)
    model_dir.mkdir(parents=True, exist_ok=True)

    import joblib
    joblib.dump(model, model_dir / "segments.joblib")

    chosen_point = next(p for p in points if p.k == chosen)
    report = {
        "artists": len(meta),
        "k": chosen,
        "chosen_by": "highest silhouette among partitions with no cluster smaller than 3",
        "metrics": asdict(chosen_point),
        "sweep": [asdict(p) for p in points],
        "segments": [asdict(p) for p in profiles],
    }
    (model_dir / "segments.json").write_text(json.dumps(report, indent=2))

    return report


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="Segment the artist catalogue")
    parser.add_argument("--k", type=int, default=None, help="Force a number of segments")
    args = parser.parse_args()

    report = run(args.k)

    print(f"\n{report['artists']} artists into {report['k']} segments")
    print(f"silhouette {report['metrics']['silhouette']:.4f}   "
          f"davies-bouldin {report['metrics']['davies_bouldin']:.4f}\n")
    for segment in report["segments"]:
        print(f"  [{segment['segment']}] {segment['label']:<22} n={segment['size']:<4} "
              f"rating {segment['avg_rating']:.2f}  terms: {', '.join(segment['terms'][:4])}")


if __name__ == "__main__":
    main()
