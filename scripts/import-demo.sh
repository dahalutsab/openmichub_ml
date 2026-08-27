#!/usr/bin/env bash
#
# Restores a demo exported by export-demo.sh: database, generated artwork and
# trained models.
#
#     docker compose up -d          # containers first; this fills them
#     ./scripts/import-demo.sh demo-export
#
# This REPLACES the contents of the target stack. It refuses to run without
# --force if the database already holds artists, because restoring over
# somebody's work by accident is not recoverable.

set -euo pipefail

SRC="${1:-demo-export}"
FORCE="${2:-}"
PROJECT="${COMPOSE_PROJECT_NAME:-openmichub}"
DB_NAME="${POSTGRES_DB:-open_mic_hub}"
DB_USER="${DB_USERNAME:-postgres}"

if [ ! -f "$SRC/database.sql" ]; then
  echo "error: $SRC/database.sql not found. Point this at an export directory." >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "${PROJECT}-postgres"; then
  echo "error: ${PROJECT}-postgres is not running. Start it with: docker compose up -d" >&2
  exit 1
fi

existing=$(docker exec "${PROJECT}-postgres" psql -U "$DB_USER" -d "$DB_NAME" -t -A \
  -c "SELECT count(*) FROM artists" 2>/dev/null || echo 0)

if [ "${existing:-0}" -gt 0 ] && [ "$FORCE" != "--force" ]; then
  echo "refusing to import: the database already has $existing artists." >&2
  echo "Re-run with --force to replace them:" >&2
  echo "    ./scripts/import-demo.sh $SRC --force" >&2
  exit 1
fi

echo "Restoring from $SRC/"

# --- database --------------------------------------------------------------
# The dump carries DROP statements, so ON_ERROR_STOP would trip on the first
# object that does not exist yet in a fresh database. Errors are still printed.
echo "  database ..."
docker exec -i "${PROJECT}-postgres" psql -U "$DB_USER" -d "$DB_NAME" -q < "$SRC/database.sql" \
  > /dev/null

restore_volume() {
  local archive="$1" volume="$2" label="$3"
  if [ ! -f "$SRC/$archive" ]; then
    echo "  $label ... skipped (no $archive)"
    return
  fi
  echo "  $label ..."
  docker volume create "$volume" >/dev/null
  docker run --rm \
    -v "$volume":/target \
    -v "$(cd "$SRC" && pwd)":/in:ro \
    alpine:3.20 sh -c "rm -rf /target/* && tar xzf /in/$archive -C /target"
}

restore_volume "uploads.tar.gz" "${PROJECT}_uploads"  "artist artwork and uploads"
restore_volume "models.tar.gz"  "${PROJECT}_mlmodels" "trained models"

echo "  restarting services ..."
docker compose -p "$PROJECT" restart api ml >/dev/null 2>&1 || \
  echo "  (restart them yourself: docker compose restart api ml)"

echo
echo "Done. Signing keys are NOT part of an export — this stack generated its own,"
echo "so any tokens issued before the import are no longer valid. Sign in again."
echo "Every seeded account uses: Admin@123"
