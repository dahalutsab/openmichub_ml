"""Tests for the taste profile and the re-ranking it drives.

None of these touch Postgres. Everything that reads the database is behind two
functions — `_rows` and `_embeddings` — and both are replaced here, which leaves
the part worth testing: how history turns into weights, and how those weights
move a ranking.

    docker compose exec ml python -m pytest tests -q
"""

from __future__ import annotations

from datetime import datetime, timedelta

import numpy as np
import pytest

from app import personalization
from app.config import get_settings
from app.personalization import TasteProfile


@pytest.fixture(autouse=True)
def _clear_cache():
    personalization.forget()
    yield
    personalization.forget()


def unit(*values: float) -> np.ndarray:
    vector = np.array(values, dtype=np.float32)
    return vector / np.linalg.norm(vector)


def artist(artist_id: int, *, genres=("Jazz",), parents=("Jazz",),
           city="Kathmandu", rate=4000.0) -> dict:
    return {
        "artist_id": artist_id,
        "stage_name": f"Act {artist_id}",
        "sub_genres": list(genres),
        "parent_genres": list(parents),
        "city": city,
        "hourly_rate": rate,
        "rating": 4.0,
        "completed_bookings": 5,
    }


# --------------------------------------------------------------------------- #
# Decay
# --------------------------------------------------------------------------- #

def test_an_event_today_counts_in_full():
    assert personalization._decay(0.0) == pytest.approx(1.0)


def test_weight_halves_over_the_half_life():
    half_life = get_settings().taste_half_life_days
    assert personalization._decay(half_life) == pytest.approx(0.5)
    assert personalization._decay(2 * half_life) == pytest.approx(0.25)


def test_an_undated_row_is_treated_as_old_rather_than_new():
    now = datetime(2026, 8, 29)
    assert personalization._age_days(None, now) == get_settings().taste_window_days
    assert personalization._age_days(now - timedelta(days=3), now) == pytest.approx(3.0)


# --------------------------------------------------------------------------- #
# Building a profile
# --------------------------------------------------------------------------- #

def _install_history(monkeypatch, interactions, bookings, artists, vectors=None):
    def rows(sql, params):
        return interactions if "FROM user_interaction" in sql else bookings

    monkeypatch.setattr(personalization, "_rows", rows)
    monkeypatch.setattr(personalization, "fetch_artists",
                        lambda ids: [a for a in artists if a["artist_id"] in set(ids)])
    monkeypatch.setattr(personalization, "_embeddings", lambda ids: dict(vectors or {}))
    monkeypatch.setattr(personalization, "embed",
                        lambda texts: np.vstack([unit(0.0, 1.0, 0.0)] * len(texts)))


def test_a_history_of_jazz_reads_as_a_preference_for_jazz(monkeypatch):
    now = datetime.now()
    interactions = [
        ("PROFILE_VIEW", 1, None, None, None, None, None, now - timedelta(days=1)),
        ("PROFILE_VIEW", 1, None, None, None, None, None, now - timedelta(days=2)),
        ("SEARCH", None, "jazz trio for a dinner", "Jazz", "Kathmandu", None, 5000.0,
         now - timedelta(days=1)),
    ]
    bookings = [(2, "CONFIRMED", "Wedding", 6000.0, now - timedelta(days=10))]
    artists = [artist(1), artist(2, genres=("Bebop",), parents=("Jazz",))]

    _install_history(monkeypatch, interactions, bookings, artists,
                     vectors={1: unit(1.0, 0.0, 0.0), 2: unit(1.0, 0.1, 0.0)})

    profile = personalization.build_profile(user_id=7)

    assert profile.events == 4
    assert profile.usable
    assert profile.genre_affinity["Jazz"] == 1.0
    assert profile.booked == {2: pytest.approx(1.0 * personalization._decay(10), rel=1e-3)}
    assert set(profile.viewed) == {1}
    assert profile.city_affinity["Kathmandu"] == 1.0
    # Somewhere between the rates of the acts they engaged with (4,000) and the
    # budget they typed (5,000) and the booking that carried the most weight
    # (6,000) - a weighted average of the evidence, not any one number in it.
    assert 4000.0 < profile.typical_rate < 6000.0
    assert profile.vector is not None
    assert np.linalg.norm(profile.vector) == pytest.approx(1.0, rel=1e-5)


def test_a_single_glance_is_not_a_taste_profile(monkeypatch):
    now = datetime.now()
    _install_history(
        monkeypatch,
        [("PROFILE_VIEW", 1, None, None, None, None, None, now - timedelta(days=1))],
        [],
        [artist(1)],
        vectors={1: unit(1.0, 0.0, 0.0)},
    )

    profile = personalization.build_profile(user_id=7)

    # One view is worth 0.45 before decay, below the floor: read, but not used.
    assert profile.events == 1
    assert not profile.usable
    assert personalization.profile_for(7) is None


def test_an_anonymous_caller_has_no_profile():
    assert personalization.profile_for(None) is None
    assert personalization.profile_for(0) is None


def test_a_database_failure_leaves_discovery_unpersonalised(monkeypatch):
    def explode(sql, params):
        raise RuntimeError("relation \"user_interaction\" does not exist")

    monkeypatch.setattr(personalization, "_rows", explode)

    profile = personalization.build_profile(user_id=7)

    assert not profile.usable
    assert profile.events == 0


def test_a_year_old_booking_weighs_less_than_last_week_s_browsing(monkeypatch):
    now = datetime.now()
    _install_history(
        monkeypatch,
        [("PROFILE_VIEW", 1, None, None, None, None, None, now - timedelta(days=2))],
        [(2, "CONFIRMED", "Wedding", 6000.0, now - timedelta(days=365))],
        [artist(1, genres=("Rock",), parents=("Rock",)),
         artist(2, genres=("Bebop",), parents=("Jazz",))],
        vectors={1: unit(1.0, 0.0, 0.0), 2: unit(0.0, 0.0, 1.0)},
    )

    profile = personalization.build_profile(user_id=7)

    assert profile.genre_affinity["Rock"] == 1.0
    assert profile.genre_affinity["Jazz"] < 0.2


# --------------------------------------------------------------------------- #
# Affinity
# --------------------------------------------------------------------------- #

def _profile(**kwargs) -> TasteProfile:
    defaults = dict(user_id=7, signal=5.0, events=10,
                    genre_affinity={"Jazz": 1.0, "Blues": 0.4},
                    city_affinity={"Kathmandu": 1.0},
                    typical_rate=4000.0)
    defaults.update(kwargs)
    return TasteProfile(**defaults)


def test_an_artist_they_booked_before_scores_top_and_says_so():
    profile = _profile(booked={11: 1.0})

    score, reasons = personalization.affinity(profile, artist(11), {})

    assert score > 0.75
    assert "You have booked them before" in reasons


def test_an_artist_in_a_genre_they_never_touch_scores_low():
    profile = _profile()

    jazz, _ = personalization.affinity(profile, artist(11), {})
    metal, reasons = personalization.affinity(
        profile, artist(12, genres=("Death Metal",), parents=("Metal",),
                        city="Biratnagar", rate=19000.0), {})

    assert metal < jazz
    assert reasons == []


def test_a_reason_is_only_given_by_a_signal_that_actually_counted():
    profile = _profile(booked={}, viewed={})

    _, reasons = personalization.affinity(profile, artist(11), {})

    # Genre, city and rate all line up here; familiarity does not, and is not
    # claimed. At most two are shown, so the card stays a card.
    assert "You have booked them before" not in reasons
    assert "You looked at their profile" not in reasons
    assert len(reasons) <= 2


def test_taste_similarity_uses_the_calibrated_band():
    settings = get_settings()
    profile = _profile(vector=unit(1.0, 0.0, 0.0))
    close = {11: unit(1.0, 0.0, 0.0)}          # cosine 1.0, above the band
    far = {11: unit(0.0, 1.0, 0.0)}            # cosine 0.0, below it

    assert personalization._taste_similarity(profile, 11, close) == 1.0
    assert personalization._taste_similarity(profile, 11, far) == 0.0
    assert settings.taste_cos_low < settings.taste_cos_high

    # No vector for that artist is a missing observation, not a bad one.
    assert personalization._taste_similarity(profile, 99, close) is None


def test_a_person_who_has_never_stated_a_budget_is_not_scored_on_one():
    with_rate = _profile(typical_rate=4000.0)
    without = _profile(typical_rate=None)

    # An artist priced far from the usual rate loses points only for the person
    # whose usual rate is known.
    expensive = artist(11, rate=30000.0)
    assert (personalization.affinity(without, expensive, {})[0]
            > personalization.affinity(with_rate, expensive, {})[0])


# --------------------------------------------------------------------------- #
# Re-ranking
# --------------------------------------------------------------------------- #

def test_scores_are_mapped_onto_a_comparable_scale():
    scaled = personalization._to_unit_scale(np.array([-4.0, 0.0, 1.0, 9.0]))

    assert list(scaled) == sorted(scaled)          # order is preserved
    assert scaled.min() > 0.0 and scaled.max() < 1.0   # nothing is pinned to an end
    # A list the model could not separate carries no information to blend with.
    flat = personalization._to_unit_scale(np.array([2.0, 2.0, 2.0]))
    assert list(flat) == [0.5, 0.5, 0.5]


def test_personalisation_lifts_a_familiar_artist_without_taking_over():
    profile = _profile(booked={12: 1.0})
    candidates = [
        # A stranger, in a genre and a city and a price bracket this person has
        # never gone near, so nothing about them can draw a reason.
        artist(11, genres=("Death Metal",), parents=("Metal",), city="Butwal", rate=19000.0),
        artist(12),
    ]
    scores = np.array([1.0, 0.6])   # the model prefers the stranger

    blended, reasons = personalization.rerank(profile, candidates, scores, alpha=0.4)

    assert blended[1] > blended[0]
    assert reasons[1] and not reasons[0]

    # The same history, given a smaller share of the score, leaves the model's
    # ordering standing. This is what separates a search from a browse.
    gentle, _ = personalization.rerank(profile, candidates, scores, alpha=0.05)
    assert gentle[0] > gentle[1]


def test_re_ranking_an_empty_result_changes_nothing():
    scores = np.array([])
    blended, reasons = personalization.rerank(_profile(), [], scores, alpha=0.4)
    assert blended.size == 0
    assert reasons == []


# --------------------------------------------------------------------------- #
# Retrieval
# --------------------------------------------------------------------------- #

def test_browsing_with_no_filters_retrieves_on_taste_alone():
    profile = _profile(vector=unit(1.0, 0.0, 0.0))
    assert np.allclose(personalization.retrieval_vector(profile, None), profile.vector)


def test_stated_filters_keep_the_larger_share_of_retrieval():
    profile = _profile(vector=unit(0.0, 1.0, 0.0))
    query = unit(1.0, 0.0, 0.0)

    blended = personalization.retrieval_vector(profile, query)

    assert np.linalg.norm(blended) == pytest.approx(1.0, rel=1e-5)
    assert float(np.dot(blended, query)) > float(np.dot(blended, profile.vector))


def test_a_profile_with_no_vector_leaves_retrieval_alone():
    profile = _profile(vector=None)
    query = unit(1.0, 0.0, 0.0)
    assert np.allclose(personalization.retrieval_vector(profile, query), query)
    assert personalization.retrieval_vector(profile, None) is None
