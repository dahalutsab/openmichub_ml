"""Tests for platform-wide signals and the final blend.

No database: `build_signals` takes rows, and the blend takes scores. What is
worth pinning is behaviour a wrong constant would quietly break - an act chosen
together once counting as a strong tie, last year's busiest act topping this
month's front page, a newcomer never getting shown, a skipped act coming back to
the top slot forever.

    docker compose exec ml python -m pytest tests -q
"""

from __future__ import annotations

from datetime import date, datetime, timedelta

import numpy as np
import pytest

from app import blend, personalization
from app.config import get_settings
from app.personalization import TasteProfile
from app.signals import Signals, build_signals

NOW = datetime(2026, 9, 15, 12, 0)


def ago(days: float) -> datetime:
    return NOW - timedelta(days=days)


def unit(*values: float) -> np.ndarray:
    vector = np.array(values, dtype=np.float32)
    return vector / np.linalg.norm(vector)


def artist(artist_id: int) -> dict:
    return {"artist_id": artist_id, "stage_name": f"Act {artist_id}",
            "sub_genres": ["Jazz"], "parent_genres": ["Jazz"], "city": "Kathmandu",
            "hourly_rate": 4000.0, "rating": 4.0, "completed_bookings": 5}


# --------------------------------------------------------------------------- #
# Co-choice
# --------------------------------------------------------------------------- #

def _circle_bookings() -> list[tuple]:
    """Six organizers who all book acts 1 and 2; a seventh books 1 and 3 once."""
    rows = []
    for organizer in range(6):
        rows.append((f"u{organizer}", 1, "COMPLETED", ago(20 + organizer)))
        rows.append((f"u{organizer}", 2, "COMPLETED", ago(25 + organizer)))
    rows.append(("u99", 1, "COMPLETED", ago(10)))
    rows.append(("u99", 3, "COMPLETED", ago(12)))
    return rows


def test_acts_chosen_together_by_many_people_are_neighbours():
    signals = build_signals(_circle_bookings(), [], [], [], NOW)

    neighbours = dict(signals.similar(1, 10))
    assert neighbours[2] > 0.5
    assert signals.actors == 7


def test_a_pair_chosen_together_once_is_a_coincidence_not_a_tie():
    signals = build_signals(_circle_bookings(), [], [], [], NOW)

    # One organizer booked 1 and 3. Below the minimum support, it is not a tie.
    assert 3 not in dict(signals.similar(1, 10))


def test_co_choice_names_the_act_it_came_from():
    signals = build_signals(_circle_bookings(), [], [], [], NOW)

    scores = signals.co_choice({1: 1.0})
    score, via = scores[2]
    assert via == 1
    assert score > 0
    # Nothing is its own neighbour.
    assert 1 not in scores


def test_views_link_acts_as_well_as_bookings_do_but_weaker():
    views = [(f"v{visitor}", artist_id, ago(3)) for visitor in range(8) for artist_id in (5, 6)]
    signals = build_signals([], views, [], [], NOW)

    assert dict(signals.similar(5, 5)).get(6, 0) > 0


def test_an_empty_platform_has_no_neighbours_and_no_scale():
    signals = build_signals([], [], [], [], NOW)

    assert signals.neighbours == {}
    assert signals.co_scale == 0.0
    assert signals.co_choice({1: 1.0}) == {}


def test_rows_after_the_replay_date_are_ignored():
    future = [(f"u{o}", 7, "COMPLETED", NOW + timedelta(days=3)) for o in range(5)]
    signals = build_signals(future, [], [], [], NOW)

    assert signals.demand == {}
    assert signals.actors == 0


# --------------------------------------------------------------------------- #
# Demand
# --------------------------------------------------------------------------- #

def test_demand_is_about_this_month_not_all_time():
    old_star = [(f"u{o}", 1, "COMPLETED", ago(200 + o)) for o in range(40)]
    busy_now = [(f"u{o}", 2, "PENDING", ago(5 + o)) for o in range(6)]
    signals = build_signals(old_star + busy_now, [], [], [], NOW)

    # Forty bookings last year do not count as demand this month at all.
    assert signals.demand.get(1, 0.0) == 0.0
    assert signals.demand[2] == pytest.approx(1.0)


def test_the_blend_lifts_an_act_in_demand_on_the_front_page_and_says_so():
    signals = Signals(demand={2: 1.0})
    candidates = [artist(1), artist(2)]
    scores = np.array([0.55, 0.50])

    final, reasons = blend.platform_blend(candidates, scores, blend.SURFACE_HOME, signals)

    assert final[1] > final[0]
    assert reasons[1] == ["In demand this month"]


def test_demand_barely_moves_a_typed_search():
    signals = Signals(demand={2: 1.0})
    candidates = [artist(1), artist(2)]
    scores = np.array([0.70, 0.50])

    final, _ = blend.platform_blend(candidates, scores, blend.SURFACE_SEARCH, signals)

    assert final[0] > final[1]


# --------------------------------------------------------------------------- #
# Exploration and skips
# --------------------------------------------------------------------------- #

def test_an_act_rarely_shown_gets_a_small_chance_over_one_shown_constantly():
    signals = Signals(exposure={1: 400, 2: 0})
    candidates = [artist(1), artist(2)]
    tied = np.array([0.5, 0.5])
    far_apart = np.array([0.8, 0.5])

    close, _ = blend.platform_blend(candidates, tied, blend.SURFACE_HOME, signals)
    apart, _ = blend.platform_blend(candidates, far_apart, blend.SURFACE_HOME, signals)

    assert close[1] > close[0]
    # A chance, not a promotion: it cannot overturn a real difference in fit.
    assert apart[0] > apart[1]


def test_no_exposure_history_means_no_exploration_bonus_for_anyone():
    candidates = [artist(1), artist(2)]
    scores = np.array([0.6, 0.4])

    final, _ = blend.platform_blend(candidates, scores, blend.SURFACE_HOME, Signals())

    # Only demand's share comes out of the score; exploration's goes back in.
    kept = 1.0 - get_settings().demand_weight_home
    assert list(final) == pytest.approx([0.6 * kept, 0.4 * kept])


def test_an_act_passed_over_again_and_again_gives_way():
    profile = TasteProfile(visitor_id="abc", skipped={1: 8})
    candidates = [artist(1), artist(2)]
    strength = personalization.skip_strength(profile, candidates)

    assert strength[0] == 1.0 and strength[1] == 0.0

    final, _ = blend.platform_blend(candidates, np.array([0.56, 0.50]),
                                    blend.SURFACE_HOME, Signals(), skips=strength)
    assert final[1] > final[0]


def test_two_skips_are_not_yet_a_pattern():
    profile = TasteProfile(visitor_id="abc", skipped={1: 2})

    assert personalization.skip_strength(profile, [artist(1)])[0] == 0.0


def test_front_page_nudges_differ_by_visitor_and_by_day_but_are_tiny():
    candidates = [artist(i) for i in range(1, 30)]
    scores = np.full(len(candidates), 0.5)

    a, _ = blend.platform_blend(candidates, scores, blend.SURFACE_HOME, Signals(),
                                viewer="va", today=date(2026, 9, 15))
    b, _ = blend.platform_blend(candidates, scores, blend.SURFACE_HOME, Signals(),
                                viewer="vb", today=date(2026, 9, 15))
    a_again, _ = blend.platform_blend(candidates, scores, blend.SURFACE_HOME, Signals(),
                                      viewer="va", today=date(2026, 9, 15))

    assert list(np.argsort(a)) != list(np.argsort(b))
    assert list(a) == list(a_again)
    assert float(np.max(np.abs(a - b))) <= blend.HOME_JITTER


# --------------------------------------------------------------------------- #
# Variety
# --------------------------------------------------------------------------- #

def test_variety_breaks_up_a_run_of_near_identical_acts():
    candidates = [artist(i) for i in range(1, 5)]
    # Three near-identical jazz acts score just above one different act.
    vectors = {1: unit(1, 0, 0), 2: unit(1, 0.01, 0), 3: unit(1, 0, 0.01), 4: unit(0, 1, 0)}
    scores = np.array([0.90, 0.88, 0.87, 0.80])

    order = blend.diversify(candidates, scores, vectors, limit=2, band=(0.55, 0.80))

    assert order[0] == 0
    assert order[1] == 3            # the different act moves up to second
    assert sorted(order) == [0, 1, 2, 3]


def test_variety_never_overturns_a_large_difference_in_fit():
    candidates = [artist(i) for i in range(1, 4)]
    vectors = {1: unit(1, 0, 0), 2: unit(1, 0.01, 0), 3: unit(0, 1, 0)}
    scores = np.array([0.95, 0.94, 0.20])

    order = blend.diversify(candidates, scores, vectors, limit=3, band=(0.55, 0.80))

    assert order[:2] == [0, 1]


# --------------------------------------------------------------------------- #
# Personalised affinity with co-choice
# --------------------------------------------------------------------------- #

def test_an_act_often_picked_alongside_one_they_booked_says_which():
    profile = TasteProfile(user_id=7, signal=5.0, events=5, booked={1: 1.0},
                           names={1: "The Velvet Club"})
    co = {2: (0.4, 1)}

    score, reasons = personalization.affinity(profile, artist(2), {}, co, co_scale=0.4)
    stranger, _ = personalization.affinity(profile, artist(3), {}, co, co_scale=0.4)

    assert "Often picked alongside The Velvet Club" in reasons
    assert score > stranger


def test_the_most_specific_reason_is_the_one_shown_first():
    profile = TasteProfile(user_id=7, signal=5.0, events=5, booked={1: 1.0, 2: 1.0},
                           names={1: "The Velvet Club"},
                           intent_vector=unit(1, 0, 0), intent_signal=1.0,
                           genre_affinity={"Jazz": 1.0})
    vectors = {2: unit(1, 0, 0)}

    _, reasons = personalization.affinity(profile, artist(2), vectors, {2: (0.4, 1)}, co_scale=0.4)

    # Booked, searched for, a favourite genre and chosen alongside another act all
    # apply. "Matches your search" could be said of half a personalised page;
    # these two could only be said of this card.
    assert reasons == ["You have booked them before", "Often picked alongside The Velvet Club"]


def test_a_loose_match_to_a_search_is_not_claimed_on_the_card():
    profile = TasteProfile(user_id=7, signal=5.0, events=5,
                           intent_vector=unit(1, 0, 0), intent_signal=1.0)
    low, high = personalization._intent_band()
    # A cosine in the middle of the band: related, not a match.
    cosine = low + 0.6 * (high - low)
    vectors = {2: unit(1, 0, 0) * cosine + unit(0, 1, 0) * np.sqrt(1 - cosine ** 2)}

    _, reasons = personalization.affinity(profile, artist(2), vectors)

    assert "Matches what you have been searching for" not in reasons


def test_co_choice_is_left_out_when_the_platform_has_no_scale_for_it():
    profile = TasteProfile(user_id=7, signal=5.0, events=5, booked={1: 1.0}, names={1: "A"})

    with_nothing, _ = personalization.affinity(profile, artist(2), {})
    unscaled, reasons = personalization.affinity(profile, artist(2), {}, {2: (0.4, 1)}, 0.0)

    assert with_nothing == pytest.approx(unscaled)
    assert not any("alongside" in reason for reason in reasons)


# --------------------------------------------------------------------------- #
# Visitors
# --------------------------------------------------------------------------- #

def test_a_signed_out_visitor_gets_a_profile_from_their_own_browsing(monkeypatch):
    now = datetime.now()
    seen = {}

    def rows(sql, params):
        seen.setdefault("sql", []).append(sql)
        if "discovery_impression" in sql:
            return []
        if "FROM user_interaction" in sql:
            return [("SEARCH", None, "dj for a club night", None, None, None, None,
                     now - timedelta(hours=1))]
        pytest.fail("a visitor has no bookings to read")

    monkeypatch.setattr(personalization, "_rows", rows)
    monkeypatch.setattr(personalization, "fetch_artists", lambda ids: [])
    monkeypatch.setattr(personalization, "_embeddings", lambda ids: {})
    monkeypatch.setattr(personalization, "embed", lambda texts: np.vstack([unit(0, 1, 0)] * len(texts)))
    monkeypatch.setattr(personalization, "_genre_vocabulary", lambda: [])
    personalization.forget()

    profile = personalization.profile_for(None, "3f2b8c1e-9a4d-4e7b-8c21-0d5e6f7a8b9c")

    assert profile is not None and profile.usable
    assert profile.user_id is None
    assert profile.intent_vector is not None
    assert all("visitor_id = %(owner)s" in sql for sql in seen["sql"])
    personalization.forget()


def test_nobody_at_all_has_no_profile():
    assert personalization.profile_for(None, None) is None
    assert personalization.profile_for(None, "") is None


def test_a_front_page_is_not_made_entirely_of_acts_they_already_know():
    candidates = [artist(i) for i in range(1, 17)]
    order = list(range(16))                       # acts 1-6 known, and ranked first
    familiar = {1, 2, 3, 4, 5, 6}

    shown = blend.limit_familiar(order, candidates, familiar, limit=8)

    first_screen = [candidates[p]["artist_id"] for p in shown[:8]]
    assert sum(1 for a in first_screen if a in familiar) == 3
    assert first_screen[:3] == [1, 2, 3]           # the best known acts keep their places
    assert sorted(shown) == list(range(16))        # moved down, never dropped


def test_known_acts_still_fill_a_screen_there_is_nothing_else_to_fill():
    candidates = [artist(i) for i in range(1, 11)]
    familiar = {1, 2, 3, 4, 5, 6}

    shown = blend.limit_familiar(list(range(10)), candidates, familiar, limit=8)

    # Only four unfamiliar acts exist; an empty slot helps nobody.
    assert len(shown[:8]) == 8
