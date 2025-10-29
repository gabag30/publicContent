ARG BASE_IMAGE=ghcr.io/open-webui/open-webui:main
FROM ${BASE_IMAGE}

USER root
COPY ca/ /usr/local/share/ca-certificates/custom/
RUN update-ca-certificates || true

