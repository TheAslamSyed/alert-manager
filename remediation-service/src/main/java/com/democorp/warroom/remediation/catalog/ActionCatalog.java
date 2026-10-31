package com.democorp.warroom.remediation.catalog;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The allowlist. Only these actions can ever run. Each has a risk level (how many approvers),
 * and a PromQL check that proves the fix worked.
 */
@Component
public class ActionCatalog {

    private static final String ERROR_RATIO = "sum(rate(democorp_requests_total{service=\"%s\",outcome=\"error\"}[30s])) "
            + "/ sum(rate(democorp_requests_total{service=\"%s\"}[30s]))";
    private static final String CARD_ERRORS = "(sum(rate(democorp_card_auth_total{outcome=~\"error_.*|throttled\"}[30s])) or vector(0)) "
            + "/ sum(rate(democorp_card_auth_total[30s]))";

    private final Map<String, ActionSpec> specs = Map.ofEntries(
            spec("ROLLBACK_DEPLOYMENT", "MEDIUM", "Roll back to the last healthy release", ERROR_RATIO, 0.05),
            spec("FAILOVER_PAYMENT_ROUTE", "MEDIUM", "Switch card traffic to the backup processor", CARD_ERRORS, 0.05),
            spec("ROTATE_CERTIFICATE", "HIGH", "Install the renewed card-network certificate", CARD_ERRORS, 0.05),
            spec("ENABLE_THROTTLING", "LOW", "Queue and throttle card authorizations", CARD_ERRORS, 0.05),
            spec("REVERT_CONFIG", "MEDIUM", "Restore the previous payment retry configuration",
                    "sum(increase(democorp_duplicate_debits_total[30s]))", 0.5),
            spec("REFUND_DUPLICATES", "HIGH", "Refund duplicate debits to customers", null, 0),
            spec("REVERT_FRAUD_RULE", "MEDIUM", "Restore the previous fraud threshold",
                    "sum(rate(democorp_card_auth_total{outcome=\"declined_fraud\"}[30s])) / sum(rate(democorp_card_auth_total[30s]))", 0.08),
            spec("MOVE_MESSAGE_TO_DLQ", "MEDIUM", "Move the poison ledger message to the dead-letter queue",
                    "max(democorp_ledger_queue_depth)", 50),
            spec("RESTART_CACHE", "LOW", "Restart the account cache", "1 - min(democorp_cache_up)", 0.5),
            spec("ENABLE_STEP_UP_AUTH", "MEDIUM", "Require step-up verification at login",
                    "sum(rate(democorp_login_attempts_total{outcome=\"failure\"}[30s]))", 2),
            spec("BLOCK_IP_RANGE", "MEDIUM", "Block the attacking IP ranges",
                    "sum(rate(democorp_login_attempts_total{outcome=\"failure\"}[30s]))", 2),
            spec("REVOKE_API_TOKEN", "HIGH", "Revoke the compromised partner API token",
                    "sum(rate(democorp_partner_api_records_total{token=\"%s\"}[30s])) or vector(0)", 5));

    private static Map.Entry<String, ActionSpec> spec(String action, String risk, String description,
                                                      String verifyQuery, double verifyBelow) {
        return Map.entry(action, new ActionSpec(action, risk, "HIGH".equals(risk) ? 2 : 1, description,
                verifyQuery, verifyBelow));
    }

    public Optional<ActionSpec> find(String action) {
        return Optional.ofNullable(specs.get(action));
    }

    public List<ActionSpec> all() {
        return specs.values().stream().sorted((a, b) -> a.action().compareTo(b.action())).toList();
    }

    /**
     * @param verifyQuery PromQL that must drop below {@code verifyBelow}; %s is replaced with the target
     */
    public record ActionSpec(String action, String risk, int approvalsNeeded, String description,
                             String verifyQuery, double verifyBelow) {
        public String verifyQueryFor(String target) {
            return verifyQuery == null ? null : verifyQuery.replace("%s", target);
        }
    }
}
