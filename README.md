# OpenMicHub

Artist booking marketplace — organizers find and book performers, artists manage availability
and earnings. Spring Boot 3.3 API + Angular 19 client.

```
open_mic_hub_service/   Spring Boot API (Java 21)
open_mic_hub_ui/        Angular 19 client
ml_service/             FastAPI: semantic search + LightGBM ranker
scripts/                developer helpers
docker-compose.yml      Postgres + API + ML, one command
```

## Running the backend

Docker is the only requirement. No JDK, no Maven, no local Postgres.

```bash
cp .env.example .env          # set ADMIN_PASSWORD at minimum
docker compose up -d --build
```

That builds both service images, starts Postgres with pgvector, waits for it to accept
connections, generates the token-signing keypair on first run, creates the schema and seeds
roles. Everything is healthy in about twenty seconds.

```bash
docker compose logs -f api    # follow
docker compose ps             # health
docker compose down           # stop, keep data
docker compose down -v        # stop and wipe data, keys and uploads
```

With an empty `.env` the stack still boots; payment, mail and the chat assistant stay inert until
their keys are set. Only `ADMIN_PASSWORD` matters for a first run — without it, no admin account
is created.

### Client

```bash
cd open_mic_hub_ui && npm install && npm start
```

### Running the API outside Docker

Needs JDK 21 on the path — Lombok does not support JDK 24+.

```bash
docker compose up -d postgres        # database only
./scripts/generate-keys.sh           # RSA keypair
cd open_mic_hub_service && ./mvnw spring-boot:run
```

| | |
|---|---|
| API | http://localhost:8181 |
| Swagger | http://localhost:8181/v1/swagger |
| Health | http://localhost:8181/actuator/health |
| ML service | http://localhost:8000/docs |
| Client | http://localhost:4200 |

## Artist discovery

Search runs meaning-based retrieval over artist profiles, then a trained ranker orders the
results by fit for the request. Both endpoints are public.

```
GET /api/v1/discover/search?q=jazz trio for a corporate dinner&city=Kathmandu&budgetPerHour=8000
GET /api/v1/discover/recommendations?city=Pokhara&eventType=Wedding
```

They fall back to the plain artist listing if the ML service is unreachable, so discovery
degrades rather than breaking. The response `strategy` field says which ranker produced the
ordering.

### Suggestions that know who is asking

Both endpoints are public, but not anonymous. A signed-in visitor is their account; a visitor who
has not signed in is a random id their browser keeps and sends as `X-Visitor-Id` — not an IP
address, not a fingerprint. A request with neither is served normally and recorded nowhere.

**What they do is recorded.** Searches, browse filters and opened profiles go to
`user_interaction`, against the account or the browser; bookings were already in `booking`.
Profile views are de-duplicated within half an hour, an unfiltered browse is ignored, and writing
the row happens on another thread and can never fail a search. Signing in moves a browser's history
onto the account and starts a fresh visitor id. History no account claims is deleted after 90 days
by a nightly job in the API.

**What they were shown is recorded too.** Every ranked list gets a `requestId` and its artists are
logged in order to `discovery_impression`; a click posts back the list and the position it came
from, and is kept only if that list was served to that same caller. This is what lets an act shown
first and opened be told apart from one shown on every visit and never opened — and it is the
training label the search ranker has been missing.

**The ranking uses all of it.** The ML service builds a taste profile per person — account or
browser — and blends it into the ordering. Two things are tracked separately, because they answer
different questions: what someone usually books, and what they are searching for this week.
Averaging the two buries the second, so a browse retrieves from both in a stated proportion. Old
events decay rather than falling out of a window, a typed query keeps most of the say over its own
results, and someone with almost no history gets the ordinary ranking rather than a guess. One
search is enough to shape what comes next; one profile view is not.

On top of one person's history sit three things only the whole platform's behaviour can say:

- **Often picked alongside** — item-to-item collaborative filtering over bookings and profile
  views. Acts are close when the same people chose both, which crosses genres whenever real
  shortlists do. It feeds retrieval, scoring, a reason line naming the act it came from, and a
  strip on every artist profile.
- **In demand this month** — decayed booking requests and views, on a three-week half-life. It is
  what a visitor with no history sees first, instead of the catalogue sorted by rating.
- **Exposure** — an act shown near the top of lists constantly gets no help; one barely shown gets a
  small, bounded chance. An act a person keeps being shown and never opens gives way to others.

Ratings are shrunk towards the catalogue mean before ranking, so one 5-star review no longer
outranks forty at 4.6, and browse pages trade a little score for variety so the first screen is not
eight versions of the same act.

Each ranked artist comes back with the reason it was raised — "you have booked them before",
"often picked alongside The Velvet Club", "in demand this month" — most specific first, and the
cards show it. `GET /users/{id}/taste` and `GET /visitors/{id}/taste` on the ML service show what the
service believes about someone; `GET /signals` shows the platform-wide side.

To get a demo catalogue and trained models on a fresh install:

```bash
docker compose exec ml python -m training.seed_world --artists 300 --wipe
curl -X POST localhost:8000/embeddings/rebuild
docker compose exec ml python -m training.segment
curl -X POST localhost:8000/train -H 'content-type: application/json' -d '{"queries":5000}'
curl "localhost:8000/signals?refresh=true"
docker compose exec ml python -m training.evaluate_recs    # how well it recommends, measured
```

See [ml_service/README.md](ml_service/README.md) for the model, its features and how it is
evaluated.

### What is trained, and on what

Worth being exact about, because "trained" covers three different things here.

| Component | Fitted on | What that means |
|---|---|---|
| Profile embeddings | Real catalogue | Not trained. A pretrained sentence encoder runs over every artist's own profile text; the vectors are theirs. |
| Similar artists | Real catalogue | Not trained. Nearest neighbours by cosine distance over those vectors. |
| Segmentation | **Real catalogue** | Genuinely fitted here: k-means over the real profile embeddings, k chosen by sweeping silhouette and Davies-Bouldin. Re-fit whenever the catalogue changes. |
| Search ranker | **Synthetic** | LightGBM LambdaRank, fitted on query-artist pairs from an explicit generative process in `training/generate.py`, not on anything that happened on the platform. |

The ranker is the honest gap. It is a real model — it learns weights, it is evaluated on a held-out
split, and its features are computed from real artists at query time — but the *preferences* it
learned came from a simulation, because the platform has no record of what anyone actually clicked
or booked after searching.

Closing that needs a search interaction log: what was searched, what was shown, what was clicked,
what turned into a booking request. Nothing in the seeded data substitutes for it. Booking outcomes
will not do either — over 99% of seeded bookings resolve the same way, and real ones would need to
be plentiful and varied before they carried signal.

Until that log exists, describe the ranker as bootstrapped on synthetic preferences rather than
trained on platform data. The distinction matters and `training/generate.py` states its whole
generative process in the first thirty lines precisely so the claim can be checked.

One part of that simulation is no longer a guess. How well the text embedding separates a
genre match from a non-match is measured against the real catalogue by `training/calibrate.py`
and fed back into the generator, so the ranker is at least fitted against an encoder as noisy as
the one it will actually be given. See `ml_service/README.md` for the measurement.

## Signing in

Email and password, or Google or Facebook. Social sign-in is **off until you configure it**: with
no credentials set the buttons stay hidden, the handshake endpoints refuse, and password login is
untouched.

### Turning it on

Register an OAuth client with each provider you want, using these redirect URIs:

```
http://localhost:8181/login/oauth2/code/google
http://localhost:8181/login/oauth2/code/facebook
```

| Provider | Where |
|---|---|
| Google | console.cloud.google.com -> APIs & Services -> Credentials -> OAuth client ID -> Web application |
| Facebook | developers.facebook.com -> My Apps -> Create App -> Facebook Login -> Settings |

Put the pairs in `.env` and restart. That is the only step — the sign-in page asks the backend
which providers it can use (`GET /api/v1/auth/providers`) and draws a button for each, so a
provider you have not configured is never offered:

```bash
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
FACEBOOK_CLIENT_ID=...
FACEBOOK_CLIENT_SECRET=...
```

Set both halves of a pair or neither — a client id without its secret fails fast at startup rather
than offering a button that cannot work. Configure only Google and only a Google button appears. Facebook requires HTTPS for anyone outside your app's own
test users, so localhost works for you and not for them.

### What happens to the account

A social sign-in creates an `ORGANIZER`, the same role self-registration creates, and marks the
address verified because the provider already confirmed it. There is no password on the account,
and a database constraint permits that only for provider-backed rows.

Accounts are matched on the provider's own subject id rather than on the email address, so someone
who changes their address at Google keeps their account here instead of silently acquiring a
second one.

If the address already belongs to a password account, the two are **linked** — but only when the
provider states it has verified the address. Google reports this per-account and an unverified one
is refused. Without that check, anyone who could set an arbitrary address on a provider account
could take over an existing user here. An address already claimed by a *different* provider is
refused outright rather than reassigned.

## Tests

```bash
# Backend — 98 tests
docker compose up -d postgres                     # optional; see below
cd open_mic_hub_service && ./mvnw test

# Frontend — 80 tests
cd open_mic_hub_ui && CHROME_BIN=$(which chromium) npx ng test --watch=false --browsers=ChromeHeadless

# ML — 56 tests, inside the running container
docker compose exec ml python -m pytest tests -q
```

The JDK on a current Arch box is newer than Lombok supports, so the backend suite usually runs in
a container instead:

```bash
docker run --rm -v "$PWD/open_mic_hub_service":/app -v "$HOME/.m2":/root/.m2 --network host \
  -w /app maven:3.9-eclipse-temurin-21 mvn test
```

Everything except the context test is a unit test with no external dependency.
`OpenMicHubServiceApplicationTests` boots the whole application — which runs every migration and
then has Hibernate validate its mappings against the result — so it needs a real Postgres, and
`--network host` is what lets the container reach the one `docker compose` started. Without a
database it **skips** rather than fails, so the suite stays green either way. An in-memory database
would not do: the migrations use partial indexes and pgvector.

What is covered, and why those parts:

| Area | Covers |
|---|---|
| `BookingServiceImplTest` | Pricing (whole, part and sub-hour slots), and every refusal — reversed times, past dates, outside the availability window, blackout dates, double bookings |
| `LocalTimeDeserializerTest` | Both time formats, and a clear rejection of something that is not a time |
| `OAuth2AccountServiceTest` | Which account a social profile resolves to, including the linking guards that stop an unverified address taking over an existing user |
| `OAuth2UserDetailsTest` | Google's OIDC claims versus Facebook's Graph fields, and that an absent verification flag never reads as verified |
| `ProfileCompletionServiceTest` | The one-time setup answer, and that it stays one-time |
| `TransactionServiceImplTest` | Wallet arithmetic, the idempotent booking credit, that a withdrawal reserves funds up front — and the payout queue: that it is queried rather than filtered out of the ledger, ordered newest first, carries a status, and that declining returns the held funds exactly once |
| `ProfileCompletenessServiceTest` | The artist checklist: that availability alone decides bookability, and that a placeholder bio or a zero rate does not count as done |
| `InteractionServiceImplTest` | What reaches the interaction log and what does not: an anonymous visitor, a blank query and an unfiltered browse are never recorded, a reloaded profile counts once, and a write that fails stays invisible to the person searching |
| `test_personalization.py` (ML) | Recency decay, how a history turns into genre, city and price tendencies, the floor below which nobody is personalised, and that a stronger affinity reorders a list without overturning the model's ranking. Also the four ways a fresh search used to get lost: falling below the floor, being averaged into a long booking history, being scaled through the wrong cosine band, and arriving after the profile the next page reads had already been cached |
| `social.component.spec.ts` | The sign-in fragment, including the malformed shapes — an empty role list is refused rather than stored |
| `complete-profile.component.spec.ts` | The submit guard and the payload it builds |
| `login.component.social.spec.ts` | That only the providers the backend reports are offered |

The generated `should create` specs are kept and now pass. They had never run: all forty failed on
a missing provider the first time the suite was executed, and one of them did not compile, which
stopped the whole run. They prove only that a component can be constructed — thin, but that does
catch a constructor asking for something its module never provides, which is a real way to break a
lazy-loaded route. `src/app/testing/test-providers.ts` is what makes them cheap to keep.

## Finishing an artist profile

An artist's dashboard shows what is still missing, scored out of 100 and weighted by how much each
item affects being booked rather than by how many fields are blank. `GET
/api/v1/artist/profile-completeness` returns the list; the card disappears once everything is done.

| | Weight | Why it is worth that much |
|---|---|---|
| Weekly availability | 25 | **Blocking.** A booking request is checked against the hours published for that weekday and refused when there are none, so an empty calendar means the artist cannot be booked at all — whatever else the profile says |
| Styles performed | 20 | The strongest signal in search: organizers filter by it, and it carries the largest share of the ranking model's gain |
| Bio | 15 | Search matches on meaning against the artist's own words, so a short or missing one gives it nothing to work with |
| Hourly rate | 15 | Results are scored on how the rate fits the organizer's budget; zero reads as unstated, not as free |
| Profile photo | 10 | First thing on every card and search result |
| Location | 8 | Distance is part of the ranking, and organizers filter by city |
| First post | 7 | What an organizer looks at once the profile has caught their eye |

The availability item is separated in the UI rather than listed with the rest, because it is a
different kind of gap: not "less visible" but "cannot be booked". The weights are a judgement about
what gets someone booked, not a measurement, and they are stated in
`ProfileCompletenessService` so they can be argued with.

## Getting paid

An artist's earnings accumulate in a wallet as bookings are paid for. Withdrawing is a request,
not a transfer: the artist asks, the platform owner pays it out through Khalti, and only then does
the money leave.

| Step | Who | Where |
|---|---|---|
| Raise a request | Artist | Wallet → **Withdraw funds**. `POST /api/v1/transactions/withDraw` |
| See its progress | Artist | Wallet ledger — *Awaiting payout*, *Paid*, or *Declined · funds returned* |
| See the queue | Any admin | Admin → **Coin transactions**, at the top. `GET /api/v1/transactions/withdrawals` |
| Pay it out | Owner only | **Pay out** on the queue, which opens Khalti. `POST /api/v1/artist/withdraw` |
| Refuse it | Owner only | **Decline** on the queue. `POST /api/v1/transactions/withdrawals/{id}/decline` |

The amount is **held the moment the request is raised** — debited from the balance, with the
transaction left `PENDING`. That is what stops an artist filing the same withdrawal repeatedly and
every copy passing the balance check. A payout that completes only flips the status; one that fails
at the gateway, or is declined, returns the funds.

Staff (`ADMIN`) can read the queue but not act on it: moving money is the `SUPER_ADMIN` role alone,
enforced server-side and reflected in the buttons the UI offers.

## Roles

| Role | Can do |
|---|---|
| `SUPER_ADMIN` | Payouts, refunds, role assignment, configuration |
| `ADMIN` | Moderation, read-only access to financial records |
| `ARTIST` | Profile, availability, posts, earnings wallet |
| `ORGANIZER` | Books and pays for artists |
| `USER` | Browses artists, reviews their own bookings |

Self-registration creates an `ORGANIZER`. Existing databases are migrated on startup: `ADMIN`
becomes `SUPER_ADMIN` and `USER` becomes `ORGANIZER`, with all assignments preserved.

## Demo accounts

Created by the seed data and `scripts/demo-accounts.sql`. **Local development only** — these are
throwaway logins for a throwaway database, and none of them should ever exist in an environment
that faces the internet.

Every account below uses the same password:

```
Admin@123
```

| Role | Email | Sees |
|---|---|---|
| `ADMIN` | `admin@demo.openmichub.local` | Admin dashboard, users, transactions, payments |
| `ORGANIZER` | `booker@demo.openmichub.local` | Booker dashboard, bookings, payment history |
| `ARTIST` | see below | Artist dashboard, calendar, wallet, posts |

There are **300 seeded artists**. Their email is their slug — the same string that appears in
their public URL — so `/artists/amber-machine` signs in as `amber-machine@seed.openmichub.local`.
List them with:

```bash
docker compose exec postgres psql -U postgres -d open_mic_hub \
  -c "select u.email, a.stage_name, a.rating from users u
      join artists a on a.user_id = u.id order by a.rating desc nulls last limit 20;"
```

A few to start with:

| Email | Stage name | City | Rating |
|---|---|---|---|
| `the-machhapuchhre-assembly@seed.openmichub.local` | The Machhapuchhre Assembly | Bhaktapur | 5.00 |
| `amber-machine@seed.openmichub.local` | Amber Machine | Kathmandu | 5.00 |
| `suraj-karki@seed.openmichub.local` | Suraj Karki | Kathmandu | 5.00 |
| `bhairav-company@seed.openmichub.local` | Bhairav Company | Bhaktapur | 5.00 |

Roughly 47 artists have no reviews and so no rating at all. That is deliberate — a new act should
read as new rather than as mediocre — and it is worth having one open while working on the
profile page.

`SUPER_ADMIN` is **not** in this list. It is created from `ADMIN_EMAIL`/`ADMIN_PASSWORD` in your
`.env` (default email `admin@openmichub.com`), so its password is whatever you set — deliberately,
since it is the only role that can move money.

To rebuild the whole demo world after a `docker compose down -v`:

```bash
docker compose exec ml python -m training.seed_world --artists 300 --wipe
curl -X POST localhost:8000/embeddings/rebuild
docker compose exec ml python -m training.segment
```

That replaces every row the platform owns, so it asks for `--wipe` explicitly. The seed is fixed,
so the same command produces the same catalogue — the same names, the same bookings, the same
artwork — on any machine.

## Sharing the demo

Two ways, depending on whether the other person can wait two minutes.

**Reproduce it from source.** The seeder is deterministic, so they need nothing but the repository:

```bash
docker compose up -d --build
docker compose exec ml python -m training.seed_world --artists 300 --wipe
curl -X POST localhost:8000/embeddings/rebuild
docker compose exec ml python -m training.segment
```

They end up with a byte-for-byte identical catalogue. Nothing large travels, and the data stays
readable in version control as the code that produces it.

**Ship a snapshot.** For a demo machine, or when the ranker's weights matter and you do not want
them retrained:

```bash
./scripts/export-demo.sh demo-export     # about 10 MB
# ... send demo-export/ ...
./scripts/import-demo.sh demo-export     # on the other machine, containers up first
```

The export carries the database (including the embeddings and segment assignments), the generated
artwork, and the trained model files, plus a `MANIFEST.txt` saying what is in it.

Two things are deliberately left out. **`certs/`**, the JWT signing keypair — sharing a private
signing key lets anyone holding it mint tokens for any account on any deployment that trusts it,
so the receiving stack generates its own. And **`.env`**, which holds your Khalti key and mail
password. Import refuses to overwrite a database that already has artists unless you pass
`--force`.

Repeated failed logins are rate-limited, so a script that guesses passwords will start getting
`429` after a few tries. Wait a minute rather than hammering it.

## Notes

- The initial admin account is only created when `ADMIN_PASSWORD` is set. There is no default
  password.
- Uploaded media is written to `UPLOAD_DIR` (default `./uploads`) and served from `/media/**`.
  Under Docker it lives on the `uploads` volume.
- Token-signing keys live on the `certs` volume and are generated on first run, so each
  deployment has its own pair and restarts do not invalidate issued tokens.
- The container runs as uid 1001, never root.
## Database schema

Owned by Flyway, in `open_mic_hub_service/src/main/resources/db/migration`. Hibernate is set to
`validate`, so it checks its entity mappings against what the migrations built and fails fast on
drift rather than silently altering tables.

```
V1__baseline_schema.sql      the schema as ddl-auto had been generating it
V2__integrity_and_indexes.sql unique constraints and query indexes
```

To change the schema, add a new `V{n}__description.sql`. Never edit an applied migration — Flyway
checksums them and will refuse to start.

A database created before Flyway is adopted automatically: `baseline-on-migrate` stamps it at V1
without re-running the baseline over live tables, then applies everything after.

The `ml` schema is separate and belongs to `ml_service`, which creates it idempotently. `public`
is the API's and is Flyway's alone — a second writer there makes a fresh database look non-empty,
at which point Flyway baselines it instead of building it.
