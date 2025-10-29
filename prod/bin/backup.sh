#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${HERE}/.."
COMPOSE_FILE="$ROOT/compose.yml"

PROJECT="${PROJECT:-localai}"
PROFILE="${PROFILE:-cpu}"

COMPOSE=(docker compose --project-directory "$ROOT" -p "$PROJECT" -f "$COMPOSE_FILE" --profile "$PROFILE")

mkdir -p "$ROOT/n8n/backup/workflows" "$ROOT/n8n/backup/credentials" "$ROOT/pgadmin-backup" "$ROOT/backups/postgres"

echo "Exporting n8n workflows/credentials..."
if ${COMPOSE[@]} ps -q n8n >/dev/null 2>&1 && [ -n "$(${COMPOSE[@]} ps -q n8n)" ]; then
  ${COMPOSE[@]} exec -T n8n sh -lc \
    'mkdir -p /backup/workflows /backup/credentials \
     && n8n export:workflow --all --separate --output=/backup/workflows \
     && n8n export:credentials --all --separate --output=/backup/credentials' || {
       echo "Warning: n8n export failed" >&2; }
else
  echo "n8n is not running; skipping export"
fi

echo "Backing up pgAdmin volume..."
if docker volume inspect ${PROJECT}_pgadmin_data >/dev/null 2>&1; then
  docker run --rm -v ${PROJECT}_pgadmin_data:/from:ro -v "$ROOT/pgadmin-backup":/to \
    alpine:3.20 sh -lc 'mkdir -p /to && rm -rf /to/* && cp -a /from/. /to/' || \
    echo "Warning: pgAdmin backup failed" >&2
else
  echo "pgAdmin volume not found; skipping"
fi

echo "Done."

