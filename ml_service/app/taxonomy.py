"""Genre, city and event tables shared by training and serving.

These lived in two places — `training/generate.py` and `app/ranker.py` — and
drifted. The serving copy of the event table had been trimmed to the three or
four genres per event that looked important, so a Wedding/Rock pair scored the
0.5 default at serving where training had said 0.4, and every feature built from
these tables was quietly computed against a different world than the model was
fitted on. One definition, imported by both, is what stops that recurring.
"""

from __future__ import annotations

# city: (province, latitude, longitude)
CITIES: dict[str, tuple[str, float, float]] = {
    "Kathmandu": ("Bagmati", 27.7172, 85.3240),
    "Lalitpur": ("Bagmati", 27.6644, 85.3188),
    "Bhaktapur": ("Bagmati", 27.6710, 85.4298),
    "Pokhara": ("Gandaki", 28.2096, 83.9856),
    "Chitwan": ("Bagmati", 27.5291, 84.3542),
    "Butwal": ("Lumbini", 27.7006, 83.4484),
    "Nepalgunj": ("Lumbini", 28.0500, 81.6167),
    "Biratnagar": ("Koshi", 26.4525, 87.2718),
    "Dharan": ("Koshi", 26.8065, 87.2846),
    "Janakpur": ("Madhesh", 26.7288, 85.9266),
}

PROVINCE: dict[str, str] = {city: value[0] for city, value in CITIES.items()}

# The musical taxonomy the generator draws on. The live catalogue also carries
# non-musical parents (Poet, Magician, Standup Comedian, Storyteller, Singer,
# Instrumentalist); those are absent here on purpose — the generator models the
# musical booking flow, and the live ones resolve through `genre_taxonomy()`.
GENRES: dict[str, list[str]] = {
    "Rock": ["Classic Rock", "Indie Rock", "Alternative", "Punk"],
    "Jazz": ["Bebop", "Smooth Jazz", "Fusion", "Swing"],
    "Folk": ["Nepali Folk", "Acoustic Folk", "Storytelling", "Bluegrass"],
    "Pop": ["Nepali Pop", "Synth Pop", "Acoustic Pop", "Dance Pop"],
    "Classical": ["Hindustani", "Sitar", "Flute", "Chamber"],
    "Hip-Hop": ["Nepali Rap", "Boom Bap", "Trap", "Lyrical"],
    "Electronic": ["House", "Techno", "Ambient", "Drum and Bass"],
    "Blues": ["Delta Blues", "Electric Blues", "Soul Blues"],
}

EVENT_TYPES: list[str] = [
    "Wedding", "Corporate", "Festival", "Birthday",
    "Club Night", "Open Mic", "Charity Gala", "Restaurant",
]

# How well each parent genre suits each event, 0-1. Deliberately not uniform:
# this is the kind of structure a ranker should pick up and a rating-only
# baseline cannot.
EVENT_GENRE_FIT: dict[str, dict[str, float]] = {
    "Wedding":      {"Folk": 1.0, "Pop": 0.9, "Classical": 0.9, "Jazz": 0.7, "Blues": 0.5, "Rock": 0.4, "Hip-Hop": 0.3, "Electronic": 0.3},
    "Corporate":    {"Jazz": 1.0, "Classical": 0.9, "Pop": 0.7, "Folk": 0.6, "Blues": 0.6, "Electronic": 0.4, "Rock": 0.3, "Hip-Hop": 0.2},
    "Festival":     {"Rock": 1.0, "Electronic": 0.9, "Pop": 0.9, "Hip-Hop": 0.8, "Folk": 0.7, "Blues": 0.5, "Jazz": 0.5, "Classical": 0.3},
    "Birthday":     {"Pop": 1.0, "Hip-Hop": 0.8, "Rock": 0.8, "Electronic": 0.7, "Folk": 0.6, "Jazz": 0.5, "Blues": 0.4, "Classical": 0.3},
    "Club Night":   {"Electronic": 1.0, "Hip-Hop": 0.9, "Pop": 0.7, "Rock": 0.6, "Blues": 0.3, "Jazz": 0.3, "Folk": 0.2, "Classical": 0.1},
    "Open Mic":     {"Folk": 1.0, "Blues": 0.9, "Hip-Hop": 0.8, "Rock": 0.7, "Jazz": 0.7, "Pop": 0.6, "Classical": 0.4, "Electronic": 0.3},
    "Charity Gala": {"Classical": 1.0, "Jazz": 0.9, "Folk": 0.8, "Pop": 0.7, "Blues": 0.6, "Rock": 0.4, "Electronic": 0.3, "Hip-Hop": 0.3},
    "Restaurant":   {"Jazz": 1.0, "Blues": 0.9, "Folk": 0.9, "Classical": 0.8, "Pop": 0.6, "Rock": 0.3, "Electronic": 0.3, "Hip-Hop": 0.2},
}

NEUTRAL_EVENT_FIT = 0.5


def event_fit(event_type: str | None, parent_genres: list[str] | None) -> float:
    """Best fit across every parent genre the artist plays.

    An act filed under both Jazz and Rock is a good Corporate booking on the
    strength of the Jazz, so the maximum is the honest reading. Taking the first
    of an alphabetised array — which is what this used to do — made the answer
    depend on spelling.
    """
    if not event_type or not parent_genres:
        return NEUTRAL_EVENT_FIT
    table = EVENT_GENRE_FIT.get(event_type)
    if not table:
        return NEUTRAL_EVENT_FIT
    fits = [table[genre] for genre in parent_genres if genre in table]
    return max(fits) if fits else NEUTRAL_EVENT_FIT
