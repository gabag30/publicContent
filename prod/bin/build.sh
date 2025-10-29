#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${HERE}/.."

REGISTRY="${REGISTRY:-}"
TAG="${TAG:-latest}"
IMG_PREFIX="${REGISTRY:+$REGISTRY/}local"

echo "Building images (tag: ${TAG})"

docker build -t "$IMG_PREFIX/prod-postgres-pgvector:$TAG" "$ROOT/docker/postgres"
docker build -t "$IMG_PREFIX/prod-pgadmin:$TAG" "$ROOT/docker/pgadmin"
docker build -t "$IMG_PREFIX/prod-open-webui:$TAG" "$ROOT/docker/open-webui"
docker build -t "$IMG_PREFIX/prod-n8n:$TAG" "$ROOT/docker/n8n"
docker build -t "$IMG_PREFIX/prod-ollama:$TAG" "$ROOT/docker/ollama"

echo "Done. If pushing to a registry, run:"
echo "  REGISTRY=<your-registry> TAG=$TAG $HERE/push.sh"

