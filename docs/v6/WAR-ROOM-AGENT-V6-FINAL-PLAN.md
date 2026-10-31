# Autonomous Incident War Room Agent — v6 Final Plan

**Status:** v6 (MEDIUM) is the final version. Purpose: placement project. Medium complexity, recognizable tech stack, microservices, **Kafka only**, buildable in a ~2-hour time box (extras are stretch goals).
**Prototype UI (6 screens):** https://claude.ai/artifact/3WGSCVR7X4oWEesaJRRSxK (private until shared from its Share menu).

---

## 1. Problem in one paragraph
When a production incident happens, engineers lose time manually connecting alerts, logs, deployments and tickets. The War Room Agent is an AI incident command center. It takes in signals, groups them into incidents, and sends **specialist AI agents** to investigate. It then finds the **root cause with a confidence score and cited evidence**, proposes fixes that **run only after a human approves them**, drafts stakeholder updates, records everything in a tamper-proof audit log, and turns resolved incidents into knowledge-base articles.

---

## 2. Tech stack

| Layer | Tool |
|---|---|
| Frontend | React + Vite + TypeScript, Material UI, TanStack Query, Zustand, React Router, oidc-client-ts, SSE via @microsoft/fetch-event-source, served by Nginx |
| Backend | Java 21, Spring Boot 3.x microservices, Maven (proposed) |
| API gateway | Spring Cloud Gateway |
| Messaging | **Apache Kafka (KRaft mode)** with Spring Kafka. No RabbitMQ |
| AI | Spring AI → **Ollama** (`qwen2.5:7b`, plus an embedding model such as `nomic-embed-text`) |
| RAG / vector search | PostgreSQL + **pgvector** (Spring AI PgVectorStore) + Postgres full-text search |
| Database | PostgreSQL, one schema per service, Flyway migrations, Spring Data JPA |
| Security | Spring Security OAuth2 Resource Server + **Keycloak** (Auth0 possible later via config) |
| Resilience | Resilience4j (circuit breaker, retry, timeout) |
| Observability | Micrometer → **Prometheus**, **Loki** (logs), **Zipkin** (traces), **Grafana** dashboards, Alertmanager |
| API docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, Vitest + React Testing Library (Testcontainers is a stretch goal) |
| Packaging | Docker + Docker Compose |

**Not in v6:** RabbitMQ, Redis, Kubernetes, MinIO, transactional outbox (deferred), MLflow (Postgres eval store instead).

---

## 3. Services (7 microservices + demo app + frontend)

| # | Service | Job | FR |
|---|---|---|---|
| 1 | **api-gateway** | Single entry point, JWT check, routing `/api/**` | Security |
| 2 | **ingestion-service** | Receives alerts, tickets, deploy events and log alerts, converts them to a standard event, publishes to `signals.v1` | FR-01 |
| 3 | **incident-service** | Correlation (time window + dependencies + recent deploy), incident lifecycle, timeline, communication drafts and approval, live UI updates (SSE) | FR-02, FR-07, FR-09 (timeline) |
| 4 | **agent-service** | Supervisor plus 6 AI agents, evidence board, RCA with confidence | FR-03, FR-04, FR-06 (plan), FR-07 (drafts) |
| 5 | **knowledge-service** | Past incidents + runbooks, hybrid search (pgvector + keywords), postmortem drafts → review → index | FR-05, FR-10 |
| 6 | **remediation-service** | Action allowlist, approval rules (no self-approval, high risk = 2 approvers), executor that runs, verifies and undoes the fix | FR-06, FR-08 |
| 7 | **audit-service** | Reads all Kafka topics, writes a hash-chained append-only log, `/verify` endpoint | FR-09 |
| + | **democorp-bank** | One demo Spring Boot app (banking, payments, accounts parts) with break switches, fix endpoints, and a scenario/evaluation runner | Demo + evaluation |
| + | **frontend** | React dashboard (the 6 prototype screens) | All |

### The 6 agents (inside agent-service)

| Agent | Looks at (read-only) | Produces |
|---|---|---|
| Monitoring | Prometheus (PromQL) | Error/latency spikes, affected services |
| Log | Loki (LogQL) | Error patterns, first-failure time |
| Security | Auth logs, login-failure metrics | Attack indicators or "ruled out" |
| RCA | Evidence board + knowledge-search tool | Ranked root causes, confidence, cited evidence |
| Remediation planner | Action allowlist + runbooks | Corrective + preventive actions (proposals only) |
| Communication | Incident status + RCA | Drafts for executives, engineers, customers |

Change detection (recent deploys) is done by incident-service during correlation, and the RCA agent uses knowledge search as a tool, so 6 agents cover all needs.

---

## 4. Kafka topics

| Topic | Producer → Consumers |
|---|---|
| `signals.v1` | ingestion → incident, audit |
| `incident.v1` | incident → agent, knowledge, audit, (UI via SSE) |
| `investigation.v1` | agent → incident, remediation, audit |
| `remediation.v1` | remediation → incident, agent, audit |
| `*.DLT` | dead-letter topic for each topic above |

Key = `incidentId` (keeps one incident's events in order). Every message uses one envelope: `messageId, type, schemaVersion, occurredAt, producer, incidentId, traceId, payload`.

---

## 5. Architecture diagrams

### Diagram 1: System overview

```
┌──────────────────────────────────────────────────────────────────────┐
│ USERS        React + Vite + TS + Material UI (Nginx)                 │
│              Asha Responder · Priya Approver · Sarah Viewer · Omar   │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │ REST + SSE · JWT
┌────────────────────────────────▼─────────────────────────────────────┐
│ EDGE         api-gateway (Spring Cloud Gateway) · Keycloak           │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │ /api/**
┌────────────────────────────────▼─────────────────────────────────────┐
│ SERVICES     ingestion · incident · agent · knowledge                │
│ (Spring Boot) remediation · audit                                     │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │ events ▼▲
┌────────────────────────────────▼─────────────────────────────────────┐
│ MESSAGING    Kafka (KRaft): signals · incident · investigation ·     │
│              remediation  (+ dead-letter topics)                     │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │ prompts ▼ answers ▲ (Spring AI)
┌────────────────────────────────▼─────────────────────────────────────┐
│ AI           Ollama: qwen2.5:7b + embedding model                    │
└──────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────┐
│ DATA         PostgreSQL + pgvector · schema per service · Flyway     │
├──────────────────────────────────────────────────────────────────────┤
│ OBSERVE      Prometheus · Loki · Zipkin · Grafana · Alertmanager     │
└────────────────────────────────▲─────────────────────────────────────┘
                                 │ logs, metrics, traces ▲ · approved fixes ▼
┌────────────────────────────────┴─────────────────────────────────────┐
│ DEMOCORP     democorp-bank (banking · payments · accounts)           │
│ BANK         break switches · fix endpoints · scenario runner        │
└──────────────────────────────────────────────────────────────────────┘
```

### Diagram 2: One incident, end to end

```
  Alertmanager / Loki rules / deploy events / tickets
                 │ webhooks
  ┌──────────────▼───────────────┐
  │ ingestion-service      FR-01 │  normalize · dedupe
  └──────────────┬───────────────┘
                 │ Kafka signals.v1
  ┌──────────────▼───────────────┐
  │ incident-service FR-02, FR-09│  correlate → INC-xxxx · timeline · SSE → UI
  └──────────────┬───────────────┘
                 │ Kafka incident.v1 (opened)
  ┌──────────────▼───────────────┐
  │ agent-service  FR-03/04/06/07│  supervisor → 6 agents → RCA + fix plan + drafts
  └──────────────┬───────────────┘
                 │ Kafka investigation.v1 (rca + fix proposed)
  ┌──────────────▼───────────────┐
  │ remediation-service    FR-08 │  ◀── Priya approves (no self-approval)
  │ approve → execute → verify   │  undo if verification fails
  └──────────────┬───────────────┘
                 │ HTTP admin endpoint (e.g. rollback v2.3 → v2.2)
  ┌──────────────▼───────────────┐
  │ democorp-bank                │  Prometheus confirms recovery
  └──────────────┬───────────────┘
                 │ Kafka remediation.v1 → incident MITIGATED → RESOLVED
  ┌──────────────▼───────────────┐
  │ knowledge-service      FR-10 │  postmortem → review → index (used by FR-05)
  └──────────────────────────────┘

  audit-service reads every topic → hash-chained log (FR-09)
  incident-service: comms drafts → human approves → sent (FR-07)
```

### Diagram 3: Multi-agent investigation (inside agent-service)

```
                    Supervisor (evidence board)
                               │ PHASE 1 · in parallel
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
   ┌────────────┐       ┌────────────┐       ┌────────────┐
   │ Monitoring │       │    Log     │       │  Security  │
   │   PromQL   │       │   LogQL    │       │ auth logs  │
   └─────┬──────┘       └─────┬──────┘       └─────┬──────┘
         └────────────────────┼────────────────────┘
                              │ findings with evidence IDs
                ┌─────────────▼─────────────┐
                │ PHASE 2 · RCA agent       │ + knowledge-search tool
                │ ranked causes + confidence│ uncited claims rejected
                └─────────────┬─────────────┘
                ┌─────────────▼─────────────┐
                │ PHASE 3 · Remediation     │ allowlist only → approval
                └─────────────┬─────────────┘
                ┌─────────────▼─────────────┐
                │ Communication agent       │ exec · eng · customer drafts
                └───────────────────────────┘
  Each agent: Spring AI → Ollama · read-only tools · structured output ·
  step/token/time limits · Resilience4j · audit event per LLM/tool call.
  A timed-out agent is marked as a gap; the investigation continues.
```

---

## 6. Workflow explained (example: FIN-01 bad release)
1. **10:00** payment part of democorp-bank is deployed as v2.3. It leaks DB connections.
2. **10:02** errors spike. Alertmanager fires a webhook, and a deploy event and a customer ticket also arrive.
3. **ingestion-service** normalizes and dedupes them and publishes to `signals.v1`.
4. **incident-service** groups them (same time window, related services, deploy 2 min before) into **INC-2041**, publishes `incident.v1`, and the dashboard updates live.
5. **agent-service:** Monitoring, Log and Security agents run in parallel. The RCA agent ranks causes: *"v2.3 connection leak, 87%"*, citing evidence and a similar past incident found through knowledge search. The Remediation planner proposes *"roll back to v2.2"* plus preventive actions. The Communication agent drafts updates.
6. **remediation-service:** Asha (Responder) cannot approve her own proposal. Priya (Approver) approves. The executor rolls back, checks the error rate in Prometheus, and undoes the change if it doesn't recover.
7. **incident-service** marks the incident Mitigated and then Resolved. Comms drafts are approved and sent.
8. **knowledge-service** drafts a postmortem. Once a human reviews it, it's indexed for next time.
9. **audit-service** has recorded every step in a hash chain. "Verify chain" proves nothing was edited.

---

## 7. Design patterns used
- Microservices + event-driven (Kafka)
- **Adapter** (source payloads → standard event)
- **Strategy** (correlation rules)
- **State** (incident and approval lifecycles)
- **Supervisor** + **Blackboard** (agents)
- **Command** (remediation actions: dry-run, execute, verify, undo)
- **Repository**
- **Template Method** (communication drafts)
- **Observer** (SSE live updates)
- Idempotent consumers, dead-letter topics
- Hash-chained audit log

---

## 8. Security and safety
- Roles: `VIEWER`, `RESPONDER`, `APPROVER`, `ADMIN` (Keycloak).
- The gateway checks the JWT, and every service checks it again.
- The LLM has **no write tools**, only allowlisted actions exist, and the executor runs approved actions only.
- No self-approval, admins can't approve, and high-risk actions need 2 approvers.
- Logs and tickets are treated as untrusted text (prompt-injection defense).

## 9. Fault tolerance
- Resilience4j on Ollama and tool calls. If the LLM is down, correlation and knowledge search still work.
- Kafka retries + dead-letter topics, plus idempotent consumers keyed by `messageId`.
- Remediation verifies the result and undoes the change automatically.
- If one agent fails, the investigation continues and marks the gap.

---

## 10. FR coverage

| FR | Covered by |
|---|---|
| FR-01 Intake | ingestion-service |
| FR-02 Correlation | incident-service |
| FR-03 RCA + confidence | agent-service (RCA agent) |
| FR-04 Multi-agent | agent-service (supervisor + 6 agents) |
| FR-05 RAG | knowledge-service + RCA knowledge tool |
| FR-06 Remediation | Remediation planner + remediation-service |
| FR-07 Communication | Communication agent + incident-service drafts |
| FR-08 Human approval | remediation-service |
| FR-09 Audit | audit-service + incident timeline |
| FR-10 Learning | knowledge-service postmortems |

**Deliverables:**
- Command Center, multi-agent workflow, knowledge repository, RCA dashboard and communication generator: the services above + the frontend
- MTTR evaluation report: the democorp-bank scenario/eval runner (stretch)
- Technical documentation: `docs/`

---

## 11. DemoCorp Bank (light support)
- **One app** with 3 logical parts (banking, payments, accounts) plus a card-network stub.
- **4 personas:**
  - Asha (Responder)
  - Priya (Approver)
  - Sarah (Viewer)
  - Omar (Admin)
- **10 incidents.**

> ⚠️ The session that produced v6 didn't record *which* 10 of the 13 incidents are kept. Proposed: keep FIN-01, 02, 04, 05, 06, 07, 08, 11, 12, 13 and drop the three that could only be simulated (FIN-03 certificate, FIN-09 disk, FIN-10 clock). **Please confirm.**

| ID | Incident | Root cause | Fix |
|---|---|---|---|
| FIN-01 | Bad release | v2.3 leaks DB connections | Roll back |
| FIN-02 | Card network outage | External network down | Switch to backup route |
| FIN-04 | Payday spike | Processor rate limit (429) | Throttle and queue |
| FIN-05 | Duplicate debits | Retry change dropped idempotency key | Revert config, refund |
| FIN-06 | Fraud rule mistake | Threshold too strict | Revert rule |
| FIN-07 | Stuck ledger | Poison message blocks consumer | Skip to DLT, restart |
| FIN-08 | Cache/DB overload | Account cache failure overloads DB | Restart cache, throttle |
| FIN-11 | Login attack | Credential stuffing | Step-up auth, block IPs |
| FIN-12 | Partner token misuse | Compromised API token | Revoke token |
| FIN-13 | False alarm | Over-sensitive alert | Close, tune alert |

---

## 12. Repository layout
```
war-room-agent/
  api-gateway/  ingestion-service/  incident-service/  agent-service/
  knowledge-service/  remediation-service/  audit-service/
  democorp-bank/  frontend/
  infra/   docker-compose.yml, prometheus, alertmanager, loki, grafana, keycloak realm
  docs/
```

## 13. Time-boxed build plan (~2 hours)
1. **Infra:** Docker Compose (Kafka, Postgres+pgvector, Keycloak, Prometheus, Loki, Zipkin, Grafana)
2. **ingestion-service + incident-service** (intake, correlation, SSE)
3. **agent-service** (supervisor + 6 agents on Ollama)
4. **remediation-service + audit-service**
5. **knowledge-service** (seeded past incidents)
6. **democorp-bank** + frontend screens

**Stretch:** evaluation report, extra scenarios, CI (GitHub Actions), Testcontainers, outbox, Auth0.

## 14. Still to confirm
1. Which 10 incidents (proposal in section 11)
2. Maven (proposed) or Gradle
3. Team size / exact deadline
