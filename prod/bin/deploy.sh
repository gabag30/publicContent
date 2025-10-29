#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${HERE}/.."
COMPOSE_FILE="$ROOT/compose.yml"

PROJECT="${PROJECT:-localai}"
PROFILE="${PROFILE:-cpu}"
TAG="${TAG:-latest}"
REGISTRY="${REGISTRY:-}"
IMG_PREFIX="${REGISTRY:+$REGISTRY/}local"

# Create real env files from examples if missing
mkdir -p "$ROOT/env"
[[ -f "$ROOT/env/n8n.env" ]] || cp "$ROOT/env/n8n.env.example" "$ROOT/env/n8n.env"
[[ -f "$ROOT/env/pgadmin.env" ]] || cp "$ROOT/env/pgadmin.env.example" "$ROOT/env/pgadmin.env"

# Parameterize images via env vars
export POSTGRES_IMAGE="$IMG_PREFIX/prod-postgres-pgvector:$TAG"
export PGADMIN_IMAGE="$IMG_PREFIX/prod-pgadmin:$TAG"
export OPENWEBUI_IMAGE="$IMG_PREFIX/prod-open-webui:$TAG"
export N8N_IMAGE="$IMG_PREFIX/prod-n8n:$TAG"
export OLLAMA_IMAGE="$IMG_PREFIX/prod-ollama:$TAG"
export OLLAMA_AMD_IMAGE="$IMG_PREFIX/prod-ollama:rocm"

echo "Deploying (project: $PROJECT, profile: $PROFILE)"

docker compose \
  --project-directory "$ROOT" \
  -p "$PROJECT" \
  -f "$COMPOSE_FILE" \
  --profile "$PROFILE" \
  up -d --pull=missing --no-build

echo "Deployment completed."

