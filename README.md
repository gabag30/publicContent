claude --resume 8b845e9e-96bf-432d-b163-b3f610b97675
# publicContent

A collection of sample projects, infrastructure templates, and personal scripts shared publicly.

## Contents

- **[das_api_java_sample_project](das_api_java_sample_project/README.md)** — Java sample demonstrating integration with the WIPO Digital Access Service (DAS) API: document registration, retrieval, and certificate download via OAuth2 client-assertion auth.

- **[sampleDockerN8n](sampleDockerN8n/README.md)** — Docker Compose stack for a local AI workspace (n8n, Ollama, Open WebUI, Postgres/pgvector, pgAdmin), for testing and learning only. Includes an [AWS CDK app](sampleDockerN8n/cdk/README.md) to deploy the same stack to ECS.

- **[prod](prod/README.md)** — Production-oriented variant of the Docker stack above: custom-built images, no exposed host ports, optional Traefik/Caddy ingress with TLS, and ops scripts for build/deploy/backup/restore.

- **[linux_stuff](linux_stuff/)** — Personal Linux workstation scripts (dev tool updates, git commit/push helper) and a devcontainer setup.

## License

See [LICENSE](LICENSE).
