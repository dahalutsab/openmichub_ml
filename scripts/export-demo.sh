#!/usr/bin/env bash
#
# Packages the running demo — database, generated artwork and trained models —
# into a single directory somebody else can restore with import-demo.sh.
#
#     ./scripts/export-demo.sh [destination]
#
# Deliberately NOT included:
#
#   certs/   the JWT signing keypair. Sharing a private signing key means anyone
#            holding it can mint tokens for any account on any deployment that
#            trusts it. The recipient's stack generates its own on first run.
#   .env     your secrets, including the Khalti key.
#
# If you would rather not ship a snapshot at all, you do not have to: the seeder
# is deterministic, so `--seed 7` reproduces this exact catalogue from source.
# See "Sharing the demo" in the README.

set -euo pipefail

DEST="${1:-demo-export}"
PROJECT="${COMPOSE_PROJECT_NAME:-openmichub}"
DB_NAME="${POSTGRES_DB:-open_mic_hub}"
DB_USER="${DB_USERNAME:-postgres}"

require_container() {
  if ! docker ps --format '{{.Names}}' | grep -qx "$1"; then
    echo "error: $1 is not running. Start the stack with: docker compose up -d" >&2
    exit 1
  fi
}

require_container "${PROJECT}-postgres"

mkdir -p "$DEST"
echo "Exporting to $DEST/"

# --- database --------------------------------------------------------------
# Plain SQL rather than a custom-format dump: it restores with psql alone, so
# the recipient needs nothing beyond the containers they already have.
echo "  database ..."
docker exec "${PROJECT}-postgres" pg_dump -U "$DB_USER" -d "$DB_NAME" --clean --if-exists \
  > "$DEST/database.sql"

# --- volumes ---------------------------------------------------------------
# Read straight out of the named volumes with a throwaway container, so this
# works whether or not the services are up.
copy_volume() {
  local volume="$1" outfile="$2" label="$3"
  if ! docker volume inspect "$volume" >/dev/null 2>&1; then
    echo "  $label ... skipped (no volume $volume)"
    return
  fi
  echo "  $label ..."
  docker run --rm \
    -v "$volume":/source:ro \
    -v "$(cd "$(dirname "$DEST")" && pwd)/$(basename "$DEST")":/out \
    alpine:3.20 tar czf "/out/$outfile" -C /source .
}

copy_volume "${PROJECT}_uploads"  "uploads.tar.gz" "artist artwork and uploads"
copy_volume "${PROJECT}_mlmodels" "models.tar.gz"  "trained models"

# --- manifest --------------------------------------------------------------
echo "  manifest ..."
{
  echo "OpenMicHub demo export"
  echo "created: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "contents"
  docker exec "${PROJECT}-postgres" psql -U "$DB_USER" -d "$DB_NAME" -t -A -F': ' -c "
    SELECT 'artists', count(*) FROM artists
    UNION ALL SELECT 'users', count(*) FROM users
    UNION ALL SELECT 'bookings', count(*) FROM booking
    UNION ALL SELECT 'reviews', count(*) FROM review
    UNION ALL SELECT 'posts', count(*) FROM posts
    UNION ALL SELECT 'payments', count(*) FROM payment
    UNION ALL SELECT 'transactions', count(*) FROM transaction;"
  echo
  echo "models"
  docker run --rm -v "${PROJECT}_mlmodels":/m:ro alpine:3.20 ls -la /m 2>/dev/null | tail -n +2 || true
  echo
  echo "not included: certs (JWT keypair), .env (secrets)."
  echo "every seeded account signs in with: Admin@123"
} > "$DEST/MANIFEST.txt"

echo
du -sh "$DEST"/* 2>/dev/null || true
echo
echo "Done. Share the $DEST directory; restore it with:"
echo "    ./scripts/import-demo.sh $DEST"
