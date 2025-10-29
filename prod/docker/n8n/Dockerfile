ARG BASE_IMAGE=n8nio/n8n:latest
FROM ${BASE_IMAGE}

# Optionally bake in custom CA certs by placing PEM files in docker/n8n/ca
USER root
COPY ca/ /usr/local/share/ca-certificates/custom/
RUN update-ca-certificates || true
USER node

ENV N8N_DIAGNOSTICS_ENABLED=false \
    N8N_PERSONALIZATION_ENABLED=false \
    N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS=true

