package com.democorp.warroom.agent.agents;

import com.democorp.warroom.agent.core.InvestigationContext;
import com.democorp.warroom.agent.rca.CauseCatalog;
import com.democorp.warroom.agent.rca.CauseCatalog.Cause;
import com.democorp.warroom.common.contracts.Investigation.Evidence;
import com.democorp.warroom.common.contracts.Investigation.Hypothesis;
import com.democorp.warroom.common.contracts.Investigation.ProposedAction;
import com.democorp.warroom.common.contracts.Investigation.RcaResult;
import com.democorp.warroom.common.contracts.Investigation.RemediationProposal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FR-06: turns the top root cause into corrective + preventive actions.
 * It may only pick allow-listed actions and only proposes them: remediation-service and a human decide.
 */
@Component
public class RemediationPlannerAgent {

    public static final String NAME = "Remediation planner";
    private static final double MIN_CONFIDENCE = 0.4;

    /** Risk hints (remediation-service owns the real policy). */
    static final Map<String, String> RISK = Map.ofEntries(
            Map.entry("ROLLBACK_DEPLOYMENT", "MEDIUM"), Map.entry("FAILOVER_PAYMENT_ROUTE", "MEDIUM"),
            Map.entry("ROTATE_CERTIFICATE", "HIGH"), Map.entry("ENABLE_THROTTLING", "LOW"),
            Map.entry("REVERT_CONFIG", "MEDIUM"), Map.entry("REFUND_DUPLICATES", "HIGH"),
            Map.entry("REVERT_FRAUD_RULE", "MEDIUM"), Map.entry("MOVE_MESSAGE_TO_DLQ", "MEDIUM"),
            Map.entry("RESTART_CACHE", "LOW"), Map.entry("ENABLE_STEP_UP_AUTH", "MEDIUM"),
            Map.entry("BLOCK_IP_RANGE", "MEDIUM"), Map.entry("REVOKE_API_TOKEN", "HIGH"));

    private final CauseCatalog catalog;

    public RemediationPlannerAgent(CauseCatalog catalog) {
        this.catalog = catalog;
    }

    public RemediationProposal plan(InvestigationContext ctx, RcaResult rca, List<Evidence> evidence) {
        String incidentId = ctx.incident().incidentId();
        if (rca.hypotheses().isEmpty()) {
            return new RemediationProposal(ctx.investigationId(), incidentId, "Unknown", List.of(),
                    List.of("Review alert coverage for this failure mode"), false,
                    "No root cause found with enough evidence. A human needs to investigate.");
        }
        Hypothesis top = rca.hypotheses().get(0);
        Optional<Cause> cause = catalog.find(top.category());
        List<String> preventive = cause.map(Cause::preventive).orElse(List.of());

        if ("FALSE_ALARM".equals(top.category())) {
            return new RemediationProposal(ctx.investigationId(), incidentId, top.title(), List.of(), preventive, true,
                    "Evidence shows no customer impact (" + pct(top.confidence()) + "). Recommend closing as a false alarm.");
        }
        if (top.confidence() < MIN_CONFIDENCE || cause.isEmpty() || cause.get().action() == null) {
            return new RemediationProposal(ctx.investigationId(), incidentId, top.title(), List.of(), preventive, false,
                    "No safe automatic fix for \"" + top.title() + "\" (" + pct(top.confidence())
                            + "). A human should decide the next step.");
        }

        String similar = evidence.stream()
                .filter(e -> "KNOWLEDGE".equals(e.source()) && e.tags().contains("similar_action:" + cause.get().action()))
                .map(e -> e.tags().get(0).replace("similar:", "")).findFirst()
                .map(ref -> " The same fix resolved " + ref + ".").orElse("");

        List<ProposedAction> corrective = new ArrayList<>();
        corrective.add(action(cause.get().action(), top, evidence, ctx,
                "Top cause: " + top.title() + " (" + pct(top.confidence()) + ")." + similar));
        if (cause.get().secondAction() != null) {
            corrective.add(action(cause.get().secondAction(), top, evidence, ctx, "Follow-up for the same cause."));
        }
        return new RemediationProposal(ctx.investigationId(), incidentId, top.title(), corrective, preventive, false,
                "Fix the top cause first; every action needs human approval before it runs.");
    }

    private ProposedAction action(String action, Hypothesis top, List<Evidence> evidence, InvestigationContext ctx,
                                  String reason) {
        return new ProposedAction(action, target(action, top, evidence, ctx), RISK.getOrDefault(action, "HIGH"),
                describe(action), reason);
    }

    private static String target(String action, Hypothesis top, List<Evidence> evidence, InvestigationContext ctx) {
        for (Evidence e : evidence) {
            if (!top.evidenceIds().contains(e.id())) {
                continue;
            }
            for (String tag : e.tags()) {
                String[] p = tag.split(":");
                if ("REVOKE_API_TOKEN".equals(action) && tag.startsWith("token_abuse:")) {
                    return p[1];
                }
                if ("ROLLBACK_DEPLOYMENT".equals(action) && tag.startsWith("recent_deploy:")) {
                    return p[1];
                }
            }
        }
        return switch (action) {
            case "MOVE_MESSAGE_TO_DLQ", "RESTART_CACHE" -> "account-service";
            case "ENABLE_STEP_UP_AUTH", "BLOCK_IP_RANGE", "REVOKE_API_TOKEN" -> "banking-api";
            case "FAILOVER_PAYMENT_ROUTE", "ROTATE_CERTIFICATE", "ENABLE_THROTTLING", "REVERT_CONFIG",
                 "REFUND_DUPLICATES", "REVERT_FRAUD_RULE" -> "payment-service";
            default -> ctx.incident().primaryService();
        };
    }

    static String describe(String action) {
        return switch (action) {
            case "ROLLBACK_DEPLOYMENT" -> "Roll back to the last healthy release";
            case "FAILOVER_PAYMENT_ROUTE" -> "Switch card traffic to the backup processor";
            case "ROTATE_CERTIFICATE" -> "Install the renewed card-network certificate";
            case "ENABLE_THROTTLING" -> "Queue and throttle card authorizations";
            case "REVERT_CONFIG" -> "Restore the previous payment retry configuration";
            case "REFUND_DUPLICATES" -> "Refund duplicate debits to customers";
            case "REVERT_FRAUD_RULE" -> "Restore the previous fraud threshold";
            case "MOVE_MESSAGE_TO_DLQ" -> "Move the poison ledger message to the dead-letter queue";
            case "RESTART_CACHE" -> "Restart the account cache";
            case "ENABLE_STEP_UP_AUTH" -> "Require step-up verification at login";
            case "BLOCK_IP_RANGE" -> "Block the attacking IP ranges";
            case "REVOKE_API_TOKEN" -> "Revoke the compromised partner API token";
            default -> action;
        };
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
