package com.democorp.warroom.common.contracts;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Payloads published by agent-service on {@code investigation.v1}. */
public final class Investigation {

    private Investigation() {
    }

    /**
     * One piece of evidence. Every RCA claim must cite evidence ids, so nothing is invented.
     *
     * @param id     stable id inside the investigation, e.g. E-3
     * @param agent  which agent found it
     * @param source PROMETHEUS, LOKI, CHANGE, KNOWLEDGE, SECURITY
     * @param tags   machine-readable facts, e.g. "error_rate_high:payment-service"
     * @param query  the exact PromQL / LogQL / search used, so a human can re-run it
     */
    public record Evidence(String id, String agent, String source, String title, String detail, String query,
                           Instant observedAt, List<String> tags) {
    }

    public record Started(String investigationId, String incidentId, List<String> agents, Instant startedAt) {
    }

    /** status: DONE, TIMED_OUT or FAILED. A failed agent is a gap, not a failed investigation. */
    public record AgentFinding(String investigationId, String incidentId, String agent, String status,
                               String summary, List<Evidence> evidence, long durationMs) {
    }

    /**
     * @param category          cause category from the cause catalog (e.g. BAD_RELEASE)
     * @param confidence        0..1, computed from the factors below
     * @param confidenceFactors evidenceSupport, sourceDiversity, temporalPrecedence, historicalMatch, modelAssessment
     * @param evidenceIds       evidence cited for this cause (never empty)
     */
    public record Hypothesis(int rank, String category, String title, double confidence, String explanation,
                             List<String> evidenceIds, Map<String, Double> confidenceFactors) {
    }

    /** method: LLM (model + rules) or RULES (fallback when the model is unavailable). */
    public record RcaResult(String investigationId, String incidentId, List<Hypothesis> hypotheses,
                            String method, String model, long durationMs, List<String> gaps) {
    }

    public record ProposedAction(String action, String target, String risk, String description, String reason) {
    }

    /**
     * @param corrective  actions that need human approval before anything changes (FR-08)
     * @param preventive  follow-up actions so it does not happen again (FR-06)
     * @param closeAsFalsePositive the planner thinks nothing is broken
     */
    public record RemediationProposal(String investigationId, String incidentId, String rootCause,
                                      List<ProposedAction> corrective, List<String> preventive,
                                      boolean closeAsFalsePositive, String rationale) {
    }

    public record CommsDraft(String audience, String channel, String subject, String body) {
    }

    public record CommsDrafts(String investigationId, String incidentId, String trigger, List<CommsDraft> drafts,
                              String method) {
    }

    public record Completed(String investigationId, String incidentId, String outcome, List<String> gaps,
                            long durationMs) {
    }
}
