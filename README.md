# War Room Agent

An AI incident command center for a fictional bank, **DemoCorp Bank**. It takes in alerts, deploys and
customer tickets, groups them into incidents, and sends **specialist agents** to investigate. They find the
**root cause with a confidence score and cited evidence**, propose a fix that **runs only after a human
approves it**, draft stakeholder updates, keep a **tamper-evident audit log**, and turn resolved incidents
into knowledge-base articles.

Built as Spring Boot microservices on Kafka, with a React dashboard, Keycloak login, Ollama (local LLM),
PostgreSQL + pgvector, and Prometheus / Loki / Zipkin / Grafana.

## Requirements covered

| FR | Requirement | Where |
|---|---|---|
| FR-01 | Ingest alerts, tickets, logs, monitoring events, deployments | `ingestion-service` |
| FR-02 | Correlate events across sources | `incident-service` (correlation rules + change detection) |
| FR-03 | Root cause with confidence and explanation | `agent-service` RCA agent |
| FR-04 | Specialised agents (monitoring, analysis, security, remediation, communication) | `agent-service` supervisor + 6 agents |
| FR-05 | Similar past incidents via RAG / semantic search | `knowledge-service` (pgvector + full-text) |
| FR-06 | Corrective and preventive actions | Remediation planner + `remediation-service` |
| FR-07 | Incident summaries and status updates | Communication agent + `incident-service` drafts |
| FR-08 | Engineer approval before any fix | `remediation-service` approval rules + signed commands |
| FR-09 | Log of evidence, findings and decisions | `audit-service` hash chain + incident timeline |
| FR-10 | Knowledge articles from resolved incidents | `knowledge-service` postmortems |

## Architecture

```
React dashboard (Vite · TS · MUI) ── Nginx ──▶ api-gateway (Spring Cloud Gateway, JWT)
                                                   │ REST + SSE
  ┌──────────────┬──────────────┬──────────────┬───┴──────────┬──────────────┬──────────────┐
ingestion     incident        agent         knowledge     remediation      audit
FR-01         FR-02/07/09     FR-03/04/06/07 FR-05/10      FR-06/08         FR-09
  └──────────────┴──────────────┴──────── Kafka (signals · incident · investigation · remediation + DLT) ┘

Shared: PostgreSQL + pgvector (schema per service) · Keycloak · Ollama (qwen2.5:7b, nomic-embed-text)
Observe: Prometheus · Alertmanager · Loki · Zipkin · Grafana
Demo:    democorp-bank (banking-api · payment-service · account-service, break/fix switches, evaluation runner)
```

More detail, including the end-to-end flow and design patterns: [docs/architecture.md](docs/architecture.md).

## Run it

**You need:** Docker Desktop (about 8 GB of RAM for Docker), and [Ollama](https://ollama.com) on the host with
two models:

```bash
ollama pull qwen2.5:7b
```

```bash
ollama pull nomic-embed-text
```

Start everything (Java and the frontend are compiled inside Docker; no local JDK or Node needed):

```bash
docker compose -f infra/docker-compose.yml --profile apps up -d --build
```

| What | URL |
|---|---|
| Dashboard | http://localhost:3001 |
| API gateway | http://localhost:8080 |
| Keycloak (admin console) | http://localhost:8180 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Zipkin | http://localhost:9411 |
| Swagger UI per service | http://localhost:808x/swagger-ui.html |

Demo users (local only) are in [infra/keycloak/realm-democorp.json](infra/keycloak/realm-democorp.json):

| User | Role | Can |
|---|---|---|
| asha | Responder | Investigate, send fixes for approval, draft messages |
| priya | Approver | Approve fixes and send messages |
| meera | Approver | Second approver for high-risk fixes |
| sarah | Viewer | Read only |
| omar | Admin | Settings; **cannot** approve fixes |

## Demo in 5 minutes

1. Sign in as **asha** → Command Center → pick **FIN-01 · Bad release** → **Run scenario**.
2. Within about a minute an incident opens with the release and a customer ticket linked to it.
3. The agents investigate. The **Root cause** tab shows ranked causes, confidence factors and evidence.
4. **Fixes** tab → **Send for approval**. Asha cannot approve her own request.
5. Sign in as **priya** → **Approve and run**. The executor rolls back and checks Prometheus that errors dropped.
6. **Communications**: priya approves and sends the drafted updates.
7. Mark the incident **resolved** → a postmortem draft appears in **Knowledge base** → publish it.
8. **Audit** tab → **Verify chain**.
9. **Evaluation** → **Run evaluation** for the MTTR report over all practice incidents.

## Practice incidents (DemoCorp Bank)

FIN-01 bad release · FIN-02 card network outage · FIN-03 expired certificate · FIN-04 processor rate limit ·
FIN-05 duplicate debits · FIN-06 fraud rule change · FIN-07 stuck ledger · FIN-08 cache failure ·
FIN-11 credential stuffing · FIN-12 partner token abuse · FIN-13 false alarm.

## Project layout

```
common/               shared events, Kafka (retry + DLT), security, tracing, logging, service catalog
ingestion-service/    webhooks → adapters → validate / dedupe / enrich → signals.v1
incident-service/     correlation, lifecycle, timeline, SSE, comms drafts, RCA read model
agent-service/        supervisor + Monitoring, Log, Security, RCA, Remediation planner, Communication agents
knowledge-service/    hybrid search, seeded history, postmortems
remediation-service/  allowlist, approvals, HMAC-signed commands, executor
audit-service/        hash-chained append-only log
api-gateway/          routing + JWT
democorp-bank/        the demo bank + evaluation runner
frontend/             React dashboard
infra/                docker-compose, Keycloak realm, Prometheus rules, Alertmanager, Grafana
Dockerfile.service    one multi-stage Dockerfile for every Java service
```

## Tests

```bash
./mvnw test
```

Unit tests cover correlation rules, the incident lifecycle, approval rules and RCA scoring.

## Known limits

- Agent MTTR in the evaluation report adds an assumed approval and fix time to the measured diagnosis time.
- Kafka publishing is at-least-once without a transactional outbox; consumers are idempotent.
- On a CPU-only machine one LLM answer can take a minute or more; agents fall back to rules if it times out.
