ARG BASE_IMAGE=dpage/pgadmin4:latest
FROM ${BASE_IMAGE}

USER root
COPY ca/ /usr/local/share/ca-certificates/custom/
RUN update-ca-certificates || true
USER pgadmin

