#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $(basename "$0") <path-to-sql[.gz]>" >&2
  exit 1
fi

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${HERE}/.."
COMPOSE_FILE="$ROOT/compose.yml"

PROJECT="${PROJECT:-localai}"
PROFILE="${PROFILE:-cpu}"
FILE="$1"

POSTGRES_ENV="$ROOT/env/postgres.env"
if [[ -f "$POSTGRES_ENV" ]]; then
  # shellcheck disable=SC1090
  source "$POSTGRES_ENV"
fi
DB_USER="${POSTGRES_USER:-postgres}"
DB_NAME="${POSTGRES_DB:-postgres}"

echo "Restoring database '$DB_NAME' from $FILE"
if [[ "$FILE" == *.gz ]]; then
  gunzip -c "$FILE" | docker compose --project-directory "$ROOT" -p "$PROJECT" -f "$COMPOSE_FILE" --profile "$PROFILE" \
    exec -T postgres sh -lc "psql -U ${DB_USER} -d ${DB_NAME}"
else
  cat "$FILE" | docker compose --project-directory "$ROOT" -p "$PROJECT" -f "$COMPOSE_FILE" --profile "$PROFILE" \
    exec -T postgres sh -lc "psql -U ${DB_USER} -d ${DB_NAME}"
fi

echo "Restore complete."

