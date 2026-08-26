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
from app.features import FEATURE_COLUMNS, build_frame, genre_match, price_fit

log = logging.getLogger(__name__)

_booster = None
_meta: dict | None = None
_lock = threading.Lock()

# Rough distance tiers, used when a query names a city.
_PROVINCE = {
    "Kathmandu": "Bagmati", "Lalitpur": "Bagmati", "Bhaktapur": "Bagmati",
    "Chitwan": "Bagmati", "Pokhara": "Gandaki", "Butwal": "Lumbini",
    "Nepalgunj": "Lumbini", "Biratnagar": "Koshi", "Dharan": "Koshi",
    "Janakpur": "Madhesh",
}

_EVENT_GENRE_FIT = {
    "Wedding":      {"Folk": 1.0, "Pop": 0.9, "Classical": 0.9, "Jazz": 0.7},
    "Corporate":    {"Jazz": 1.0, "Classical": 0.9, "Pop": 0.7},
    "Festival":     {"Rock": 1.0, "Electronic": 0.9, "Pop": 0.9, "Hip-Hop": 0.8},
    "Birthday":     {"Pop": 1.0, "Hip-Hop": 0.8, "Rock": 0.8},
    "Club Night":   {"Electronic": 1.0, "Hip-Hop": 0.9, "Pop": 0.7},
    "Open Mic":     {"Folk": 1.0, "Blues": 0.9, "Hip-Hop": 0.8},
    "Charity Gala": {"Classical": 1.0, "Jazz": 0.9, "Folk": 0.8},
    "Restaurant":   {"Jazz": 1.0, "Blues": 0.9, "Folk": 0.9, "Classical": 0.8},
}
_NEUTRAL_EVENT_FIT = 0.5


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
    query_province = _PROVINCE.get(query_city.title())
    artist_province = _PROVINCE.get(artist_city.title())
    if query_province and query_province == artist_province:
        return 0.65
    return 0.2


def _normalised_similarity(candidates: list[dict]) -> list[float]:
    """Rescales cosine similarity to [0, 1] across the candidate set.

    Raw cosine values from a sentence embedder sit in a narrow, model-specific
    band — typically 0.4 to 0.8 here — while training saw the full range. What
    carries the signal is an artist's position relative to the others retrieved
    for the same query, so the pool is normalised rather than the absolute value
    trusted. This also keeps the feature stable if the embedding model changes.
    """
    values = [float(c.get("similarity") or 0.0) for c in candidates]
    if not values:
        return []
    low, high = min(values), max(values)
    if high - low < 1e-6:
        return [0.5] * len(values)
    return [(v - low) / (high - low) for v in values]


def build_candidate_frame(candidates: list[dict], *, city: str | None,
                          budget_per_hour: float | None, event_type: str | None,
                          wanted_genre: str | None) -> pd.DataFrame:
    """Turns retrieved artists plus the query into the model's feature frame."""
    budget = budget_per_hour or 0.0
    similarities = _normalised_similarity(candidates)
    rows = []

    # NaN, not zero, when the organizer stated nothing. LightGBM was trained with
    # these withheld the same way and routes missing values down their own
    # branch; zero would read as "definitely a bad match".
    genre_known = bool(wanted_genre)
    budget_known = bool(budget_per_hour)

    for artist, similarity in zip(candidates, similarities):
        subs = [s for s in (artist.get("sub_genres") or []) if s]
        parents = [p for p in (artist.get("parent_genres") or []) if p]
        parent = parents[0] if parents else None

        fit = _NEUTRAL_EVENT_FIT
        if event_type:
            fit = _EVENT_GENRE_FIT.get(event_type, {}).get(parent, _NEUTRAL_EVENT_FIT)

        rows.append({
            "query_id": 0,
            "artist_id": artist["artist_id"],
            "genre_match": (genre_match(wanted_genre, wanted_genre, subs, parent)
                            if genre_known else float("nan")),
            "text_similarity": similarity,
            "price_fit": (price_fit(budget, artist["hourly_rate"])
                          if budget_known else float("nan")),
            "rating_norm": (float(artist["rating"]) - 1.0) / 4.0,
            "location_match": _location_match(city, artist.get("city")),
            "experience": min(1.0, np.log1p(artist["completed_bookings"]) / np.log1p(120)),
            "event_fit": fit,
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
    return (
        0.24 * genre
        + 0.16 * frame["text_similarity"]
        + 0.18 * price
        + 0.13 * frame["rating_norm"]
        + 0.13 * frame["location_match"]
        + 0.08 * frame["experience"]
        + 0.04 * frame["event_fit"]
        + 0.04 * frame["responsiveness"]
    ).to_numpy()
