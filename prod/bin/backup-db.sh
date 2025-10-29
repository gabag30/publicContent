#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${HERE}/.."
COMPOSE_FILE="$ROOT/compose.yml"

PROJECT="${PROJECT:-localai}"
PROFILE="${PROFILE:-cpu}"

# Resolve DB params from env file if present
POSTGRES_ENV="$ROOT/env/postgres.env"
if [[ -f "$POSTGRES_ENV" ]]; then
  # shellcheck disable=SC1090
  source "$POSTGRES_ENV"
fi
DB_USER="${POSTGRES_USER:-postgres}"
DB_NAME="${POSTGRES_DB:-postgres}"

OUT_DIR="$ROOT/backups/postgres"
mkdir -p "$OUT_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="$OUT_DIR/${DB_NAME}-${STAMP}.sql.gz"

echo "Backing up database '$DB_NAME' to $OUT_FILE"
docker compose --project-directory "$ROOT" -p "$PROJECT" -f "$COMPOSE_FILE" --profile "$PROFILE" \
  exec -T postgres sh -lc "pg_dump -U ${DB_USER} -d ${DB_NAME} | gzip -c" > "$OUT_FILE"

echo "Backup complete."

