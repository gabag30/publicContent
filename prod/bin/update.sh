#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${HERE}/.."
COMPOSE_FILE="$ROOT/compose.yml"

PROJECT="${PROJECT:-localai}"
PROFILE="${PROFILE:-cpu}"

echo "Pulling upstream bases (if compose needs them) and recreating..."
docker compose --project-directory "$ROOT" -p "$PROJECT" -f "$COMPOSE_FILE" --profile "$PROFILE" pull || true
docker compose --project-directory "$ROOT" -p "$PROJECT" -f "$COMPOSE_FILE" --profile "$PROFILE" up -d --no-build

echo "Update completed."

