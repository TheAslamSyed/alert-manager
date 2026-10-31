package com.democorp.warroom.agent.rca;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The causes the RCA agent may name. The LLM must pick from these categories, so it cannot invent a cause
 * with no fix; each category says which evidence supports it, the allow-listed fix and preventive steps.
 */
@Component
public class CauseCatalog {

    /** Evidence tags that mean "something is actually broken" (used to rule out a false alarm). */
    public static final List<String> REAL_PROBLEM_TAGS = List.of("error_rate_high", "card_error_timeout", "card_error_tls",
            "card_throttled", "fraud_declines_high", "duplicate_debits", "ledger_backlog", "cache_down",
            "credential_stuffing", "token_abuse", "log_pool_exhausted", "db_pool_saturated");

    private final List<Cause> causes = List.of(
            new Cause("BAD_RELEASE", "Release {version} of {service} introduced a fault (database connections leak)",
                    List.of("recent_deploy"), List.of("log_pool_exhausted", "db_pool_saturated", "error_rate_high", "latency_high"),
                    true, "ROLLBACK_DEPLOYMENT", null,
                    List.of("Alert when DB pool usage stays above 80% for 2 minutes",
                            "Add a connection-leak test to the release pipeline",
                            "Canary new releases on 5% of traffic before full rollout")),
            new Cause("CARD_NETWORK_OUTAGE", "The external primary card network is down (not a DemoCorp bug)",
                    List.of("card_error_timeout", "log_card_timeout"), List.of("error_rate_high", "latency_high"),
                    false, "FAILOVER_PAYMENT_ROUTE", null,
                    List.of("Automatic failover when the primary route error rate is above 20% for 2 minutes",
                            "Health-check both card processors every 30 seconds")),
            new Cause("EXPIRED_CERTIFICATE", "The TLS certificate for the card network connection has expired",
                    List.of("card_error_tls", "log_cert_expired"), List.of("error_rate_high"),
                    false, "ROTATE_CERTIFICATE", null,
                    List.of("Alert 30 days before any certificate expires", "Automate certificate renewal")),
            new Cause("PROCESSOR_RATE_LIMIT", "Traffic exceeded the card processor's rate limit (HTTP 429)",
                    List.of("card_throttled", "log_rate_limited"), List.of("error_rate_high"),
                    false, "ENABLE_THROTTLING", null,
                    List.of("Request a higher processor quota before payday", "Keep queue-and-throttle on during peaks")),
            new Cause("DUPLICATE_DEBITS", "A retry config change disabled idempotency keys, so retried payments debit twice",
                    List.of("duplicate_debits", "log_duplicate_debit"), List.of("config_change"),
                    true, "REVERT_CONFIG", "REFUND_DUPLICATES",
                    List.of("Contract test: every payment request must carry an idempotency key",
                            "Config changes to payment retries need a second reviewer")),
            new Cause("FRAUD_RULE_CHANGE", "A fraud rule change is declining legitimate payments",
                    List.of("fraud_declines_high", "log_fraud_declines"), List.of("config_change"),
                    true, "REVERT_FRAUD_RULE", null,
                    List.of("Run new fraud rules in shadow mode for 24 hours first", "Alert when decline rate doubles")),
            new Cause("LEDGER_POISON_MESSAGE", "A malformed ledger message is blocking the account-service consumer",
                    List.of("ledger_backlog", "log_ledger_poison"), List.of(),
                    false, "MOVE_MESSAGE_TO_DLQ", null,
                    List.of("Dead-letter a ledger message after 3 failed attempts", "Validate ledger message format at the producer")),
            new Cause("CACHE_FAILURE", "The account cache is down, so every balance read overloads the database",
                    List.of("cache_down", "log_cache_down"), List.of("latency_high", "db_pool_saturated"),
                    false, "RESTART_CACHE", null,
                    List.of("Add a circuit breaker around the cache", "Alert on cache availability, not only on latency")),
            new Cause("CREDENTIAL_STUFFING", "A credential-stuffing attack is replaying leaked passwords from many IPs",
                    List.of("credential_stuffing"), List.of("log_login_failures"),
                    false, "ENABLE_STEP_UP_AUTH", "BLOCK_IP_RANGE",
                    List.of("Check passwords against breached-password lists", "Add bot detection on login")),
            new Cause("API_TOKEN_ABUSE", "Partner API token {token} is compromised and scraping account data",
                    List.of("token_abuse"), List.of(),
                    false, "REVOKE_API_TOKEN", null,
                    List.of("Alert per token when volume is 5x normal", "Issue partner tokens with narrower scopes")),
            new Cause("DATABASE_CAPACITY", "The {service} database is undersized for current traffic",
                    List.of("db_pool_saturated"), List.of("latency_high", "error_rate_high"),
                    false, null, null,
                    List.of("Review DB pool size and capacity plan")),
            new Cause("FALSE_ALARM", "No real problem: a scheduled batch job briefly slowed {service} and the alert is too sensitive",
                    List.of("log_batch_job"), List.of("latency_high", "metrics_normal", "no_attack_signals"),
                    false, null, null,
                    List.of("Raise the latency alert duration to 5 minutes during the batch window")));

    public List<Cause> all() {
        return causes;
    }

    public Optional<Cause> find(String category) {
        return causes.stream().filter(c -> c.category().equalsIgnoreCase(category)).findFirst();
    }

    /**
     * @param primary      at least one of these evidence tags must be present
     * @param supporting   these raise the score
     * @param changeDriven the cause is a change (deploy/config) that happened before the symptoms
     * @param action       allow-listed corrective action (null = no safe automatic fix)
     * @param secondAction optional second corrective action
     */
    public record Cause(String category, String title, List<String> primary, List<String> supporting,
                        boolean changeDriven, String action, String secondAction, List<String> preventive) {
    }
}
