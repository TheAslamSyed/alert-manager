# War Room Agent: architecture

## 1. Services

| Service | Port | Owns | Kafka in → out |
|---|---|---|---|
| api-gateway | 8080 | Routing, JWT check, CORS | – |
| ingestion-service | 8081 | Webhooks, adapters, dedupe, enrichment | → `signals.v1` |
| incident-service | 8082 | Correlation, lifecycle, timeline, SSE, comms drafts, RCA read model | `signals`, `investigation`, `remediation` → `incident.v1` |
| agent-service | 8083 | Supervisor + 6 agents | `incident` → `investigation.v1` |
| knowledge-service | 8084 | Hybrid search, seeded history, postmortems | `incident`, `investigation`, `remediation` |
| remediation-service | 8085 | Allowlist, approvals, signed commands, executor | `investigation`, `remediation` → `remediation.v1` |
| audit-service | 8086 | Hash-chained audit log | all topics |
| democorp-bank | 8090 | Demo bank, scenarios, fix endpoints, evaluation | (HTTP → ingestion) |
| frontend | 3001 | React dashboard (Nginx) | – |

Every service has its own Postgres schema and login (database per service), its own Flyway migrations,
and checks the Keycloak JWT itself even behind the gateway.

## 2. One incident end to end (FIN-01, bad release)

```
democorp-bank  deploy v2.3 ──▶ ingestion /ingest/deploy ─┐
               errors, pool full → Prometheus rules ─▶ Alertmanager ─▶ /ingest/alertmanager
               customer ticket ──▶ /ingest/ticket ────────┤
                                                          ▼
ingestion-service   adapt · validate · dedupe · enrich ─▶ signals.v1
incident-service    correlate (same service / dependency / time) + link recent deploy ─▶ INC-xxxx ─▶ incident.v1
agent-service       PHASE 1 (parallel): Monitoring (PromQL) · Log (LogQL) · Security
                    PHASE 2: RCA = cause catalog + knowledge search + LLM; confidence formula; uncited claims dropped
                    PHASE 3: Remediation planner (allowlist only) · Communication drafts ─▶ investigation.v1
incident-service    INVESTIGATING → IDENTIFIED, stores RCA + drafts, pushes SSE to the dashboard
remediation-service PROPOSED → (asha) PENDING_APPROVAL → (priya) APPROVED + HMAC signature ─▶ remediation.v1
executor            verify signature → dry run → execute (democorp /admin) → verify with Prometheus ─▶ remediation.v1
incident-service    MITIGATING → MITIGATED → (human) RESOLVED
knowledge-service   postmortem draft → human publishes → embedded → found by the next similar incident
audit-service       every event on every topic → SHA-256 hash chain; /verify recomputes it
```

## 3. RCA confidence

```
confidence = 0.35 · evidence support      (how many of the cause's evidence types were seen)
           + 0.15 · source diversity      (metrics + logs + changes > one source)
           + 0.15 · temporal precedence   (change happened before the symptoms)
           + 0.15 · historical match      (similar past incident fixed the same way)
           + 0.20 · model assessment      (LLM's own estimate; 0.5 when running on rules only)
```

The LLM may only choose causes from the cause catalog and must cite evidence ids that exist; anything else is
rejected and reported as a gap. Without the LLM the same formula ranks causes from rules.

## 4. Design patterns

| Pattern | Where |
|---|---|
| Microservices, API Gateway, Database per service | whole system |
| Event-driven pub/sub, idempotent consumers, dead-letter topics | Kafka + `common` |
| Adapter | ingestion (Alertmanager, CI, config store, ticketing) |
| Chain of Responsibility | ingestion pipeline (validate → dedupe → enrich) |
| Strategy | correlation rules |
| State | incident lifecycle, remediation approval lifecycle |
| Supervisor + Blackboard | agent-service (agents never call each other) |
| Command | remediation executor (preconditions, dry run, execute, verify, undo) |
| Template Method | communication drafts per audience |
| Observer | SSE live updates |
| Repository | Spring Data JPA |
| Circuit breaker + timeout | LLM calls (Resilience4j) with rule-based fallback |

## 5. Security and safety

- Keycloak realm `democorp`: roles VIEWER, RESPONDER, APPROVER, ADMIN; PKCE login for the dashboard.
- JWT checked at the gateway and again in every service; method-level `@PreAuthorize` on actions.
- Webhooks use an API key; DemoCorp's fix endpoints use a separate admin key only the executor holds.
- `/internal/**` endpoints are not routed by the gateway.
- The LLM has no write tools; fixes come only from the allowlist; nothing runs without approval;
  no self-approval; admins cannot approve; HIGH risk needs two approvers; commands are HMAC-signed.
- Logs and tickets are treated as untrusted text in prompts.

## 6. Fault tolerance

- Kafka: idempotent producer (acks=all), 2 retries then `<topic>.DLT`; consumers record processed message ids.
- Investigation state is stored in Postgres and resumed after a restart.
- An agent that times out or fails is a recorded gap; the investigation continues.
- LLM down or slow → rules-based RCA and template messages; embedding model down → keyword search.
- Correlation uses a Postgres advisory lock, so several instances never open duplicate incidents.
- The executor verifies every fix with Prometheus and sends the incident back to investigation if it did not work.

## 7. Observability

Micrometer metrics → Prometheus (business metrics, agent durations, LLM call outcomes), logs → Loki
(logfmt, with trace ids), traces → Zipkin across HTTP and Kafka, Grafana with all three data sources.
