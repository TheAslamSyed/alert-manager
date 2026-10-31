# War Room Agent — Architecture Diagrams

- **Diagram 1**: what the system is made of, layer by layer, with tools.
- **Diagram 2**: how one incident moves through the services, with the Kafka topic / RabbitMQ queue at each step and the FR covered.
- **Diagram 3**: how the agents work together inside the investigation step.

## Diagram 1: System overview (all layers and tools)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ USERS         React + Vite + TypeScript + Material UI · Nginx            │
│               Asha Responder · Priya Approver · Sarah Viewer             │
│               Omar Admin                                                 │
└─────────────────────────────────────┬────────────────────────────────────┘
                                      │  REST + SSE · JWT on every call
┌─────────────────────────────────────▼────────────────────────────────────┐
│ EDGE          API Gateway (Spring Cloud Gateway) · Redis rate limit      │
│               Keycloak (default) / Auth0 (config) · Spring Security      │
└─────────────────────────────────────┬────────────────────────────────────┘
                                      │  routes /api/** to the core services
┌─────────────────────────────────────▼────────────────────────────────────┐
│ CORE          ingestion · correlation · incident · orchestrator          │
│ SERVICES      knowledge · remediation · remediation-executor             │
│ (Spring Boot) communication · audit · evaluation-runner                  │
│               democorp-simulator (demo support)                          │
└─────────────────────────────────────┬────────────────────────────────────┘
                                      │  events ▼▲ Kafka · tasks & commands ▼▲ RabbitMQ
┌─────────────────────────────────────▼────────────────────────────────────┐
│ MESSAGING     Kafka (KRaft): events · replayable, ordered by incident    │
│               RabbitMQ: tasks & commands · ack, retry, dead-letter       │
└─────────────────────────────────────┬────────────────────────────────────┘
                                      │  agent.tasks ▼ · agent.results ▲
┌─────────────────────────────────────▼────────────────────────────────────┐
│ AGENTS        agent-runtime: 1 codebase → 8 containers (AGENT_ROLE)      │
│ (multi-agent) Monitoring · Log · Change · Security · Knowledge           │
│               RCA · Remediation planner · Communication                  │
└─────────────────────────────────────┬────────────────────────────────────┘
                                      │  Spring AI · prompts ▼ · structured answers ▲
┌─────────────────────────────────────▼────────────────────────────────────┐
│ AI            Ollama on the host: qwen2.5:7b + an embedding model        │
└──────────────────────────────────────────────────────────────────────────┘

  ── shared by every service ──────────────────────────────────────────────

┌──────────────────────────────────────────────────────────────────────────┐
│ DATA          PostgreSQL + pgvector · one schema per service · Flyway    │
│               Redis · dedup keys, rate limits, cache                     │
└──────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────┐
│ OBSERVABILITY Micrometer → Prometheus · Loki (logs) · Zipkin (traces)    │
│               Grafana dashboards · Alertmanager → ingestion webhook      │
└─────────────────────────────────────▲────────────────────────────────────┘
                                      │  logs, metrics, traces ▲   ·   approved fixes ▼ (executor)
┌─────────────────────────────────────┴────────────────────────────────────┐
│ DEMOCORP BANK banking-api · payment-service · account-service            │
│ (watched)     card-network stub · break switches · fix endpoints         │
└──────────────────────────────────────────────────────────────────────────┘
```

## Diagram 2: One incident, end to end (with FRs)

```
    ┌────────────────────────────────┐
    │ SIGNALS IN                     │
    │ metric alerts · log alerts     │  from Alertmanager (Prometheus), Loki rules
    │ deploy events · tickets        │  and democorp-simulator
    └────────────────┬───────────────┘
                     │  webhooks (HTTP)
    ┌────────────────▼───────────────┐
    │ ingestion-service              │  FR-01
    │ adapt · validate · dedupe      │  Redis dedup keys · raw events kept for replay
    └────────────────┬───────────────┘
                     │  Kafka · telemetry.normalized
    ┌────────────────▼───────────────┐
    │ correlation-service            │  FR-02
    │ group signals → 1 incident     │  time window + dependencies + recent deploys
    └────────────────┬───────────────┘
                     │  Kafka · incident.events
    ┌────────────────▼───────────────┐
    │ incident-service               │  FR-09 timeline
    │ lifecycle · timeline · SSE     │  live updates ──SSE──▶ React dashboard
    └────────────────┬───────────────┘
                     │  Kafka · incident.opened
    ┌────────────────▼───────────────┐
    │ investigation-orchestrator     │  FR-04
    │ supervisor · saga · evidence   │  state saved in Postgres, resumes after a crash
    └────────────────┬───────────────┘
                     │  RabbitMQ · agent.tasks ▼   agent.results ▲
    ┌────────────────▼───────────────┐
    │ 8 agents · agent-runtime       │  FR-03 to FR-07  (zoom-in: diagram 3)
    │ evidence → RCA → plan → comms  │  Spring AI → Ollama · read-only tools
    └────────────────┬───────────────┘
                     │  Kafka · investigation.events (fix proposed)
    ┌────────────────▼───────────────┐
    │ remediation-service            │  FR-06, FR-08
    │ approval rules · signing       │  ◀── Priya approves in the dashboard (no self-approval)
    └────────────────┬───────────────┘
                     │  RabbitMQ · remediation.commands (signed, approved only)
    ┌────────────────▼───────────────┐
    │ remediation-executor           │  FR-08
    │ verify · run · check · undo    │  the ONLY service allowed to change anything
    └────────────────┬───────────────┘
                     │  HTTP · admin endpoint, e.g. rollback v2.3 → v2.2
    ┌────────────────▼───────────────┐
    │ DemoCorp Bank apps             │
    │ fixed? Prometheus confirms     │  error rate back to normal → incident MITIGATED → RESOLVED
    └────────────────┬───────────────┘
                     │  Kafka · remediation.events, incident resolved
    ┌────────────────▼───────────────┐
    │ knowledge-service              │  FR-10
    │ postmortem → review → index    │  the next similar incident finds it (FR-05)
    └────────────────────────────────┘

    Alongside every step:
      audit-service ........ reads every Kafka topic → hash-chained log      (FR-09)
      communication-service  agent drafts → human approves → send          (FR-07)
```

## Diagram 3: Multi-agent investigation (zoom-in)

```
                  investigation-orchestrator
                  (supervisor + evidence board)
                                 │  RabbitMQ · agent.tasks
                                 │  PHASE 1 · all five at the same time
     ┌─────────────┬─────────────┴─────────────┬─────────────┐
     ▼             ▼             ▼             ▼             ▼
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│Monitoring│  │   Log    │  │  Change  │  │ Security │  │Knowledge │
│  PromQL  │  │  LogQL   │  │ deploys  │  │auth logs │  │KB search │
└────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
     └─────────────┴─────────────┼─────────────┴─────────────┘
                                 │  findings with evidence IDs → evidence board
                   ┌─────────────▼─────────────┐
                   │ PHASE 2 · RCA agent       │  confidence score per cause
                   │ ranked root causes        │  claims without evidence are rejected
                   └─────────────┬─────────────┘
                                 │
                   ┌─────────────▼─────────────┐
                   │ PHASE 3 · Remediation     │  picks only from the action allowlist
                   │ corrective + preventive   │  → remediation-service → human approval
                   └─────────────┬─────────────┘
                                 │
                   ┌─────────────▼─────────────┐
                   │ Communication agent       │  drafts on every status change
                   │ exec · eng · customer     │  → communication-service → human approval
                   └───────────────────────────┘

  Every agent (from agent-sdk): Spring AI → Ollama · read-only tools · structured
  output · limits on steps, tokens, time · retry → dead-letter · audit event per call
  If an agent times out, the investigation continues and marks the gap.
```
