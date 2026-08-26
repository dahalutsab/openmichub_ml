# OpenMicHub

Artist booking marketplace — organizers find and book performers, artists manage availability
and earnings. Spring Boot 3.3 API + Angular 19 client.

```
open_mic_hub_service/   Spring Boot API (Java 21)
open_mic_hub_ui/        Angular 19 client
scripts/                developer helpers
```

## Running locally

Requires Docker. A local JDK 21 and Node are optional — the API can run in a container.

**1. Database**

```bash
docker compose up -d          # postgres 16 on :5432
```

**2. Secrets and signing keys**

```bash
cp .env.example .env          # then fill in the values you need
./scripts/generate-keys.sh    # RSA keypair for access tokens
```

`.env` and `*.pem` are gitignored. Nothing in this repo carries a real credential — the service
reads everything through `${VAR}` placeholders in `application-local.yml`. With an empty `.env`
the app still boots; payment, mail and the chat assistant stay inert until their keys are set.

**3. API**

```bash
docker run --rm --network host \
  --env-file .env \
  -v "$PWD/open_mic_hub_service":/app \
  -v openmichub_m2:/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-21 mvn spring-boot:run
```

Or natively, with JDK 21 on the path: `cd open_mic_hub_service && ./mvnw spring-boot:run`.
Lombok does not support JDK 24+, so JDK 21 is required for a native build.

**4. Client**

```bash
cd open_mic_hub_ui && npm install && npm start
```

| | |
|---|---|
| API | http://localhost:8181 |
| Swagger | http://localhost:8181/v1/swagger |
| Client | http://localhost:4200 |

## Notes

- The initial admin account is only created when `ADMIN_PASSWORD` is set. There is no default
  password.
- Uploaded media is written to `UPLOAD_DIR` (default `./uploads`) and served from `/media/**`.
- Schema is managed by Hibernate `ddl-auto: update`. Migrations are not yet in place.
