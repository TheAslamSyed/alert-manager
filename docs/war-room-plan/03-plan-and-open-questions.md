# War Room Agent — Plan Status and Open Questions

## Status
- Design and plan **approved** by the team.
- Prototype (6 screens) published: https://claude.ai/artifact/3WGSCVR7X4oWEesaJRRSxK
- No application code written yet. Each build phase is shown file by file and approved before coding.

## Confirmed decisions
- Backend: Java 21 + Spring Boot microservices; frontend: React + Vite + TypeScript + Material UI
- Messaging: Kafka (events) + RabbitMQ (tasks and commands)
- Observability: Prometheus, Loki, Zipkin, Grafana
- Security: Spring Security with Keycloak (default) / Auth0 (config)
- LLM: Ollama only (qwen2.5:7b + an embedding model)
- Agents: one `agent-runtime` codebase, 8 containers
- DemoCorp Bank: 3 apps + card-network stub, 4 roles, 13 incidents, ~40 seeded past incidents
- Deferred: Kubernetes, MinIO

## Build phases
0. Foundation: repo, Docker Compose, CI, schemas, auth, observability
1. Intake, correlation and DemoCorp simulator (FR-01, FR-02)
2. Agents and RCA with evidence (FR-03, FR-04)
3. Knowledge base / RAG (FR-05, FR-10)
4. Remediation, approval and communications (FR-06, FR-07, FR-08)
5. Dashboard and audit views (FR-09)
6. Evaluation report, documentation and hardening

## Still open
1. Deadline and team size (decides the build order)
2. Maven or Gradle (Maven proposed)
3. Evaluation store: Postgres instead of MLflow ("MLflow or equivalent" is allowed)
