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

To get a demo catalogue and trained models on a fresh install:

```bash
docker compose exec ml python -m training.seed_world --artists 300 --wipe
curl -X POST localhost:8000/embeddings/rebuild
docker compose exec ml python -m training.segment
curl -X POST localhost:8000/train -H 'content-type: application/json' -d '{"queries":5000}'
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
