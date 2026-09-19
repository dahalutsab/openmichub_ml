#!/usr/bin/env bash
#
# Fills a running stack with the demo world and trains everything on it, in the
# order that matters: seed, embed, segment, rank, then the platform signals.
#
#     docker compose up -d --build     # containers first; this fills them
#     ./scripts/seed-demo.sh           # 300 artists, cover art, trained models
#     ./scripts/seed-demo.sh --artists 60 --no-art    # quicker and smaller
#
# Windows: scripts\seed-demo.ps1 does the same thing from PowerShell.
#
# Every step runs inside the ml container — the HTTP calls included — so the
# host needs nothing but Docker, and the ports in .env do not matter.
#
# Seeding replaces every platform row, so on a database that already has
# artists it refuses unless you pass --wipe. On one that has none there is
# nothing to lose and the flag is not needed. The wipe takes the super-admin
# account with it; the API is restarted at the end, which recreates it from
# ADMIN_EMAIL / ADMIN_PASSWORD.

set -euo pipefail

ARTISTS=300
SEED=7
NO_ART=""
WIPE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --artists) ARTISTS="$2"; shift 2 ;;
    --seed)    SEED="$2"; shift 2 ;;
    --no-art)  NO_ART="--no-art"; shift ;;
    --wipe)    WIPE="--wipe"; shift ;;
    -h|--help) sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "error: unknown option $1 (try --help)" >&2; exit 1 ;;
  esac
done

PROJECT="${COMPOSE_PROJECT_NAME:-openmichub}"
DB_NAME="${POSTGRES_DB:-open_mic_hub}"
DB_USER="${DB_USERNAME:-postgres}"
ML="${PROJECT}-ml"
PG="${PROJECT}-postgres"

for c in "$PG" "$ML"; do
  if ! docker ps --format '{{.Names}}' | grep -qx "$c"; then
    echo "error: $c is not running. Start the stack with: docker compose up -d --build" >&2
    exit 1
  fi
done

existing=$(docker exec "$PG" psql -U "$DB_USER" -d "$DB_NAME" -t -A \
  -c "SELECT count(*) FROM artists" 2>/dev/null || echo 0)

if [ "${existing:-0}" -gt 0 ] && [ -z "$WIPE" ]; then
  echo "refusing to seed: the database already has $existing artists." >&2
  echo "Re-run with --wipe to delete every platform row and start over:" >&2
  echo "    ./scripts/seed-demo.sh --wipe" >&2
  exit 1
fi

# POSTs to the ML service from inside its own container. The body goes in on
# stdin so no shell ever has to quote JSON.
ml_post() {
  local body="${2:-}"
  [ -n "$body" ] || body='{}'
  printf '%s' "$body" | docker exec -i "$ML" curl -fsS -X POST "http://localhost:8000$1" \
    -H 'content-type: application/json' --data-binary @- > /dev/null
}

echo "[1/5] seeding $ARTISTS artists (seed $SEED) ..."
# The seeder always wants --wipe; the check above is what decided it is safe.
docker exec "$ML" python -m training.seed_world --artists "$ARTISTS" --seed "$SEED" $NO_ART --wipe

echo "[2/5] embedding the catalogue ..."
ml_post /embeddings/rebuild

echo "[3/5] fitting the segmentation ..."
docker exec "$ML" python -m training.segment

echo "[4/5] training the search ranker (a minute or two) ..."
ml_post /train '{"queries":5000}'

echo "[5/5] building the platform signals ..."
docker exec "$ML" curl -fsS "http://localhost:8000/signals?refresh=true" > /dev/null

# Brings the super-admin back: the API creates it at startup when it is missing.
docker restart "${PROJECT}-api" > /dev/null

artist=$(docker exec "$PG" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c \
  "SELECT email FROM users WHERE email LIKE '%@seed.openmichub.local' ORDER BY id LIMIT 1")

echo
echo "Done. Every seeded account signs in with: Admin@123"
echo "    admin@demo.openmichub.local    ADMIN"
echo "    booker@demo.openmichub.local   ORGANIZER"
echo "    $artist   ARTIST (each artist is <slug>@seed.openmichub.local)"
