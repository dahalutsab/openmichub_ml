"""OpenMicHub ML service.

Two capabilities, behind one small API:

  semantic search   pgvector retrieval over artist embeddings, so a query in
                    plain language finds artists whose bios never use those words
  ranking           a LightGBM LambdaRank model that reorders the retrieved
                    candidates by predicted fit for the specific request
  segmentation      k-means over the same embeddings, grouping the catalogue
                    into segments and answering "more artists like this one"

Retrieval and ranking are deliberately separate. Vector similarity is good at
"is this the right kind of artist" and blind to whether they are affordable,
nearby or any good; the ranker weighs those against each other. Running them in
sequence is standard practice and keeps each part simple enough to reason about.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from app import ranker, search, segments
from app.config import get_settings
from app.db import connection, init_schema
from app.repository import fetch_artists, genre_vocabulary
from app.schemas import (
    ArtistHit, HealthResponse, RebuildResponse, RecommendRequest,
    SearchRequest, SearchResponse, TrainRequest,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(name)s: %(message)s")
log = logging.getLogger("mlservice")


@asynccontextmanager
async def lifespan(_: FastAPI):
    try:
        init_schema()
    except Exception:
        # The API may still be creating its own tables. Health reports the
        # failure; the schema is retried on first use.
        log.exception("Could not prepare the vector schema at startup")
    ranker.load_model()
    segments.load()
    yield


app = FastAPI(
    title="OpenMicHub ML",
    version="1.0.0",
    description="Semantic artist search and learned ranking.",
    lifespan=lifespan,
)


def _to_hits(candidates: list[dict], scores) -> list[ArtistHit]:
    hits = []
    for artist, score in zip(candidates, scores):
        hits.append(ArtistHit(
            artist_id=artist["artist_id"],
            stage_name=artist["stage_name"],
            full_name=artist.get("full_name"),
            bio=artist.get("bio") or None,
            city=artist.get("city") or None,
            hourly_rate=float(artist["hourly_rate"]),
            rating=float(artist["rating"]),
            completed_bookings=int(artist["completed_bookings"]),
            sub_genres=[s for s in (artist.get("sub_genres") or []) if s],
            parent_genres=[p for p in (artist.get("parent_genres") or []) if p],
            profile_image=artist.get("profile_image"),
            score=float(score),
            similarity=artist.get("similarity"),
        ))
    hits.sort(key=lambda hit: -hit.score)
    return hits


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    database, embeddings = "down", None
    try:
        with connection() as conn:
            conn.execute("SELECT 1")
        database = "up"
        try:
            embeddings = search.embedding_count()
        except Exception:
            embeddings = None
    except Exception as exc:
        log.warning("Database unreachable: %s", exc)

    return HealthResponse(
        status="ok" if database == "up" else "degraded",
        database=database,
        embeddings=embeddings,
        ranker=ranker.model_info(),
    )


@app.post("/search", response_model=SearchResponse)
def semantic_search(request: SearchRequest) -> SearchResponse:
    """Retrieve by meaning, then rank by fit."""
    settings = get_settings()
    candidates = search.vector_candidates(
        request.query,
        limit=settings.candidate_pool_size,
        city=request.city,
        max_hourly_rate=None,
    )
    if not candidates:
        return SearchResponse(query=request.query, total=0,
                              strategy=ranker.model_info()["strategy"], results=[])

    # An explicit filter wins; otherwise try to read a genre out of the query.
    wanted_genre = request.genre or ranker.infer_genre(request.query, genre_vocabulary())

    frame = ranker.build_candidate_frame(
        candidates, city=request.city, budget_per_hour=request.budget_per_hour,
        event_type=request.event_type, wanted_genre=wanted_genre,
    )
    scores = ranker.score(frame)
    hits = _to_hits(candidates, scores)[: request.limit]
    return SearchResponse(query=request.query, total=len(hits),
                          strategy=ranker.model_info()["strategy"], results=hits)


@app.post("/recommend", response_model=SearchResponse)
def recommend(request: RecommendRequest) -> SearchResponse:
    """Rank the catalogue for a set of requirements, with no text query."""
    candidates = fetch_artists()
    # No text query, so no similarity signal: leave it neutral for everyone
    # rather than letting an absent feature skew the ordering.
    for candidate in candidates:
        candidate["similarity"] = None
    if request.city:
        wanted = request.city.strip().lower()
        candidates = [a for a in candidates if (a.get("city") or "").strip().lower() == wanted]
    if not candidates:
        return SearchResponse(total=0, strategy=ranker.model_info()["strategy"], results=[])

    frame = ranker.build_candidate_frame(
        candidates, city=request.city, budget_per_hour=request.budget_per_hour,
        event_type=request.event_type, wanted_genre=request.genre,
    )
    scores = ranker.score(frame)
    hits = _to_hits(candidates, scores)[: request.limit]
    return SearchResponse(total=len(hits), strategy=ranker.model_info()["strategy"], results=hits)


@app.post("/embeddings/rebuild", response_model=RebuildResponse)
def rebuild_embeddings() -> RebuildResponse:
    """Re-embeds the catalogue. Run after importing or editing artists."""
    try:
        init_schema()
        return RebuildResponse(embedded=search.rebuild_embeddings())
    except Exception as exc:
        log.exception("Rebuild failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/train")
def train_model(request: TrainRequest) -> dict:
    """Retrains the ranker and reloads it in place.

    Synchronous on purpose: training takes seconds at this scale, and a job queue
    would be machinery without a payoff. Move it to a worker if the dataset grows.
    """
    from pathlib import Path

    from training.generate import GeneratorConfig
    from training.train import train as run_training

    cfg = GeneratorConfig(
        n_artists=request.artists, n_queries=request.queries,
        candidates_per_query=request.candidates_per_query,
        noise_sd=request.noise_sd, seed=request.seed,
    )
    try:
        report = run_training(cfg, Path(get_settings().model_dir), num_rounds=request.rounds)
    except Exception as exc:
        log.exception("Training failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    ranker.load_model(force=True)
    return {
        "trained_at": report["trained_at"],
        "best_iteration": report["best_iteration"],
        "dataset": report["dataset"],
        "metrics": report["metrics"],
        "feature_importance_share": report["feature_importance_share"],
    }


@app.get("/model")
def model_details() -> dict:
    """Full training report, for the write-up and for spot checks."""
    import json
    from pathlib import Path

    settings = get_settings()
    meta_path = Path(settings.model_dir) / settings.ranker_meta_filename
    if not meta_path.exists():
        raise HTTPException(status_code=404, detail="No model has been trained yet.")
    return json.loads(meta_path.read_text())


# --------------------------------------------------------------------------- #
# Segmentation
# --------------------------------------------------------------------------- #

@app.post("/segments/train")
def train_segments(k: int | None = None) -> dict:
    """Refits the segmentation and hot-reloads it.

    `k` is chosen automatically when not given. Pass one to override the sweep.
    """
    from training.segment import run

    try:
        report = run(k)
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc

    segments.reload_model()
    return {
        "artists": report["artists"],
        "k": report["k"],
        "chosen_by": report["chosen_by"],
        "metrics": report["metrics"],
        "segments": [
            {"segment": s["segment"], "label": s["label"], "size": s["size"]}
            for s in report["segments"]
        ],
    }


@app.get("/segments")
def list_segments() -> dict:
    """Segments with live membership counts and representative artists."""
    if not segments.is_ready():
        raise HTTPException(
            status_code=404,
            detail="Segmentation has not been run. POST /segments/train first.",
        )
    return {"segments": segments.overview()}


@app.get("/segments/report")
def segments_report() -> dict:
    """Full report, including the metric sweep across every k considered."""
    return segments.report()


@app.get("/artists/{artist_id}/segment")
def artist_segment(artist_id: int) -> dict:
    result = segments.segment_of(artist_id)
    if result is None:
        raise HTTPException(status_code=404, detail="This artist has not been segmented.")
    return result


@app.get("/artists/{artist_id}/similar")
def artist_similar(artist_id: int, limit: int = 6) -> dict:
    """Nearest neighbours by embedding. Works whether or not segmentation has run."""
    limit = max(1, min(limit, 24))
    return {"artistId": artist_id, "similar": segments.similar_artists(artist_id, limit)}
