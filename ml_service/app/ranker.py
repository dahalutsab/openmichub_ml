"""Scoring: reorders retrieved candidates with the trained model.

Falls back to the heuristic the model was measured against when no model has
been trained yet, so search works from a cold start and simply gets better once
a model exists.
"""

from __future__ import annotations

import json
import logging
import threading
from pathlib import Path

import numpy as np
import pandas as pd

from app.config import get_settings
from app.features import (
    FEATURE_COLUMNS, build_frame, calibrate_similarity, genre_match, price_fit,
)
from app.repository import genre_taxonomy
from app.taxonomy import PROVINCE, event_fit

log = logging.getLogger(__name__)

_booster = None
_meta: dict | None = None
_lock = threading.Lock()


def infer_genre(query_text: str | None, vocabulary: list[str]) -> str | None:
    """Picks a genre out of the query text, if the organizer named one.

    Someone searching "jazz trio for a corporate dinner" has stated a genre even
    though they did not select one from a dropdown. Recovering it matters: the
    ranker leans heavily on genre when it is known, and falls back to text
    similarity when it is not, so handing it a genre it could have read from the
    query is worth a plain substring pass.

    Longest match wins, so "Acoustic Folk" beats "Folk".
    """
    if not query_text:
        return None
    haystack = query_text.lower()
    matches = [name for name in vocabulary if name and name.lower() in haystack]
    return max(matches, key=len) if matches else None


def _model_path() -> Path:
    settings = get_settings()
    return Path(settings.model_dir) / settings.ranker_filename


def load_model(force: bool = False):
    """Loads the trained booster if one has been written."""
    global _booster, _meta
    if _booster is not None and not force:
        return _booster

    with _lock:
        path = _model_path()
        if not path.exists():
            log.warning("No trained ranker at %s - falling back to the heuristic", path)
            _booster = None
            return None

        import lightgbm as lgb

        _booster = lgb.Booster(model_file=str(path))
        meta_path = path.parent / get_settings().ranker_meta_filename
        _meta = json.loads(meta_path.read_text()) if meta_path.exists() else None
        log.info("Loaded ranker from %s", path)
        return _booster


def model_info() -> dict:
    load_model()
    if _booster is None:
        return {"trained": False, "strategy": "heuristic fallback"}
    return {
        "trained": True,
        "strategy": "lightgbm lambdarank",
        "trained_at": (_meta or {}).get("trained_at"),
        "best_iteration": (_meta or {}).get("best_iteration"),
        "test_ndcg@10": ((_meta or {}).get("metrics", {})
                         .get("model (LambdaRank)", {}).get("ndcg@10")),
        "features": (_meta or {}).get("features", FEATURE_COLUMNS),
    }


def _location_match(query_city: str | None, artist_city: str | None) -> float:
    if not query_city or not artist_city:
        return 0.35
    if query_city.strip().lower() == artist_city.strip().lower():
        return 1.0
    query_province = PROVINCE.get(query_city.title())
    artist_province = PROVINCE.get(artist_city.title())
    if query_province and query_province == artist_province:
        return 0.65
    return 0.2


def _similarity_features(candidates: list[dict]) -> list[float]:
    """Calibrated text similarity per candidate, or NaN when there was no query.

    A browse or "similar artists" surface has no query text, so there is no
    observation to calibrate. That is a missing feature, not a mediocre one, and
    it is reported as NaN so LightGBM routes it down the branch it learned for
    withheld signals. Handing the model a flat 0.5 instead — which is what this
    used to do — pinned the feature carrying most of its gain to a constant, and
    the trees collapsed: a hundred artists came back on thirty-six distinct
    scores, the top ten sharing two.
    """
    values = []
    for candidate in candidates:
        similarity = candidate.get("similarity")
        values.append(float("nan") if similarity is None
                      else calibrate_similarity(float(similarity)))
    return values


def build_candidate_frame(candidates: list[dict], *, city: str | None,
                          budget_per_hour: float | None, event_type: str | None,
                          wanted_genre: str | None) -> pd.DataFrame:
    """Turns retrieved artists plus the query into the model's feature frame."""
    budget = budget_per_hour or 0.0
    similarities = _similarity_features(candidates)
    rows = []

    # NaN, not zero, when the organizer stated nothing. LightGBM was trained with
    # these withheld the same way and routes missing values down their own
    # branch; zero would read as "definitely a bad match".
    genre_known = bool(wanted_genre)
    budget_known = bool(budget_per_hour)

    # One name comes in; the model wants the sub-genre and the parent separately.
    wanted_sub, wanted_parent = genre_taxonomy().resolve(wanted_genre)

    for artist, similarity in zip(candidates, similarities):
        subs = [s for s in (artist.get("sub_genres") or []) if s]
        parents = [p for p in (artist.get("parent_genres") or []) if p]

        rows.append({
            "query_id": 0,
            "artist_id": artist["artist_id"],
            "genre_match": (genre_match(wanted_sub, wanted_parent, subs, parents)
                            if genre_known else float("nan")),
            "text_similarity": similarity,
            "price_fit": (price_fit(budget, artist["hourly_rate"])
                          if budget_known else float("nan")),
            "rating_norm": (float(artist["rating"]) - 1.0) / 4.0,
            "location_match": _location_match(city, artist.get("city")),
            "experience": min(1.0, np.log1p(artist["completed_bookings"]) / np.log1p(120)),
            "event_fit": event_fit(event_type, parents),
            "responsiveness": float(artist["response_rate"]),
            "hourly_rate": float(artist["hourly_rate"]),
            "completed_bookings": int(artist["completed_bookings"]),
            "budget_per_hour": budget if budget else float(artist["hourly_rate"]),
        })

    return build_frame(pd.DataFrame(rows))


def score(frame: pd.DataFrame) -> np.ndarray:
    """Model score if one is trained, otherwise the weighted heuristic."""
    booster = load_model()
    if booster is not None:
        return booster.predict(frame[FEATURE_COLUMNS].to_numpy(dtype=np.float32))

    # Mirrors the utility weights the model was trained against, so the cold
    # start behaves like a weaker version of the trained system rather than
    # something different.
    # Unstated signals contribute their neutral value rather than dragging the
    # sum to NaN.
    genre = frame["genre_match"].fillna(0.4)
    price = frame["price_fit"].fillna(0.5)
    similarity = frame["text_similarity"].fillna(0.5)
    return (
        0.24 * genre
        + 0.16 * similarity
        + 0.18 * price
        + 0.13 * frame["rating_norm"]
        + 0.13 * frame["location_match"]
        + 0.08 * frame["experience"]
        + 0.04 * frame["event_fit"]
        + 0.04 * frame["responsiveness"]
    ).to_numpy()
