"""Feature construction, shared by training and inference.

Both paths must build features identically or the model sees a different world at
serving time than it was trained on. Keeping the column list and the encoders in
one module is what prevents that drift.
"""

from __future__ import annotations

import math

import numpy as np
import pandas as pd

from app.config import get_settings

# Order matters: LightGBM binds features by position.
FEATURE_COLUMNS = [
    "genre_match",
    "text_similarity",
    "price_fit",
    "rating_norm",
    "location_match",
    "experience",
    "event_fit",
    "responsiveness",
    "rate_to_budget_ratio",
    "log_hourly_rate",
    "log_completed_bookings",
    "same_city",
    "same_province",
]

CATEGORICAL_COLUMNS: list[str] = []


def price_fit(budget_per_hour: float, hourly_rate: float) -> float:
    """Peaks a little under budget; over budget falls away sharply."""
    if budget_per_hour <= 0:
        return 0.0
    ratio = hourly_rate / budget_per_hour
    if ratio > 1.0:
        return max(0.0, 1.0 - 2.2 * (ratio - 1.0))
    return math.exp(-((ratio - 0.85) ** 2) / (2 * 0.28**2))


def genre_match(wanted_sub: str | None, wanted_parent: str | None,
                artist_subs: list[str] | None,
                artist_parents: list[str] | None) -> float:
    """Graded genre agreement: exact sub-genre, same parent, or neither.

    Takes every parent the artist is filed under rather than one. An act listed
    under both Jazz and Rock matches a Jazz request; picking a single parent off
    an alphabetised array made that depend on which name sorted first.
    """
    if wanted_sub and wanted_sub in (artist_subs or []):
        return 1.0
    if wanted_parent and wanted_parent in (artist_parents or []):
        return 0.6
    return 0.0


def calibrate_similarity(cosine: float) -> float:
    """Maps a raw cosine onto the 0-1 scale the model was trained against.

    Two things had to be fixed here at once.

    The old code min-max normalised the retrieved pool. Because retrieval hands
    candidates back already sorted by cosine, that made the top hit exactly 1.0
    and the last exactly 0.0 on *every* search — turning the model's most
    important feature into a restatement of the retrieval order it was supposed
    to be reconsidering. It also stretched a band about 0.08 wide across the
    full range, amplifying roughly 0.02 sd of embedding noise by ten.

    A fixed affine map fixes both: the same cosine now means the same thing on
    every query, and an artist that is genuinely a poor fit scores low instead
    of being promoted for topping a weak pool. The band is measured, not
    guessed - see `training/calibrate.py`.
    """
    settings = get_settings()
    low, high = settings.similarity_cos_low, settings.similarity_cos_high
    span = high - low
    if span <= 1e-9:
        return 0.5
    return float(min(1.0, max(0.0, (cosine - low) / span)))


def build_frame(pairs: pd.DataFrame) -> pd.DataFrame:
    """Derives the columns the model consumes from the raw signal columns."""
    frame = pairs.copy()

    frame["rate_to_budget_ratio"] = np.where(
        frame["budget_per_hour"] > 0,
        frame.get("hourly_rate", frame["budget_per_hour"] * frame["price_fit"].clip(lower=0.01))
        / frame["budget_per_hour"],
        0.0,
    )
    if "hourly_rate" in frame:
        frame["log_hourly_rate"] = np.log1p(frame["hourly_rate"])
    else:
        # Reconstructed from the ratio when the raw rate was not carried through.
        frame["log_hourly_rate"] = np.log1p(frame["rate_to_budget_ratio"] * frame["budget_per_hour"])

    if "completed_bookings" in frame:
        frame["log_completed_bookings"] = np.log1p(frame["completed_bookings"])
    else:
        frame["log_completed_bookings"] = frame["experience"] * math.log1p(120)

    if "text_similarity" not in frame:
        frame["text_similarity"] = 0.5

    frame["same_city"] = (frame["location_match"] >= 0.999).astype(int)
    frame["same_province"] = (frame["location_match"] >= 0.649).astype(int)

    for column in FEATURE_COLUMNS:
        if column not in frame:
            frame[column] = np.nan

    # NaN is meaningful here and must survive: it is how "not stated" is
    # expressed, and LightGBM handles it natively.
    return frame


def to_matrix(pairs: pd.DataFrame) -> np.ndarray:
    return build_frame(pairs)[FEATURE_COLUMNS].to_numpy(dtype=np.float32)
