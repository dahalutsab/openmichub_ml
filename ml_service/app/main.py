"""OpenMicHub ML service.

Two capabilities, behind one small API:

  semantic search   pgvector retrieval over artist embeddings, so a query in
                    plain language finds artists whose bios never use those words
  ranking           a LightGBM LambdaRank model that reorders the retrieved
                    candidates by predicted fit for the specific request
  segmentation      k-means over the same embeddings, grouping the catalogue
                    into segments and answering "more artists like this one"
  personalisation   a taste profile built from one person's own searches,
                    profile views and past bookings - an account's, or a
                    signed-out browser's - which adjusts the ranking for them
  platform signals  what everyone's behaviour says: acts chosen together,
                    acts in demand this month, and how often each has been shown
                    (app/signals.py), folded in last with variety (app/blend.py)

Retrieval and ranking are deliberately separate. Vector similarity is good at
"is this the right kind of artist" and blind to whether they are affordable,
nearby or any good; the ranker weighs those against each other. Running them in
sequence is standard practice and keeps each part simple enough to reason about.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

import numpy as np
from fastapi import FastAPI, HTTPException

from app import blend, personalization, ranker, search, segments, signals
from app.config import get_settings
from app.db import connection, init_schema
from app.embedder import embed_one
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


def _to_hits(candidates: list[dict], scores, order: list[int],
             reasons: list[list[str]] | None, personalized: bool) -> list[ArtistHit]:
    """Hits in the order given. The order is decided upstream, not re-sorted here:
    on a browse surface variety has already moved some acts off pure score order."""
    hits = []
    for index in order:
        artist, score = candidates[index], scores[index]
        hits.append(ArtistHit(
            artist_id=artist["artist_id"],
            slug=artist.get("slug"),
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
            # True for every hit in a personalised list, including the ones the
            # profile had nothing particular to say about — the ordering around
            # them still moved.
            personalized=personalized,
            reasons=(reasons[index] if reasons else [])[:2],
        ))
    return hits


def _strategy(personalized: bool) -> str:
    """What produced this ordering, said plainly enough to print in the UI."""
    base = ranker.model_info()["strategy"]
    return f"{base} + your history" if personalized else base


def _viewer(user_id: int | None, visitor_id: str | None) -> str | None:
    if user_id:
        return f"u{user_id}"
    return f"v{visitor_id}" if visitor_id else None


def _usable(profile) -> bool:
    return profile is not None and profile.usable


def _finish(candidates: list[dict], raw_scores, surface: str, profile, alpha: float,
            limit: int, viewer: str | None, diversify: bool):
    """Everything after the model has scored: person, platform, variety, order."""
    platform = signals.current()
    personalized = _usable(profile)

    reasons = None
    if personalized:
        co = platform.co_choice(profile.seeds())
        scores, reasons = personalization.rerank(
            profile, candidates, raw_scores, alpha, co, platform.co_scale)
    else:
        scores = blend.to_unit_scale(raw_scores)

    scores, reasons = blend.platform_blend(
        candidates, scores, surface, platform,
        skips=personalization.skip_strength(profile, candidates),
        reasons=reasons, viewer=viewer)

    if diversify:
        top = [int(candidates[i]["artist_id"]) for i in np.argsort(-scores)[: limit * 3]]
        order = blend.diversify(candidates, scores, personalization._embeddings(top),
                                limit, personalization._taste_band())
        if personalized:
            order = blend.limit_familiar(order, candidates, set(profile.seeds()), limit)
    else:
        order = list(np.argsort(-scores, kind="stable"))

    return _to_hits(candidates, scores, order[:limit], reasons, personalized), personalized


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
    visitor_id = None if request.user_id else request.visitor_id
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

    # Retrieval stays untouched here. Someone who has typed a sentence has just
    # said what they want, and their history is context for ordering the results
    # rather than grounds for returning different ones. No variety pass either:
    # a search for jazz trios should come back as jazz trios.
    profile = personalization.profile_for(request.user_id, visitor_id)
    hits, personalized = _finish(
        candidates, scores, blend.SURFACE_SEARCH, profile, settings.taste_alpha_search,
        request.limit, _viewer(request.user_id, visitor_id), diversify=False)

    # Folded in after this search was ranked, not before: the query already
    # decides these results, and what it shapes is where they go next.
    personalization.note_search(request.user_id, request.query, visitor_id)

    return SearchResponse(query=request.query, total=len(hits),
                          strategy=_strategy(personalized), personalized=personalized,
                          results=hits)


@app.post("/recommend", response_model=SearchResponse)
def recommend(request: RecommendRequest) -> SearchResponse:
    """Rank the catalogue for a set of requirements the organizer did not type.

    Browse filters are still a statement of intent, so they are turned back into
    a query and run through the same retrieve-then-rank path as `/search`.

    With nothing stated at all this is the front page, and it is where the
    platform's own behaviour matters most: who is in demand this month, what this
    visitor keeps passing over, and not showing eight versions of the same act.
    """
    settings = get_settings()
    visitor_id = None if request.user_id else request.visitor_id
    pseudo_query = search.requirement_query(
        request.genre, request.event_type, request.city)
    profile = personalization.profile_for(request.user_id, visitor_id)
    platform = signals.current()

    if _usable(profile) and (profile.vector is not None or profile.intent_vector is not None):
        # A browse has no words, so re-ranking alone would only reorder whatever
        # generic pool retrieval happened to return. Here the profile is allowed
        # into retrieval itself, from three sources: what this person is asking
        # for now (stated filters and recent searches, both query text), what
        # they have engaged with (a centre of profile vectors), and what people
        # who chose the same acts went on to choose (co-choice). The reported
        # similarity is still measured against the filters alone, so the ranker's
        # `text_similarity` keeps meaning what it was calibrated to mean.
        pool = settings.candidate_pool_size
        query_vector = embed_one(pseudo_query) if pseudo_query else None

        co = platform.co_choice(profile.seeds())
        co_slots = round(pool * settings.co_retrieval_share) if co else 0
        co_ids = [artist for artist, _ in sorted(co.items(), key=lambda kv: -kv[1][0])]
        from_co = search.candidates_by_id(co_ids[: co_slots * 2], city=request.city,
                                          similarity_vector=query_vector)[:co_slots]

        from_vectors = search.merged_candidates(
            personalization.query_space_vector(profile, query_vector),
            profile.vector,
            taste_share=settings.taste_retrieval_share,
            limit=pool - len(from_co), city=request.city,
            similarity_vector=query_vector)
        candidates = search.interleave(from_vectors, from_co)
    elif pseudo_query:
        candidates = search.vector_candidates(
            pseudo_query, limit=settings.candidate_pool_size, city=request.city)
    else:
        # Nothing stated but perhaps a location, which is a filter rather than a
        # taste. Similarity stays absent, and the model ranks on the rest.
        candidates = fetch_artists()
        for candidate in candidates:
            candidate["similarity"] = None
        if request.city:
            wanted = request.city.strip().lower()
            candidates = [a for a in candidates
                          if (a.get("city") or "").strip().lower() == wanted]
        # Past a certain catalogue size, scoring every act for a front page is
        # wasted work: keep the ones in demand and the best reviewed.
        cap = settings.candidate_pool_size * 5
        if len(candidates) > cap:
            candidates.sort(key=lambda a: (-platform.demand.get(a["artist_id"], 0.0),
                                           -float(a.get("rating_smoothed", a["rating"]))))
            candidates = candidates[:cap]

    if not candidates:
        return SearchResponse(total=0, strategy=_strategy(_usable(profile)),
                              personalized=_usable(profile), results=[])

    frame = ranker.build_candidate_frame(
        candidates, city=request.city, budget_per_hour=request.budget_per_hour,
        event_type=request.event_type, wanted_genre=request.genre,
    )
    scores = ranker.score(frame)

    # A browse that states nothing leaves the ranker with no feature about this
    # request to order by, so the person's own history takes the larger share,
    # and platform demand weighs most. Say what the event is and both recede.
    stated_something = bool(request.genre or request.event_type or request.budget_per_hour)
    alpha = (settings.taste_alpha_browse if stated_something
             else settings.taste_alpha_browse_unfiltered)
    surface = blend.SURFACE_BROWSE if stated_something else blend.SURFACE_HOME

    hits, personalized = _finish(
        candidates, scores, surface, profile, alpha, request.limit,
        _viewer(request.user_id, visitor_id), diversify=True)

    return SearchResponse(total=len(hits), strategy=_strategy(personalized),
                          personalized=personalized, results=hits)


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


@app.get("/users/{user_id}/taste")
def user_taste(user_id: int) -> dict:
    """What the service believes about one person, and whether it is enough to use.

    Built fresh rather than read from the cache, and returned even when it is too
    thin to personalise from — the point is to be able to check the input to a
    ranking rather than infer it from the ranking.
    """
    return personalization.build_profile(user_id=user_id).describe()


@app.get("/visitors/{visitor_id}/taste")
def visitor_taste(visitor_id: str) -> dict:
    """The same, for a browser that has not signed in."""
    return personalization.build_profile(visitor_id=visitor_id[:64]).describe()


@app.post("/users/{user_id}/forget")
def forget_user(user_id: int) -> dict:
    """Drops one account's cached profile, e.g. after a visitor's history moved onto it."""
    personalization.forget(user_id=user_id)
    return {"userId": user_id, "forgotten": True}


@app.get("/signals")
def platform_signals(refresh: bool = False) -> dict:
    """What the platform-wide signals currently hold. `refresh` rebuilds them first."""
    if refresh:
        signals.reset()
    return signals.current().describe()


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


@app.get("/artists/{artist_id}/also-chosen")
def artist_also_chosen(artist_id: int, limit: int = 6) -> dict:
    """Artists the people who chose this one also chose - booked, or opened.

    A different question from `/similar`. Similar is "whose profile reads the
    same"; this is "who ends up on the same shortlist", which crosses genres
    whenever real bookings do. `similarity` is the support-shrunk cosine, 0-1,
    comparable across artists.
    """
    limit = max(1, min(limit, 24))
    platform = signals.current()
    neighbours = platform.similar(artist_id, limit)
    if not neighbours:
        return {"artistId": artist_id, "alsoChosen": []}

    artists = {a["artist_id"]: a for a in fetch_artists([other for other, _ in neighbours])}
    return {
        "artistId": artist_id,
        "alsoChosen": [
            {
                "artistId": other,
                "slug": artists[other].get("slug"),
                "stageName": artists[other]["stage_name"],
                "fullName": artists[other].get("full_name"),
                "profileImage": artists[other].get("profile_image"),
                "rating": float(artists[other]["rating"]),
                "hourlyRate": float(artists[other]["hourly_rate"]),
                "city": artists[other].get("city"),
                "similarity": round(similarity, 4),
            }
            for other, similarity in neighbours if other in artists
        ],
    }
