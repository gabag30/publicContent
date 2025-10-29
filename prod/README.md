# Production-Style Deployment

This folder contains a production-oriented layout using custom-built images, a separate compose file, optional ingress overlays (Traefik or Caddy), and ops scripts for deploy, backup, update, and DB restore.

Contents
- compose.yml: Base production compose (no host ports; CPU/GPU profiles).
- compose.traefik.yml: Optional Traefik ingress overlay with TLS via ACME.
- compose.caddy.yml: Optional Caddy ingress overlay with automatic TLS.
- docker/*: Minimal Dockerfiles per service with optional CA injection.
- env/*.env.example: Environment templates (copy and fill with secrets).
- bin/*.sh: Build, deploy, backup (app and DB), update, stop, restore DB.
- ingress/: Examples for Caddyfile and Traefik storage.

Quick Start
1) Prepare env files
   cp env/n8n.env.example env/n8n.env
   cp env/pgadmin.env.example env/pgadmin.env
   # Optionally also create env/postgres.env to set POSTGRES_*
   - Required: set N8N_ENCRYPTION_KEY, N8N_USER_MANAGEMENT_JWT_SECRET, POSTGRES_PASSWORD.
   - Optional: set WEBHOOK_URL in env/n8n.env when using TLS and a public hostname.

2) Build images (optional)
   REGISTRY=ghcr.io/yourorg TAG=prod-2025-01 bin/build.sh
   REGISTRY=ghcr.io/yourorg TAG=prod-2025-01 bin/push.sh

3) Deploy
   # CPU profile (no GPU)
   PROJECT=localai PROFILE=cpu bin/deploy.sh
   # NVIDIA GPU
   PROJECT=localai PROFILE=gpu-nvidia bin/deploy.sh
   # AMD GPU (ROCm)
   PROJECT=localai PROFILE=gpu-amd bin/deploy.sh

Ingress and TLS
- This compose does not publish ports. Place it behind your reverse proxy (Traefik or Caddy overlays are provided) and route to service ports inside the compose network:
  - n8n: 5678, Open WebUI: 8080, Ollama: 11434, pgAdmin: 80 (restrict access).
- For Traefik overlay:
  - Set environment variables with your domains: N8N_HOST, OPENWEBUI_HOST, OLLAMA_HOSTNAME, PGADMIN_HOST and ACME email: TRAEFIK_ACME_EMAIL.
  - Run with overlay:
    docker compose --project-directory . -p localai -f compose.yml -f compose.traefik.yml up -d
- For Caddy overlay:
  - Copy ingress/Caddyfile.example to ingress/Caddyfile and edit domains.
  - Run with overlay:
    docker compose --project-directory . -p localai -f compose.yml -f compose.caddy.yml up -d
- Set n8n WEBHOOK_URL in env/n8n.env to your HTTPS URL (e.g., WEBHOOK_URL=https://n8n.example.com).

Backups and Ops
- Export n8n flows/credentials and pgAdmin data:
  PROJECT=localai PROFILE=cpu bin/backup.sh
- Postgres DB backup (pg_dump to gzip):
  PROJECT=localai PROFILE=cpu bin/backup-db.sh
  - Outputs to backups/postgres/<db>-<timestamp>.sql.gz
- Restore DB from dump:
  PROJECT=localai PROFILE=cpu bin/restore-db.sh backups/postgres/<file>.sql.gz

Service Discovery (compose network)
- Postgres: hostname postgres (5432)
- Ollama: hostname ollama (or ollama-nvidia / ollama-amd by profile)
- Open WebUI: hostname open-webui (8080)
- n8n: hostname n8n (5678)

Stop / Update
- Stop:
  PROJECT=localai PROFILE=cpu bin/stop.sh
- Update (recreate with latest bases):
  PROJECT=localai PROFILE=cpu bin/update.sh

Custom CA
- Place your organization PEMs under docker/<service>/ca/ before building to bake them into images.

Security
- Keep service ports internal only; expose via org ingress with TLS and proper auth.

