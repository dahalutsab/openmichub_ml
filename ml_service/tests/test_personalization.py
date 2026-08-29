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

from app import personalization, search
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

def _install_history(monkeypatch, interactions, bookings, artists, vectors=None,
                     genres=("Jazz", "Blues", "Electronic", "House")):
    def rows(sql, params):
        return interactions if "FROM user_interaction" in sql else bookings

    monkeypatch.setattr(personalization, "_rows", rows)
    monkeypatch.setattr(personalization, "fetch_artists",
                        lambda ids: [a for a in artists if a["artist_id"] in set(ids)])
    monkeypatch.setattr(personalization, "_embeddings", lambda ids: dict(vectors or {}))
    monkeypatch.setattr(personalization, "embed",
                        lambda texts: np.vstack([unit(0.0, 1.0, 0.0)] * len(texts)))
    monkeypatch.setattr(personalization, "embed_one", lambda text: unit(0.0, 1.0, 0.0))
    monkeypatch.setattr(personalization, "_genre_vocabulary", lambda: list(genres))


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
    # Two vectors, not one: the artists they engaged with, and separately the
    # search they typed.
    assert profile.vector is not None
    assert np.linalg.norm(profile.vector) == pytest.approx(1.0, rel=1e-5)
    assert profile.intent_vector is not None
    assert profile.intent_signal > 0


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


def test_similarity_uses_the_calibrated_band():
    settings = get_settings()
    band = personalization._taste_band()
    reference = unit(1.0, 0.0, 0.0)
    close = {11: unit(1.0, 0.0, 0.0)}          # cosine 1.0, above the band
    far = {11: unit(0.0, 1.0, 0.0)}            # cosine 0.0, below it

    assert personalization._similarity_to(reference, 11, close, band) == 1.0
    assert personalization._similarity_to(reference, 11, far, band) == 0.0
    assert settings.taste_cos_low < settings.taste_cos_high

    # No vector for that artist, or none on the profile, is a missing
    # observation rather than a bad match.
    assert personalization._similarity_to(reference, 99, close, band) is None
    assert personalization._similarity_to(None, 11, close, band) is None


def test_intent_is_scaled_as_a_query_and_taste_as_a_profile():
    """The two vectors live in different parts of the cosine distribution.

    Scaling a search through the taste band was what left a perfectly matching
    artist scoring about 0.2, with no reason on the card to show for it.
    """
    intent_low, intent_high = personalization._intent_band()
    taste_low, taste_high = personalization._taste_band()

    assert intent_low < taste_low and intent_high < taste_high

    # A cosine typical of a good query match reads as a good match on the intent
    # scale, and as a poor one on the taste scale.
    cosine = 0.62
    vectors = {11: unit(1.0, 0.0, 0.0)}
    reference = unit(1.0, 0.0, 0.0) * cosine + unit(0.0, 1.0, 0.0) * np.sqrt(1 - cosine ** 2)

    as_intent = personalization._similarity_to(reference, 11, vectors,
                                               personalization._intent_band())
    as_taste = personalization._similarity_to(reference, 11, vectors,
                                              personalization._taste_band())
    assert as_intent > 0.9
    assert as_taste < 0.4


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

def test_a_search_outweighs_the_filters_it_was_typed_alongside():
    profile = _profile(intent_vector=unit(0.0, 1.0, 0.0), intent_signal=1.0)
    filters = unit(1.0, 0.0, 0.0)

    blended = personalization.query_space_vector(profile, filters)

    assert np.linalg.norm(blended) == pytest.approx(1.0, rel=1e-5)
    assert float(np.dot(blended, profile.intent_vector)) > float(np.dot(blended, filters))


def test_query_space_holds_only_query_text():
    """The taste vector must not be blended in here.

    It is an average of profile vectors and sits closer to every artist than
    query text ever does, so a single vector containing both hands retrieval to
    the taste side whatever share the weights claim. The pool split is what
    honours the share; see `merged_candidates`.
    """
    filters = unit(1.0, 0.0, 0.0)
    taste_only = _profile(vector=unit(0.0, 0.0, 1.0))

    assert np.allclose(personalization.query_space_vector(taste_only, filters), filters)
    assert personalization.query_space_vector(taste_only, None) is None

    intent_only = _profile(vector=None, intent_vector=unit(0.0, 1.0, 0.0), intent_signal=1.0)
    assert np.allclose(personalization.query_space_vector(intent_only, None),
                       intent_only.intent_vector)


def test_the_pool_is_split_between_the_two_sources_by_slot_count(monkeypatch):
    asked = [{"artist_id": i} for i in range(1, 66)]
    history = [{"artist_id": i} for i in range(100, 135)]
    calls = []

    def fake(vector, limit=None, city=None, max_hourly_rate=None,
             on_profile_vectors=False, similarity_vector=None):
        calls.append((limit, on_profile_vectors))
        return history[:limit] if on_profile_vectors else asked[:limit]

    monkeypatch.setattr(search, "candidates_near", fake)

    merged = search.merged_candidates(unit(1.0, 0.0, 0.0), unit(0.0, 1.0, 0.0),
                                      taste_share=0.35, limit=100)

    # 35 slots from the history, 65 from what they asked for, each against its
    # own column.
    assert sorted(calls) == sorted([(65, False), (35, True)])
    assert len(merged) == 100
    assert len({c["artist_id"] for c in merged}) == 100


def test_one_source_takes_the_whole_pool_when_the_other_is_missing(monkeypatch):
    calls = []

    def fake(vector, limit=None, city=None, max_hourly_rate=None,
             on_profile_vectors=False, similarity_vector=None):
        calls.append((limit, on_profile_vectors))
        return [{"artist_id": 1}]

    monkeypatch.setattr(search, "candidates_near", fake)

    search.merged_candidates(unit(1.0, 0.0, 0.0), None, taste_share=0.35, limit=100)
    search.merged_candidates(None, unit(0.0, 1.0, 0.0), taste_share=0.35, limit=100)

    assert calls == [(100, False), (100, True)]
    assert search.merged_candidates(None, None, taste_share=0.35, limit=100) == []


def test_an_overlap_between_the_pools_is_not_returned_twice(monkeypatch):
    same = [{"artist_id": 1}, {"artist_id": 2}]
    monkeypatch.setattr(search, "candidates_near",
                        lambda *a, **kw: list(same[:kw.get("limit") or 2]))

    merged = search.merged_candidates(unit(1.0, 0.0, 0.0), unit(0.0, 1.0, 0.0),
                                      taste_share=0.5, limit=4)

    assert [c["artist_id"] for c in merged] == [1, 2]


# --------------------------------------------------------------------------- #
# Search intent
#
# These are the cases behind "I searched for a DJ and it still isn't showing me
# DJs". Each one was a real defect: a single search fell below the floor, a
# fresh search was averaged into a long booking history until it was worth about
# two percent of the profile, and the profile the browse page read had been
# cached before the search was made.
# --------------------------------------------------------------------------- #

def test_one_search_is_enough_to_personalise(monkeypatch):
    now = datetime.now()
    _install_history(
        monkeypatch,
        [("SEARCH", None, "dj for a late night club event", None, None, None, None,
          now - timedelta(minutes=5))],
        [], [],
    )

    profile = personalization.build_profile(user_id=7)

    # Worth less than the floor as durable taste, and usable all the same: they
    # said what they wanted.
    assert profile.signal < get_settings().taste_min_signal
    assert profile.intent_vector is not None
    assert profile.usable
    assert personalization.profile_for(7) is not None


def test_one_profile_view_is_still_not_enough(monkeypatch):
    now = datetime.now()
    _install_history(
        monkeypatch,
        [("PROFILE_VIEW", 1, None, None, None, None, None, now - timedelta(minutes=5))],
        [], [artist(1)], vectors={1: unit(1.0, 0.0, 0.0)},
    )

    assert not personalization.build_profile(user_id=7).usable


def test_searching_the_same_thing_again_counts_for_more_but_not_forever(monkeypatch):
    now = datetime.now()
    one = [("SEARCH", None, "dj for a club night", None, None, None, None, now)]
    many = one * 8

    _install_history(monkeypatch, one, [], [])
    single = personalization.build_profile(user_id=7).intent_signal

    _install_history(monkeypatch, many, [], [])
    repeated = personalization.build_profile(user_id=8).intent_signal

    assert repeated > single
    assert repeated <= personalization.MAX_INTENT_PER_TEXT


def test_a_search_names_a_genre_even_when_no_filter_was_picked(monkeypatch):
    now = datetime.now()
    _install_history(
        monkeypatch,
        [("SEARCH", None, "house dj set for a club night", None, None, None, None, now)],
        [], [],
    )

    profile = personalization.build_profile(user_id=7)

    # The genre was in the words, not in a dropdown. Reading it back out is what
    # puts "you keep coming back to House" on the resulting cards.
    assert profile.genre_affinity.get("House") == 1.0


def test_intent_fades_faster_than_taste():
    settings = get_settings()
    fortnight = 14.0

    assert (personalization._decay(fortnight, settings.taste_intent_half_life_days)
            < personalization._decay(fortnight) / 2)


def test_a_fresh_search_outweighs_a_long_history_in_the_score_too():
    """Intent must beat the durable side in ranking, not only in retrieval.

    Taste used to be counted twice — once as a vector and again as genre
    affinity — so an artist retrieved because of a search was then ordered as
    though the search had not happened. Both shares now come from one number.
    """
    intent, taste, genre = personalization._affinity_weights()

    assert intent > taste + genre
    assert (intent + taste + genre + personalization.W_FAMILIARITY
            + personalization.W_BUDGET + personalization.W_CITY) == pytest.approx(1.0)


def test_a_fresh_search_is_not_buried_by_a_long_booking_history():
    """One search against twenty-eight bookings, scored as a whole artist."""
    profile = _profile(vector=unit(1.0, 0.0, 0.0), intent_vector=unit(0.0, 1.0, 0.0),
                       intent_signal=1.0, booked={i: 1.0 for i in range(28)},
                       genre_affinity={"Jazz": 1.0}, city_affinity={}, typical_rate=None)

    # An artist matching the search but nothing else, against one matching the
    # whole booking history but not the search.
    searched_for, _ = personalization.affinity(
        profile, artist(11, genres=("Techno",), parents=("Electronic",)),
        {11: unit(0.0, 1.0, 0.0)})
    booked_before, _ = personalization.affinity(
        profile, artist(12), {12: unit(1.0, 0.0, 0.0)})

    assert searched_for > booked_before


def test_a_search_reaches_the_next_page_without_waiting_for_a_rebuild(monkeypatch):
    now = datetime.now()
    _install_history(
        monkeypatch,
        [("PROFILE_VIEW", 1, None, None, None, None, None, now - timedelta(days=1))],
        [(2, "CONFIRMED", "Wedding", 6000.0, now - timedelta(days=5))],
        [artist(1), artist(2)],
        vectors={1: unit(1.0, 0.0, 0.0), 2: unit(1.0, 0.0, 0.0)},
    )
    monkeypatch.setattr(personalization, "embed_one", lambda text: unit(0.0, 0.0, 1.0))

    profile = personalization.profile_for(7)
    assert profile is not None and profile.intent_vector is None

    personalization.note_search(7, "dj for a late night club event")

    # The cached profile - the one the next browse will read, before any rebuild
    # is due - now carries that search.
    updated = personalization.profile_for(7)
    assert updated.intent_vector is not None
    assert float(np.dot(updated.intent_vector, unit(0.0, 0.0, 1.0))) == pytest.approx(1.0, rel=1e-5)


def test_noting_a_search_for_nobody_does_nothing(monkeypatch):
    monkeypatch.setattr(personalization, "embed_one",
                        lambda text: pytest.fail("should not embed"))

    personalization.note_search(None, "dj for a club night")
    personalization.note_search(7, "   ")
    personalization.note_search(7, None)


def test_an_artist_matching_a_recent_search_says_so():
    profile = _profile(intent_vector=unit(1.0, 0.0, 0.0), intent_signal=1.0)

    _, reasons = personalization.affinity(profile, artist(11), {11: unit(1.0, 0.0, 0.0)})

    assert "Matches what you have been searching for" in reasons



