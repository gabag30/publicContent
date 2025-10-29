#FOR TESTING AND LEARNING POURPOSES ONLY!!!!! 

#DON´T USE DOCKER COMPOSE IN PRODUCTION!!!!!!

# Docker Environment Documentation

This repository provides a Docker Compose stack to run a local AI workspace built around n8n, Ollama, Open WebUI, Postgres, and pgAdmin, with sensible defaults for CPU and GPU setups (NVIDIA/AMD), plus import/backup helpers.

## What's Inside

- n8n: Automation platform with AI components.
- Ollama: Local LLM runtime (CPU or GPU via profiles).
- Open WebUI: Chat UI that can call Ollama and trigger n8n.
- Postgres: Database used by n8n (and optionally your workflows).
- pgAdmin: Postgres admin UI, with backup/restore to a host folder.
- Helper jobs: n8n import on first start; pgAdmin restore on start; Ollama model pulls.

## Prerequisites

- Docker Engine 24+ and Docker Compose v2.
- Optional: NVIDIA Container Toolkit for NVIDIA GPUs; ROCm-capable host for AMD GPUs.
- A `.env` file with required secrets (see Environment Variables).

## Files and Structure

- `docker-compose.yml`: Main stack definition, services, volumes, and profiles.
- `docker-compose.override.private.yml`: Binds service ports to localhost-only.
- `.env`: Secrets and configuration variables (not committed).
- `start.sh`: Convenience script to start the CPU profile.
- `stop.sh`: Graceful shutdown that exports n8n data and backs up pgAdmin.
- `n8n/backup/`: Host folder for n8n workflow/credential imports/exports.
- `pgadmin-backup/`: Host folder for pgAdmin data backup/restore.
- `selfSignedCertificate/sampleCert.pem`: Example CA bundle mounted into Ollama containers to trust custom CAs.
- `shared/`: A shared host folder mounted into the n8n container at `/data/shared`.
- `scripts/pgvector-init.sql`: SQL template that creates the app role/DB, enables `vector`, and seeds tables (idempotent).
- `scripts/pg-init.sh`: Wrapper that injects env vars into `psql` and executes the SQL template during first-time DB init.

## Services and Ports

Unless noted, ports are bound to `127.0.0.1` by `docker-compose.override.private.yml`.

- n8n
  - URL: http://localhost:5678
  - Container name: `n8n`
  - Volumes: `n8n_storage:/home/node/.n8n`, `./n8n/backup:/backup`, `./shared:/data/shared`
  - Notes: Imports existing workflows/credentials from `./n8n/backup` via `n8n-import` job.

- Open WebUI
  - URL: http://localhost:8080
  - Container name: `open-webui`
  - Volume: `open-webui:/app/backend/data`

- Ollama
  - Base URL (host): http://localhost:11434
  - Container name: `ollama` (varies per profile)
  - Volume: `ollama_storage:/root/.ollama`
  - Custom CA: mounts `./selfSignedCertificate/sampleCert.pem` and runs `update-ca-certificates` in entrypoint.

- Postgres
  - Host port: 5433 (mapped to container 5432)
  - Container name: `postgres`
  - Volume: `postgres_data:/var/lib/postgresql/data`
  - Healthcheck: `pg_isready`
  - Init: `scripts/pg-init.sh` runs once on first start to create the app role/DB and enable `pgvector`.

- pgAdmin
  - URL: http://localhost:5050
  - Container name: `pgadmin`
  - Volume: `pgadmin_data:/var/lib/pgadmin`
  - Defaults: `PGADMIN_DEFAULT_EMAIL=admin@admin.com`, `PGADMIN_DEFAULT_PASSWORD=admin`

- Helper jobs
  - `n8n-import`: runs once to import from `./n8n/backup` (credentials + workflows) into n8n.
  - `pgadmin-restore`: copies backup from `./pgadmin-backup` to `pgadmin_data` volume on start.
  - `ollama-pull-llama-*`: pulls models (e.g., `qwen2.5:7b-instruct-q4_K_M`, `nomic-embed-text`) after Ollama starts.

## Profiles

Enable only what you need to reduce image downloads:

- Ollama CPU: `--profile cpu` (adds: `ollama-cpu`, `ollama-pull-llama-cpu`)
- Ollama NVIDIA: `--profile gpu-nvidia` (adds: `ollama-gpu`, `ollama-pull-llama-gpu`)
- Ollama AMD (ROCm): `--profile gpu-amd` (adds: `ollama-gpu-amd`, `ollama-pull-llama-gpu-amd`)
- Open WebUI: `--profile ui` (adds: `open-webui`)
- pgAdmin UI: `--profile db-ui` (adds: `pgadmin`, `pgadmin-restore`)

By default (no profiles), only the core services start: `n8n`, `postgres`, and the one-time `n8n-import` job. This keeps downloads minimal.

Examples:

```bash
## Start minimal (n8n + Postgres only)
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml up -d

## Add Open WebUI
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile ui up -d

## Add pgAdmin UI
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile db-ui up -d

## CPU Ollama only (no UIs)
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile cpu up -d

## CPU Ollama + Open WebUI
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile cpu --profile ui up -d

## NVIDIA GPU Ollama + both UIs
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile gpu-nvidia --profile ui --profile db-ui up -d
```

GPU notes:
- NVIDIA: ensure NVIDIA Container Toolkit is installed (see Ollama docs).
- AMD: uses `ollama/ollama:rocm` and exposes `/dev/kfd`, `/dev/dri`.

## Environment Variables

Populate `.env` with required secrets before the first start. Important keys used by this stack:

- `N8N_ENCRYPTION_KEY`: Required. Generate with `openssl rand -hex 32`.
- `N8N_USER_MANAGEMENT_JWT_SECRET`: Required. Generate with `openssl rand -hex 32`.
- `POSTGRES_PASSWORD`: Required. Password for the internal Postgres.
- `N8N_HOSTNAME`: Optional. If set, n8n uses `https://$N8N_HOSTNAME` for webhook URLs.

App database variables used by the Postgres init scripts (optional; defaults apply if unset):

- `N8N_DB_USER` (default: `n8n_user`)
- `N8N_DB_PASSWORD` (default: `change_me`)
- `N8N_DB_NAME` (default: `n8n`)

Other keys may exist in `.env` (e.g., `POSTGRES_HOST`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PORT`) but the Compose file already sets the internal service configuration. Unused variables from `.env` are safe to ignore.

Security tip: Do not commit `.env`. Treat its contents as secrets.

## Start and Stop

Quick scripts:

- Start (CPU profile):
  ```bash
  ./start.sh
  ```

- Stop with backups:
  ```bash
  ./stop.sh
  ```
  What it does:
  - Exports all n8n workflows and credentials to `./n8n/backup/{workflows,credentials}`.
  - Stops the stack.
  - Backs up the `pgadmin_data` named volume to `./pgadmin-backup/`.

Equivalent Compose commands:

```bash
# Up
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile cpu up -d

# Down
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile cpu down
```

## Postgres Init and Seeding

First-time database preparation is handled inside the `postgres` container via a small wrapper script that passes environment variables into `psql`:

- Files mounted in the container:
  - `/docker-entrypoint-initdb.d/init.sql.template` ← from `scripts/pgvector-init.sql` (not auto-executed)
  - `/docker-entrypoint-initdb.d/010-init.sh` ← from `scripts/pg-init.sh` (auto-executed by the Postgres entrypoint)
- Why a template: Postgres auto-runs `*.sql` files without variable expansion. We avoid double-execution and safely pass variables by only executing the template through the wrapper.
- What it does (idempotent):
  - Creates role `N8N_DB_USER` with password `N8N_DB_PASSWORD` if missing.
  - Creates database `N8N_DB_NAME` owned by that role if missing.
  - Connects to that DB, grants privileges, enables `pgvector` (`CREATE EXTENSION IF NOT EXISTS vector`).
  - Creates example tables (`embeddings`, `document_metadata`, `document_rows`) with `IF NOT EXISTS`.

Configure via `.env` (example):

```
N8N_DB_USER=n8n_user
N8N_DB_PASSWORD=replace-with-a-strong-secret
N8N_DB_NAME=n8n
POSTGRES_PASSWORD=... # existing variable for the postgres superuser
```

Re-run the init against an existing DB (safe; idempotent):

```
docker compose exec postgres bash -lc '/docker-entrypoint-initdb.d/010-init.sh'
```

Reset and run from scratch (destroys DB data):

```
docker compose down -v
docker compose up -d postgres
docker compose logs -f postgres
```

## Data Persistence and Backups

Named volumes persist container data:

- `n8n_storage`: n8n app data.
- `ollama_storage`: Ollama model store (~/.ollama).
- `open-webui`: Open WebUI data.
- `postgres_data`: Postgres data directory.
- `pgadmin_data`: pgAdmin data directory.

Host folders:

- `./n8n/backup`: Import/export for n8n workflows and credentials.
- `./pgadmin-backup`: Backup snapshot of the `pgadmin_data` volume.

Restore flows:

- pgAdmin: On next start, `pgadmin-restore` copies `./pgadmin-backup` into the `pgadmin_data` volume.
- n8n: On start, `n8n-import` imports any files found under `./n8n/backup/{credentials,workflows}`.

## Accessing Services

- n8n editor: http://localhost:5678
- Open WebUI: http://localhost:8080
- pgAdmin: http://localhost:5050 (login defaults in Compose; change after first login)
- Postgres (host tools): `localhost:5433` (maps to container 5432)
- Ollama API: http://localhost:11434

Inside the Compose network, use service names instead of localhost:

- Postgres hostname: `postgres`
- Ollama hostname: `ollama` (varies by profile)

## Using Host Ollama (e.g., on macOS)

If you run Ollama on the host for better performance, use the `none` profile and point n8n to the host via `host.docker.internal`:

1. Start without Ollama in Docker:
   ```bash
   docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile none up -d
   ```
2. In `docker-compose.yml`, set (uncomment or add) in the `x-n8n` section:
   ```yaml
   - OLLAMA_HOST=host.docker.internal:11434
   ```
3. In n8n, set the Ollama credential base URL to `http://host.docker.internal:11434/`.

## Logs and Troubleshooting

- Inspect logs for a service:
  ```bash
  docker compose -p localai logs -f <service>
  ```
- Verify Postgres health: the `postgres` service includes a healthcheck; n8n waits for imports before starting.
- Model downloads: first run may take time while Ollama pulls models.
- Custom CA: replace `selfSignedCertificate/sampleCert.pem` with your organization CA bundle if needed.
- Ports: this setup binds services to localhost only for safety. Adjust `docker-compose.override.private.yml` if remote access is required.

Postgres init troubleshooting:

- Double-execution error (psql syntax near `:`): If logs show the wrapper ran and then `/docker-entrypoint-initdb.d/init.sql` runs too, ensure the SQL is mounted as `init.sql.template` and only `/docker-entrypoint-initdb.d/010-init.sh` executes. Update compose, then restart Postgres or `down -v` for a fresh init.
- Init didn’t run: The `postgres_data` volume already contains a cluster. Either reset with `docker compose down -v` or re-run the wrapper manually: `docker compose exec postgres bash -lc '/docker-entrypoint-initdb.d/010-init.sh'`.
- Password quoting errors: Do not put double quotes around passwords in SQL. The wrapper passes values via `psql -v` and the template uses safe quoting, so keep values in `.env` without extra quoting.

## Minimize Downloads

- Prefer minimal profiles: start only `n8n` + `postgres` (no profiles), and add `--profile ui` / `--profile db-ui` only when needed.
- Start specific services: `docker compose up -d n8n postgres` also limits pulls to just those images.
- Avoid building from source: building `n8n` or Open WebUI locally still pulls large base images and fetches dependencies (often more data than pulling the official images).
- Reuse images offline: on a machine with fast internet, pull once then transfer:
  - Save: `docker pull <image:tag>; docker save <image:tag> | gzip > image.tar.gz`
  - Load: `gunzip -c image.tar.gz | docker load`
  - Do this for: `n8nio/n8n:latest`, `ankane/pgvector`, and any optional UIs you plan to use.
- Match platform: if you’re on ARM/AMD64, ensure your Docker defaults match host arch to avoid emulation downloads.

## Upgrading

Update containers to the latest images while preserving data:

```bash
# Stop services (pick your active profile)
docker compose -p localai -f docker-compose.yml --profile <cpu|gpu-nvidia|gpu-amd|none> down

# Pull latest images
docker compose -p localai -f docker-compose.yml --profile <cpu|gpu-nvidia|gpu-amd|none> pull

# Start again with your override file if used
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile <cpu|gpu-nvidia|gpu-amd|none> up -d
```

## Security Notes

- Secrets live in `.env`.
- Default pgAdmin credentials are for local development only; change them after first login.
- Ports are restricted to `127.0.0.1` by default; expose carefully if needed.
