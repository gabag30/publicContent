#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${HERE}/.."
COMPOSE_FILE="$ROOT/compose.yml"

PROJECT="${PROJECT:-localai}"
PROFILE="${PROFILE:-cpu}"

echo "Stopping project '$PROJECT' (profile: $PROFILE)"
docker compose --project-directory "$ROOT" -p "$PROJECT" -f "$COMPOSE_FILE" --profile "$PROFILE" down

