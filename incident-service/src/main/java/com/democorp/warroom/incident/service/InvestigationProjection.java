package com.democorp.warroom.incident.service;

import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.contracts.Investigation;
import com.democorp.warroom.common.contracts.Remediation;
import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.EventEnvelope;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.messaging.IdempotencyGuard;
import com.democorp.warroom.incident.comms.CommsDraftEntity;
import com.democorp.warroom.incident.comms.CommsDraftRepository;
import com.democorp.warroom.incident.domain.Incident;
import com.democorp.warroom.incident.domain.IncidentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Keeps the incident in step with the agents and the approval workflow:
 * investigation started → INVESTIGATING, RCA → IDENTIFIED, fix approved → MITIGATING,
 * fix verified → MITIGATED, fix failed → back to IDENTIFIED. Also stores findings, RCA and drafts for the UI.
 */
@Service
public class InvestigationProjection {

    private static final String AGENTS = "agents";

    private final IncidentService incidents;
    private final IncidentRepository incidentRepo;
    private final CommsDraftRepository drafts;
    private final JdbcTemplate jdbc;
    private final EventCodec codec;
    private final IdempotencyGuard guard;

    public InvestigationProjection(IncidentService incidents, IncidentRepository incidentRepo, CommsDraftRepository drafts,
                                   JdbcTemplate jdbc, EventCodec codec, IdempotencyGuard guard) {
        this.incidents = incidents;
        this.incidentRepo = incidentRepo;
        this.drafts = drafts;
        this.jdbc = jdbc;
        this.codec = codec;
        this.guard = guard;
    }

    @Transactional
    public void onInvestigationEvent(EventEnvelope e) {
        if (e.incidentId() == null || !incidentRepo.existsById(e.incidentId())
                || !guard.firstTime(e.messageId(), "incident.investigation")) {
            return;
        }
        String id = e.incidentId();
        switch (e.type()) {
            case EventTypes.INVESTIGATION_STARTED -> {
                Investigation.Started s = codec.payload(e, Investigation.Started.class);
                incidents.moveIfAllowed(id, IncidentStatus.INVESTIGATING, AGENTS,
                        s.agents().size() + " agents dispatched");
            }
            case EventTypes.AGENT_FINDING -> {
                Investigation.AgentFinding f = codec.payload(e, Investigation.AgentFinding.class);
                jdbc.update("INSERT INTO incident_finding (incident_id, agent, status, summary, payload, at) "
                                + "VALUES (?, ?, ?, ?, ?::jsonb, ?)",
                        id, f.agent(), f.status(), f.summary(), e.payload().toString(), Timestamp.from(Instant.now()));
                incidents.addTimelineEntry(id, "AGENT", f.agent() + ": " + f.summary()
                        + ("DONE".equals(f.status()) ? "" : " [" + f.status() + "]"), f.agent());
            }
            case EventTypes.RCA_COMPLETED -> {
                Investigation.RcaResult rca = codec.payload(e, Investigation.RcaResult.class);
                jdbc.update("INSERT INTO incident_rca (incident_id, payload, updated_at) VALUES (?, ?::jsonb, now()) "
                        + "ON CONFLICT (incident_id) DO UPDATE SET payload = EXCLUDED.payload, updated_at = now()",
                        id, e.payload().toString());
                if (!rca.hypotheses().isEmpty()) {
                    Investigation.Hypothesis top = rca.hypotheses().get(0);
                    Incident incident = incidents.find(id);
                    incident.recordRca(top.title(), top.confidence());
                    incidentRepo.save(incident);
                    incidents.moveIfAllowed(id, IncidentStatus.IDENTIFIED, "RCA agent",
                            "Root cause: " + top.title() + " (" + Math.round(top.confidence() * 100) + "%, " + rca.method() + ")");
                } else {
                    incidents.addTimelineEntry(id, "RCA", "RCA could not name a cause: needs a human", "RCA agent");
                }
            }
            case EventTypes.REMEDIATION_PROPOSED -> {
                Investigation.RemediationProposal p = codec.payload(e, Investigation.RemediationProposal.class);
                jdbc.update("UPDATE incident_rca SET plan = ?::jsonb, updated_at = now() WHERE incident_id = ?",
                        e.payload().toString(), id);
                String text = p.closeAsFalsePositive() ? "Planner recommends closing as a false alarm"
                        : p.corrective().isEmpty() ? p.rationale()
                        : "Fix proposed: " + String.join(", ", p.corrective().stream()
                        .map(a -> a.action() + " on " + a.target() + " (" + a.risk() + ")").toList()) + " — awaiting approval";
                incidents.addTimelineEntry(id, "PROPOSAL", text, "Remediation planner");
            }
            case EventTypes.COMMS_DRAFTED -> {
                Investigation.CommsDrafts d = codec.payload(e, Investigation.CommsDrafts.class);
                for (Investigation.CommsDraft draft : d.drafts()) {
                    drafts.findByIncidentIdAndAudienceAndStatus(id, draft.audience(), CommsDraftEntity.DRAFT)
                            .forEach(CommsDraftEntity::supersede);
                    drafts.save(new CommsDraftEntity("MSG-" + drafts.nextNumber(), id, d.trigger(), draft.audience(),
                            draft.channel(), draft.subject(), draft.body(), d.method()));
                }
                incidents.addTimelineEntry(id, "COMMS", d.drafts().size() + " message drafts ready for review ("
                        + d.method().toLowerCase() + ")", "Communication agent");
            }
            case EventTypes.INVESTIGATION_COMPLETED -> {
                Investigation.Completed c = codec.payload(e, Investigation.Completed.class);
                incidents.addTimelineEntry(id, "INVESTIGATION", "Investigation " + c.outcome().toLowerCase() + " in "
                        + Math.max(1, c.durationMs() / 1000) + " s" + (c.gaps().isEmpty() ? "" : " · gaps: "
                        + String.join("; ", c.gaps())), "Supervisor");
            }
            default -> { /* not shown on the incident */ }
        }
    }

    @Transactional
    public void onRemediationEvent(EventEnvelope e) {
        if (e.incidentId() == null || !incidentRepo.existsById(e.incidentId())
                || !guard.firstTime(e.messageId(), "incident.remediation")) {
            return;
        }
        String id = e.incidentId();
        switch (e.type()) {
            case EventTypes.ACTION_APPROVED -> {
                Remediation.Decision d = codec.payload(e, Remediation.Decision.class);
                incidents.moveIfAllowed(id, IncidentStatus.MITIGATING, String.join(" + ", d.approvals()),
                        d.action() + " on " + d.target() + " approved by " + String.join(" and ", d.approvals()));
            }
            case EventTypes.ACTION_REJECTED -> {
                Remediation.Decision d = codec.payload(e, Remediation.Decision.class);
                incidents.addTimelineEntry(id, "DECISION", d.action() + " rejected by " + d.decidedBy()
                        + (d.reason() == null ? "" : ": " + d.reason()), d.decidedBy());
            }
            case EventTypes.ACTION_EXECUTED -> {
                Remediation.Result r = codec.payload(e, Remediation.Result.class);
                incidents.moveIfAllowed(id, IncidentStatus.MITIGATED, "remediation-executor",
                        r.action() + " verified: " + r.verification());
            }
            case EventTypes.ACTION_FAILED -> {
                Remediation.Result r = codec.payload(e, Remediation.Result.class);
                if (!incidents.moveIfAllowed(id, IncidentStatus.IDENTIFIED, "remediation-executor",
                        r.action() + " " + r.outcome() + ": " + r.message())) {
                    incidents.addTimelineEntry(id, "EXECUTION", r.action() + " " + r.outcome() + ": " + r.message(),
                            "remediation-executor");
                }
            }
            default -> { }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> investigation(String incidentId) {
        List<Map<String, Object>> findings = jdbc.queryForList(
                "SELECT agent, status, summary, payload::text AS payload, at FROM incident_finding WHERE incident_id = ? ORDER BY id",
                incidentId);
        List<Map<String, Object>> rca = jdbc.queryForList(
                "SELECT payload::text AS payload, plan::text AS plan, updated_at FROM incident_rca WHERE incident_id = ?", incidentId);
        return Map.of("findings", findings.stream().map(f -> Map.of(
                        "agent", f.get("agent"), "status", f.get("status"), "summary", String.valueOf(f.get("summary")),
                        "at", f.get("at"), "finding", read((String) f.get("payload")))).toList(),
                "rca", rca.isEmpty() ? Map.of() : read((String) rca.get(0).get("payload")),
                "plan", rca.isEmpty() || rca.get(0).get("plan") == null ? Map.of() : read((String) rca.get(0).get("plan")));
    }

    private JsonNode read(String json) {
        try {
            return codec.mapper().readTree(json);
        } catch (Exception ex) {
            return codec.mapper().createObjectNode();
        }
    }
}
