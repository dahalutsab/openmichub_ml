"""Vocabulary for a demo catalogue that reads like a real roster.

Kept apart from the seeding logic because it is the part that decides whether
the platform looks credible. A UI reviewed against "Sound of Bebop 104" tells
you nothing about how it handles an actual artist name, and a profile whose bio
is three words tells you nothing about how the About panel wraps.

Everything here is invented. The names are built from Nepali and English pieces
the way working acts in Kathmandu actually mix them; none is a real performer.
"""

from __future__ import annotations

# --- people ------------------------------------------------------------------

NEPALI_GIVEN = [
    "Aayush", "Aastha", "Nirajan", "Simran", "Prasiddha", "Sadiksha", "Bibek",
    "Anjali", "Sujan", "Pratigya", "Kiran", "Manisha", "Rohit", "Sabina",
    "Dipesh", "Ujjwal", "Sneha", "Bishal", "Ruchi", "Nabin", "Prakriti",
    "Sandesh", "Muna", "Anup", "Sarita", "Binod", "Ishwor", "Rekha", "Suraj",
    "Alina", "Bipin", "Sushmita", "Gaurav", "Nisha", "Ramesh", "Puja",
]

NEPALI_FAMILY = [
    "Sharma", "Shrestha", "Maharjan", "Gurung", "Tamang", "Adhikari", "Rai",
    "Limbu", "Thapa", "Karki", "Pandey", "Bhattarai", "Lama", "Magar",
    "Sherpa", "Chaudhary", "Poudel", "Dahal", "Basnet", "Khadka", "Joshi",
    "Subedi", "Acharya", "Bista", "Rana", "Malla", "Newar", "Ghimire",
]

# --- band names --------------------------------------------------------------

BAND_PREFIX = ["The", "The", "The", "", "", "", "Project", "Kollektiv"]

BAND_NOUNS = [
    "Himal", "Bagmati", "Sarangi", "Madal", "Bansuri", "Jhyaure", "Dohori",
    "Sunkoshi", "Trishuli", "Chautari", "Bhairav", "Malashree", "Annapurna",
    "Machhapuchhre", "Monsoon", "Lantern", "Velvet", "Copper", "Northern",
    "Amber", "Midnight", "Paper", "Marigold", "Tin", "Glass", "Rooftop",
    "Thamel", "Patan", "Boudha", "Phewa", "Kalinchowk", "Rani", "Saptakoshi",
]

BAND_TAIL = [
    "Club", "Union", "Assembly", "Collective", "Orchestra", "Sessions",
    "Society", "Company", "Quartet", "Trio", "Ensemble", "Riot", "Choir",
    "Machine", "Avenue", "Radio", "Circus", "Brigade", "Parade", "Bazaar",
]

# --- bios --------------------------------------------------------------------
#
# Four shapes, so the About panel is exercised by short and long copy alike.
# The column is varchar(255), and the templates are sized to stay inside it.

BIO_TEMPLATES = [
    "{sub} out of {city}. {years} years of playing {venue_kind}, from {small} to {big}. "
    "We bring our own PA and we soundcheck early.",

    "{adjective} {sub} for people who want {mood}. Based in {city}, touring {province} "
    "most weekends. Happy to learn a first-dance song if you send it ahead.",

    "{sub}. {years} years, {gigs}+ shows, still the same four people. "
    "We play {small} on Fridays and {big} when someone asks nicely.",

    "{city}-based {sub}. Weddings, {event_lower}s, anything with a floor to fill. "
    "Set lengths from 45 minutes to three hours, entirely up to you.",

    "We started in a {city} living room in {start_year} and have been playing {venue_kind} "
    "ever since. {sub}, mostly originals, a handful of covers nobody minds.",

    "{sub} that leans {mood_short}. Comfortable at {small}, equally at home at {big}. "
    "Ask us about the acoustic set if the room is small.",

    "{adjective} {sub} from {city}. Two guitars, no laptop, {years} years of it. "
    "We travel across {province} and will go further for the right night.",

    "Booking {year_ahead} now. {sub} with {gigs}+ shows behind us, most of them {venue_kind}. "
    "We handle our own sound and finish when you tell us to.",

    "{sub}, {city}. We are the band you hire when {mood}. "
    "Deposit holds the date; the rest on the night.",

    "Long-running {sub} outfit. {years} years, three line-up changes, one van. "
    "{small} keep us honest, {big} pay for the strings.",

    "{adjective} {sub}. We play {event_lower}s across {province} and take exactly one "
    "booking a weekend, so the night you get is not our third of the week.",

    "{sub} built around {instrument} and whatever the room needs. {city}-based, "
    "touring when asked. Happy to play quietly through dinner and loudly after it.",
]

BIO_ADJECTIVES = [
    "Loud", "Warm", "Restless", "Unhurried", "Six-piece", "Four-piece", "Acoustic",
    "Five-piece", "Understated", "Brass-heavy", "Stripped-back", "Three-piece",
]

BIO_MOODS = [
    "a room that moves", "something mellow behind conversation", "an actual dancefloor",
    "the last hour to matter", "songs their parents also know",
    "the ceremony to feel unhurried", "a night nobody checks their phone through",
    "guests to ask who we were", "the kind of set that starts quiet and does not stay there",
    "music that suits the room rather than the poster",
]

BIO_MOODS_SHORT = [
    "acoustic", "electric", "brass-forward", "percussive", "vocal-led",
    "instrumental", "unhurried", "loud", "melodic", "traditional",
]

INSTRUMENTS = [
    "a sarangi", "the madal", "upright bass", "a Hammond organ", "twin guitars",
    "a bansuri", "a five-piece horn section", "close harmony", "a tabla",
    "fingerpicked guitar", "an accordion", "a full rhythm section",
]

VENUE_KINDS = [
    "bars", "rooftops", "wedding lawns", "festival stages", "restaurants",
    "hotel ballrooms", "college fests", "courtyards", "private gardens",
]

SMALL_VENUES = [
    "Thamel bars", "Patan courtyards", "house shows", "cafe corners", "open mics",
    "Lakeside terraces", "college canteens", "bookshop back rooms",
]

BIG_VENUES = [
    "Nepathya's stage", "Jazzmandu", "wedding lawns in Godavari", "corporate halls",
    "Pokhara lakeside festivals", "college fests", "hotel ballrooms in Durbar Marg",
    "the Basantapur stage", "Dashain corporate nights",
]

# --- venues bookings actually happen at --------------------------------------

VENUES = {
    "Kathmandu": [
        "LOD Thamel", "Purple Haze Rock Bar", "House of Music", "Trisara",
        "Moksh Live", "Hyatt Regency Ballroom", "Soaltee Crowne Plaza",
        "Jazz Upstairs", "Base Camp Thamel",
    ],
    "Lalitpur": ["Patan Durbar Courtyard", "The Yellow House", "Cafe Swotha", "Yala Mandala"],
    "Bhaktapur": ["Nyatapola Square", "Peacock Guest House", "Taumadhi Rooftop"],
    "Pokhara": ["Busy Bee Cafe", "Lakeside Amphitheatre", "Bamboo Stick", "Fewa Paradise Lawn"],
    "Chitwan": ["Sauraha Riverside", "Jungle Villa Lawn", "Rhino Lodge Terrace"],
    "Butwal": ["Hotel Prasidhi Hall", "Traffic Chowk Grounds"],
    "Nepalgunj": ["Sneha Hotel Hall", "Bageshwori Grounds"],
    "Biratnagar": ["Hotel Eastern Star", "Koshi Convention Hall"],
    "Dharan": ["Bhanu Chowk Stage", "Dantakali Lawn"],
    "Janakpur": ["Janaki Mandir Grounds", "Mithila Hall"],
}

# --- reviews -----------------------------------------------------------------
#
# Split by rating so the text and the number agree. Reviews that praise a band
# five stars while the score says two are the fastest way to make seeded data
# feel obviously fake.

REVIEWS_GREAT = [
    "Absolutely made the night. Read the room perfectly and kept the floor full until close.",
    "Turned up early, soundchecked without fuss, played a blinder. Would book again tomorrow.",
    "Our guests are still talking about the last set. Learned our first dance exactly as asked.",
    "Professional from the first email to the last song. Worth every rupee.",
    "Better live than anything I'd heard beforehand. The whole hall was up by the third song.",
    "Handled a delayed start with total grace and still finished on time. Brilliant.",
]

REVIEWS_GOOD = [
    "Really solid set and a lovely bunch of people. Slightly loud for the room but we sorted it.",
    "Good energy and a well-judged setlist. Setup took a little longer than planned.",
    "Exactly what we wanted for the evening. Communication in the run-up could have been quicker.",
    "Played well and the crowd enjoyed it. A couple of songs felt like filler.",
    "Very good, would recommend. Bring your own extension leads, they were short a couple.",
]

REVIEWS_MIXED = [
    "Fine performance but arrived late, which put the whole schedule back half an hour.",
    "The music was good; the communication beforehand was hard work.",
    "Decent set, though not really the style we'd agreed on in advance.",
    "Played the hours we paid for. Not much stage presence for the money.",
]

REVIEWS_POOR = [
    "Turned up an hour late with no message. The set itself was ordinary.",
    "Not what was described on the profile. Wouldn't book again.",
]

# --- posts -------------------------------------------------------------------

POST_TEMPLATES = [
    ("Friday at {venue}", "Packed room, three encores, and the PA held up. Thanks to everyone who came out. Taking bookings for {month} now."),
    ("New set, same racket", "We've rewritten about half the set over the winter. Two new originals in there and a cover nobody expects. Come and tell us if it works."),
    ("Wedding season is filling up", "{month} is almost gone and we're into the month after. If you're planning something, message early — we only take one a weekend."),
    ("Recording week", "Four days in a room in {city} tracking the EP. Everything live, no clicks, mistakes left in. Out in the spring."),
    ("Thank you {city}", "Second time playing here this year and it was even better than the first. Same time next year, hopefully."),
    ("We're on the lookout", "Our bass player is moving abroad in the autumn, so we're quietly looking. If you play and you're in {city}, get in touch."),
]

MONTHS = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
]

# --- event types -------------------------------------------------------------

EVENT_TYPES = [
    "Wedding", "Corporate", "Festival", "Birthday",
    "Club Night", "Open Mic", "Charity Gala", "Restaurant",
]

# How likely each event type is. Weddings and restaurant residencies dominate a
# real Nepali booking calendar; charity galas are rare.
EVENT_WEIGHTS = [0.30, 0.14, 0.08, 0.12, 0.13, 0.06, 0.03, 0.14]
