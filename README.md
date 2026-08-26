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

To get a demo catalogue and a trained model on a fresh install:

```bash
docker compose exec ml python -m training.seed_db --artists 200
curl -X POST localhost:8000/embeddings/rebuild
curl -X POST localhost:8000/train -H 'content-type: application/json' -d '{"queries":5000}'
```

See [ml_service/README.md](ml_service/README.md) for the model, its features and how it is
evaluated.

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

## Notes

- The initial admin account is only created when `ADMIN_PASSWORD` is set. There is no default
  password.
- Uploaded media is written to `UPLOAD_DIR` (default `./uploads`) and served from `/media/**`.
  Under Docker it lives on the `uploads` volume.
- Token-signing keys live on the `certs` volume and are generated on first run, so each
  deployment has its own pair and restarts do not invalidate issued tokens.
- The container runs as uid 1001, never root.
- Schema is managed by Hibernate `ddl-auto: update`. Migrations are not yet in place.
