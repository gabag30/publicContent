#!/usr/bin/env bash
set -euo pipefail

echo "Running custom Postgres init using env vars..."
psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname   "$POSTGRES_DB" \
  -v username="${N8N_DB_USER}" \
  -v userpass="${N8N_DB_PASSWORD}" \
  -v dbname="${N8N_DB_NAME}" \
  -f /docker-entrypoint-initdb.d/init.sql.template

echo "Custom Postgres init completed."
