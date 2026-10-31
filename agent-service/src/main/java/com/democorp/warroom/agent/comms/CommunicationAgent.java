package com.democorp.warroom.agent.comms;

import com.democorp.warroom.agent.llm.LlmClient;
import com.democorp.warroom.common.contracts.IncidentSnapshot;
import com.democorp.warroom.common.contracts.Investigation.CommsDraft;
import com.democorp.warroom.common.contracts.Investigation.CommsDrafts;
import com.democorp.warroom.common.contracts.Investigation.Evidence;
import com.democorp.warroom.common.contracts.Investigation.Hypothesis;
import com.democorp.warroom.common.contracts.Investigation.RcaResult;
import com.democorp.warroom.common.contracts.Investigation.RemediationProposal;
import com.democorp.warroom.agent.comms.DraftTemplates.Facts;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FR-07: drafts stakeholder messages for executives, engineers and customers.
 * Drafts only: incident-service holds them until a human approves and sends.
 */
@Component
public class CommunicationAgent {

    public static final String NAME = "Communication agent";
    private static final List<String> AUDIENCES = List.of("EXECUTIVE", "ENGINEERING", "CUSTOMER");

    private final LlmClient llm;

    public CommunicationAgent(LlmClient llm) {
        this.llm = llm;
    }

    public CommsDrafts afterInvestigation(String investigationId, IncidentSnapshot incident, RcaResult rca,
                                          RemediationProposal plan, List<Evidence> evidence) {
        Facts facts = facts(incident, "IDENTIFIED", rca, plan, evidence);
        Optional<JsonNode> answer = llm.askJson("comms", system(), user(facts));
        if (answer.isPresent()) {
            List<CommsDraft> templates = DraftTemplates.all(facts);
            List<CommsDraft> drafts = new ArrayList<>();
            for (int i = 0; i < AUDIENCES.size(); i++) {
                JsonNode d = answer.get().path(AUDIENCES.get(i).toLowerCase());
                String subject = d.path("subject").asText("").trim();
                String body = d.path("body").asText("").trim();
                drafts.add(subject.isEmpty() || body.isEmpty() ? templates.get(i)
                        : new CommsDraft(AUDIENCES.get(i), templates.get(i).channel(), subject, body));
            }
            return new CommsDrafts(investigationId, incident.incidentId(), "IDENTIFIED", drafts, "LLM");
        }
        return new CommsDrafts(investigationId, incident.incidentId(), "IDENTIFIED", DraftTemplates.all(facts), "TEMPLATE");
    }

    /** Quick status-change updates (mitigated / resolved / false alarm): templates, no LLM wait. */
    public CommsDrafts statusUpdate(String investigationId, IncidentSnapshot incident, String rootCause) {
        Facts facts = new Facts(incident.incidentId(), incident.title(), String.join(", ", incident.services()),
                incident.severity().name(), incident.status().name(), rootCause, null, null, null);
        return new CommsDrafts(investigationId, incident.incidentId(), incident.status().name(),
                DraftTemplates.all(facts), "TEMPLATE");
    }

    private static Facts facts(IncidentSnapshot incident, String status, RcaResult rca, RemediationProposal plan,
                               List<Evidence> evidence) {
        Hypothesis top = rca.hypotheses().isEmpty() ? null : rca.hypotheses().get(0);
        String action = plan.closeAsFalsePositive() ? "close as false alarm"
                : plan.corrective().isEmpty() ? null : plan.corrective().get(0).description();
        String cited = top == null ? null : evidence.stream().filter(e -> top.evidenceIds().contains(e.id()))
                .limit(4).map(e -> e.id() + " " + e.title()).collect(Collectors.joining("; "));
        return new Facts(incident.incidentId(), incident.title(), String.join(", ", incident.services()),
                incident.severity().name(), status, top == null ? null : top.title(),
                top == null ? null : Math.round(top.confidence() * 100) + "%", action, cited);
    }

    private static String system() {
        return """
                You are the communication agent for DemoCorp Bank's incident war room.
                Write three short drafts about the incident, one per audience:
                - executive: business impact, no jargon, 3-4 sentences
                - engineering: technical cause, evidence ids, proposed fix
                - customer: status page text, calm and simple, never blame, never mention internal names
                Never promise a time you were not given. Never say a fix is done if it is only proposed.
                Answer with JSON only:
                {"executive":{"subject":"","body":""},"engineering":{"subject":"","body":""},"customer":{"subject":"","body":""}}""";
    }

    private static String user(Facts f) {
        return "Incident " + f.incidentId() + " (" + f.severity() + "): " + f.title()
                + "\nServices: " + f.services()
                + "\nProbable cause: " + f.rootCause() + " (confidence " + f.confidence() + ")"
                + "\nProposed fix (awaiting approval): " + f.action()
                + "\nKey evidence: " + f.evidence();
    }
}
