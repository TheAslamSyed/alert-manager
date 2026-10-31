package com.democorp.warroom.knowledge.learning;

import com.democorp.warroom.common.contracts.IncidentSnapshot;
import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.contracts.Investigation;
import com.democorp.warroom.common.contracts.Remediation;
import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.EventEnvelope;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.messaging.IdempotencyGuard;
import com.democorp.warroom.knowledge.domain.Article;
import com.democorp.warroom.knowledge.domain.ArticleRepository;
import com.democorp.warroom.knowledge.domain.IncidentFact;
import com.democorp.warroom.knowledge.domain.IncidentFactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FR-10: collects facts about each incident from the event stream and, when it is RESOLVED,
 * writes a postmortem draft. A human reviews and publishes it; only then is it indexed for search.
 */
@Service
public class PostmortemService {

    private static final Logger log = LoggerFactory.getLogger(PostmortemService.class);

    private final IncidentFactRepository facts;
    private final ArticleRepository articles;
    private final EventCodec codec;
    private final IdempotencyGuard guard;
    private final JdbcTemplate jdbc;

    public PostmortemService(IncidentFactRepository facts, ArticleRepository articles, EventCodec codec,
                             IdempotencyGuard guard, JdbcTemplate jdbc) {
        this.facts = facts;
        this.articles = articles;
        this.codec = codec;
        this.guard = guard;
        this.jdbc = jdbc;
    }

    @Transactional
    public void onEvent(EventEnvelope e, String consumer) {
        if (e.incidentId() == null || !guard.firstTime(e.messageId(), consumer)) {
            return;
        }
        // Three topic listeners may touch the same incident at once: serialise them per incident.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", e.incidentId());
        IncidentFact fact = facts.findById(e.incidentId()).orElseGet(() -> new IncidentFact(e.incidentId()));
        switch (e.type()) {
            case EventTypes.INCIDENT_OPENED, EventTypes.INCIDENT_UPDATED, EventTypes.INCIDENT_STATUS_CHANGED -> {
                IncidentSnapshot s = codec.payload(e, IncidentSnapshot.class);
                fact.put("title", s.title());
                fact.put("primaryService", s.primaryService());
                fact.put("services", s.services());
                fact.put("severity", s.severity().name());
                fact.put("openedAt", s.openedAt().toString());
                fact.put("signals", s.signals().size());
                fact.put("notes", s.correlationNotes());
                facts.save(fact);
                if (EventTypes.INCIDENT_STATUS_CHANGED.equals(e.type()) && s.status() == IncidentStatus.RESOLVED) {
                    writeDraft(fact, s.updatedAt(), s.changedBy());
                }
                return;
            }
            case EventTypes.RCA_COMPLETED -> {
                Investigation.RcaResult rca = codec.payload(e, Investigation.RcaResult.class);
                if (!rca.hypotheses().isEmpty()) {
                    Investigation.Hypothesis top = rca.hypotheses().get(0);
                    fact.put("rootCause", top.title());
                    fact.put("rootCauseExplanation", top.explanation());
                    fact.put("category", top.category());
                    fact.put("confidence", top.confidence());
                    fact.put("rcaMethod", rca.method());
                }
            }
            case EventTypes.REMEDIATION_PROPOSED -> {
                Investigation.RemediationProposal p = codec.payload(e, Investigation.RemediationProposal.class);
                fact.put("preventive", p.preventive());
            }
            case EventTypes.ACTION_APPROVED -> {
                Remediation.Decision d = codec.payload(e, Remediation.Decision.class);
                fact.put("approvedBy", d.approvals());
            }
            case EventTypes.ACTION_EXECUTED, EventTypes.ACTION_FAILED -> {
                Remediation.Result r = codec.payload(e, Remediation.Result.class);
                fact.put("action", r.action());
                fact.put("actionTarget", r.target());
                fact.put("actionOutcome", r.outcome());
                fact.put("verification", r.verification());
            }
            default -> {
                return;
            }
        }
        facts.save(fact);
    }

    @SuppressWarnings("unchecked")
    private void writeDraft(IncidentFact fact, Instant resolvedAt, String resolvedBy) {
        String id = "PM-" + fact.getIncidentId();
        if (articles.existsById(id)) {
            return;
        }
        Map<String, Object> f = fact.getFacts();
        Instant opened = Instant.parse((String) f.get("openedAt"));
        int minutes = (int) Math.max(1, Duration.between(opened, resolvedAt).toMinutes());
        String rootCause = (String) f.getOrDefault("rootCause", "Not determined");
        String action = (String) f.get("action");
        List<String> preventive = (List<String>) f.getOrDefault("preventive", List.of());
        List<String> approvers = (List<String>) f.getOrDefault("approvedBy", List.of());

        String resolution = action == null ? "Resolved without an automated action."
                : action + " on " + f.get("actionTarget") + " (" + f.get("actionOutcome") + ")"
                + (approvers.isEmpty() ? "" : ", approved by " + String.join(" and ", approvers));

        List<String> body = new ArrayList<>();
        body.add("## Summary\n" + f.get("title") + " on " + f.get("primaryService") + ". Severity "
                + f.get("severity") + ", resolved in " + minutes + " minutes.");
        body.add("## Impact\nServices affected: " + f.get("services") + ". Signals correlated: " + f.get("signals") + ".");
        body.add("## Root cause\n" + rootCause
                + (f.get("confidence") == null ? "" : " (confidence " + Math.round(((Number) f.get("confidence")).doubleValue() * 100) + "%)")
                + (f.get("rootCauseExplanation") == null ? "" : "\n\n" + f.get("rootCauseExplanation")));
        body.add("## What changed before it started\n" + String.join("\n", (List<String>) f.getOrDefault("notes", List.of())));
        body.add("## What fixed it\n" + resolution + (f.get("verification") == null ? "" : "\nVerification: " + f.get("verification")));
        body.add("## Preventive actions\n" + (preventive.isEmpty() ? "None recorded." : "- " + String.join("\n- ", preventive)));

        Article draft = new Article(id, "POSTMORTEM", fact.getIncidentId(),
                "Postmortem " + fact.getIncidentId() + ": " + f.get("title"), (String) f.get("primaryService"),
                f.get("title") + ". Resolved in " + minutes + " min by " + (action == null ? "manual action" : action) + ".",
                rootCause, action, resolution, minutes, String.join("\n\n", body), Article.IN_REVIEW, opened);
        articles.save(draft);
        log.info("Postmortem draft {} written for {} (resolved by {})", id, fact.getIncidentId(), resolvedBy);
    }
}
