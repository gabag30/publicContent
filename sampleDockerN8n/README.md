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

- pgAdmin
  - URL: http://localhost:5050
  - Container name: `pgadmin`
  - Volume: `pgadmin_data:/var/lib/pgadmin`
  - Defaults: `PGADMIN_DEFAULT_EMAIL=admin@admin.com`, `PGADMIN_DEFAULT_PASSWORD=admin`

- Helper jobs
  - `n8n-import`: runs once to import from `./n8n/backup` (credentials + workflows) into n8n.
  - `pgadmin-restore`: copies backup from `./pgadmin-backup` to `pgadmin_data` volume on start.
  - `ollama-pull-llama-*`: pulls models (e.g., `qwen2.5:7b-instruct-q4_K_M`, `nomic-embed-text`) after Ollama starts.

## Profiles (CPU / GPU)

Choose one profile when starting the stack:

- CPU only: `--profile cpu` (services: `ollama-cpu`, `ollama-pull-llama-cpu`)
- NVIDIA GPU: `--profile gpu-nvidia` (services: `ollama-gpu`, `ollama-pull-llama-gpu`)
- AMD GPU (ROCm): `--profile gpu-amd` (services: `ollama-gpu-amd`, `ollama-pull-llama-gpu-amd`)
- No Ollama (use external/local): `--profile none` (omit all Ollama services)

Example:

```bash
# CPU
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile cpu up -d

# NVIDIA GPU
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile gpu-nvidia up -d

# AMD GPU
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile gpu-amd up -d

# No Ollama in Docker (use host Ollama)
docker compose -p localai -f docker-compose.yml -f docker-compose.override.private.yml --profile none up -d
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


