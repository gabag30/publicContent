#!/usr/bin/env bash
set -e

COMPOSE_ARGS=("-p" "localai" "-f" "docker-compose.yml" "-f" "docker-compose.override.private.yml" "--profile" "cpu")

# Ensure host backup directories exist and are writable by container user
mkdir -p ./n8n/backup/workflows ./n8n/backup/credentials
sudo chown -R $(whoami):"domain users" ./n8n
#chmod -R 777 ./n8n/backup || true

echo "Exporting n8n workflows and credentials before shutdown..."
if docker compose "${COMPOSE_ARGS[@]}" ps -q n8n >/dev/null 2>&1 && [ -n "$(docker compose "${COMPOSE_ARGS[@]}" ps -q n8n)" ]; then
  if ! docker compose "${COMPOSE_ARGS[@]}" exec -T n8n sh -lc \
    'mkdir -p /backup/workflows /backup/credentials \
     && n8n export:workflow --all --separate --output=/backup/workflows \
     && n8n export:credentials --all --separate --output=/backup/credentials'; then
    echo "Warning: n8n export failed; proceeding with shutdown." >&2
  else
    echo "n8n export completed to ./n8n/backup (workflows, credentials)."
  fi
else
  echo "n8n container not running; skipping export."
fi

echo "Stopping stack..."
docker compose "${COMPOSE_ARGS[@]}" down
echo "Done."

# Backup pgAdmin named volume to local folder for versioning
echo "Backing up pgAdmin data volume to ./pgadmin-backup..."
mkdir -p ./pgadmin-backup
if docker volume inspect localai_pgadmin_data >/dev/null 2>&1; then
  docker run --rm \
    -v localai_pgadmin_data:/from:ro \
    -v "$(pwd)/pgadmin-backup":/to \
    alpine:3.20 sh -lc 'mkdir -p /to && rm -rf /to/* && cp -a /from/. /to/' && \
  echo "pgAdmin backup completed." || echo "Warning: pgAdmin backup failed." >&2
else
  echo "pgAdmin volume not found; skipping backup."
fi
