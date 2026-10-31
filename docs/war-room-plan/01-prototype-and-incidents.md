# War Room Agent — Prototype, Incidents and Agent Packaging

Prototype (private until shared from the page's Share menu): https://claude.ai/artifact/3WGSCVR7X4oWEesaJRRSxK

## 1. Prototype screens
Dark "command center" look, Roboto (Material UI default). Use **Play** to click between screens. All numbers are **sample data**.

| # | Screen | What it shows | Logged in as |
|---|---|---|---|
| 1 | Command Center | Incident list, key numbers, the 8 agents' status, live agent activity, "Run scenario" demo control | Asha (Responder) |
| 2 | Incident & RCA | Status steps, 3 ranked root causes with confidence, how the 87% was calculated, evidence (PromQL, LogQL, deploy record), agent phases, timeline, audit chain | Asha |
| 3 | Approval queue | Fix proposal, dry run, undo plan, approval rule. Approve and Reject work | Priya (Approver) |
| 4 | Communications | Drafts for executives, engineers and customers, plus sent history | Priya |
| 5 | Knowledge base | Similar past incidents with match %, postmortem draft waiting for review | Asha |
| 6 | Evaluation & MTTR | All 13 scenarios: correct cause, confidence, manual vs agent time to resolve | Sarah (Viewer) |

## 2. The 13 fintech incidents
DemoCorp Bank: **banking-api, payment-service, account-service**, plus a fake **card network** (primary + backup route).

| ID | Incident | What customers see | Correct root cause | Fix (needs approval) | Main agents |
|---|---|---|---|---|---|
| FIN-01 | Bad release | Card transfers fail | v2.3 leaks database connections | Roll back release (medium) | Change, Log |
| FIN-02 | Card network outage | Card payments time out | External card network down | Switch to backup processor (medium) | Monitoring, Log |
| FIN-03 | Expired certificate | All card payments fail at once | Secure connection certificate expired | Replace certificate (high, 2 approvers) | Log, Change |
| FIN-04 | Payday traffic spike | Payments slow and rejected | Processor rate limit hit (HTTP 429) | Throttle and queue payments (low) | Monitoring |
| FIN-05 | Duplicate debits | Customers charged twice | Retry config change dropped the idempotency key | Revert config (medium) + refund duplicates (high) | Change, Log |
| FIN-06 | Fraud rule mistake | Good payments declined | Fraud threshold set too strict | Revert fraud rule (medium) | Change, Monitoring |
| FIN-07 | Stuck ledger | Transfer succeeds, balance doesn't update | One bad message blocks the ledger consumer | Move message to DLQ + restart consumer (medium) | Log, Monitoring |
| FIN-08 | Cache crash | Balance checks very slow | Redis down, account DB overloaded | Restart cache + throttle reads (low) | Monitoring + correlation |
| FIN-09 | Disk full | Transfers can't be saved | Transaction DB disk full | Archive old data + add storage (high) | Monitoring, Log |
| FIN-10 | Clock drift | Some one-time passcodes rejected | One server's clock out of sync | Resync clock + remove server from rotation (low) | Log, Monitoring |
| FIN-11 | Login attack | Accounts locked, failed logins spike | Credential-stuffing attack | Step-up login check + block IP ranges (medium) | Security |
| FIN-12 | Partner token misuse | Unusual data access | Compromised partner API token scraping accounts | Revoke token (high) | Security |
| FIN-13 | False alarm | Nothing | Alert too sensitive during a batch job | Close as false alarm, tune alert | Correlation |

Notes:
- Each incident also has **preventive actions** (new alerts, pipeline tests, runbook updates).
- About **40 past incidents** will be seeded for the knowledge library.
- FIN-03, FIN-09 and FIN-10 are **simulated through logs and metrics** to keep DemoCorp light.

## 3. Agent packaging (confirmed)
One `agent-runtime` codebase, deployed as **8 containers**. A setting such as `AGENT_ROLE=log` picks the agent. Each container has its own queue and scales on its own.
