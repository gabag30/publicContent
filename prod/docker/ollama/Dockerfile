ARG BASE_IMAGE=ollama/ollama:latest
FROM ${BASE_IMAGE}

USER root
COPY ca/ /usr/local/share/ca-certificates/custom/
RUN update-ca-certificates || true
USER 0

