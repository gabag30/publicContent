#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${REGISTRY:-}" ]]; then
  echo "Set REGISTRY to your container registry (e.g., ghcr.io/yourorg)" >&2
  exit 1
fi
TAG="${TAG:-latest}"
IMG_PREFIX="$REGISTRY/local"

for name in prod-postgres-pgvector prod-pgadmin prod-open-webui prod-n8n prod-ollama; do
  docker push "$IMG_PREFIX/$name:$TAG"
done

