package com.democorp.bank.scenario;

import java.util.List;
import java.util.Set;

import static com.democorp.bank.scenario.FixAction.*;

/**
 * Practice incidents with a known correct answer (ground truth for the evaluation report).
 * {@code fixes} lists the actions that actually clear the fault; anything else has no effect.
 */
public enum Scenario {

    FIN_01("FIN-01", "Bad release leaks DB connections", "payment-service",
            "Release v2.3.0 of payment-service leaks database connections",
            List.of("v2.3", "connection", "leak", "pool", "release"), Set.of(ROLLBACK_DEPLOYMENT), false),
    FIN_02("FIN-02", "Card network outage", "payment-service",
            "The external primary card network is down (not a DemoCorp bug)",
            List.of("card network", "outage", "timeout", "primary"), Set.of(FAILOVER_PAYMENT_ROUTE), false),
    FIN_03("FIN-03", "Expired certificate to card network", "payment-service",
            "The TLS certificate used for the card network connection has expired",
            List.of("certificate", "expired", "tls", "pkix"), Set.of(ROTATE_CERTIFICATE), false),
    FIN_04("FIN-04", "Payday spike hits processor rate limit", "payment-service",
            "Payday traffic exceeded the card processor's rate limit (HTTP 429)",
            List.of("rate limit", "429", "throttl", "payday"), Set.of(ENABLE_THROTTLING), false),
    FIN_05("FIN-05", "Duplicate debits after retry change", "payment-service",
            "A retry config change disabled idempotency keys, so retried payments debit twice",
            List.of("duplicate", "idempotency", "retry", "config"), Set.of(REVERT_CONFIG), false),
    FIN_06("FIN-06", "Fraud rule change blocks good payments", "payment-service",
            "The fraud score threshold was changed from 0.85 to 0.35, declining legitimate payments",
            List.of("fraud", "threshold", "declin", "rule"), Set.of(REVERT_FRAUD_RULE), false),
    FIN_07("FIN-07", "Ledger consumer stuck on a bad message", "account-service",
            "A malformed (poison) ledger message blocks the account-service ledger consumer",
            List.of("ledger", "poison", "message", "consumer", "stuck"), Set.of(MOVE_MESSAGE_TO_DLQ), false),
    FIN_08("FIN-08", "Cache down overloads the account database", "account-service",
            "The account cache is down, so every balance read hits the database and overloads it",
            List.of("cache", "redis", "database", "overload"), Set.of(RESTART_CACHE), false),
    FIN_11("FIN-11", "Credential-stuffing attack", "banking-api",
            "A credential-stuffing attack is trying leaked passwords from many IP addresses",
            List.of("credential", "stuffing", "attack", "login"), Set.of(ENABLE_STEP_UP_AUTH, BLOCK_IP_RANGE), false),
    FIN_12("FIN-12", "Partner API token scraping account data", "banking-api",
            "Partner API token tok_8f2c is compromised and is scraping account data",
            List.of("token", "partner", "compromised", "scrap"), Set.of(REVOKE_API_TOKEN), false),
    FIN_13("FIN-13", "False alarm during batch window", "account-service",
            "No real problem: the nightly interest batch briefly slows account-service and the alert is too sensitive",
            List.of("false", "batch", "no customer impact", "noise"), Set.of(), true);

    private final String code;
    private final String title;
    private final String primaryService;
    private final String rootCause;
    private final List<String> rootCauseKeywords;
    private final Set<FixAction> fixes;
    private final boolean falseAlarm;

    Scenario(String code, String title, String primaryService, String rootCause,
             List<String> rootCauseKeywords, Set<FixAction> fixes, boolean falseAlarm) {
        this.code = code;
        this.title = title;
        this.primaryService = primaryService;
        this.rootCause = rootCause;
        this.rootCauseKeywords = rootCauseKeywords;
        this.fixes = fixes;
        this.falseAlarm = falseAlarm;
    }

    public static Scenario byCode(String code) {
        for (Scenario s : values()) {
            if (s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown scenario " + code);
    }

    public String code() { return code; }
    public String title() { return title; }
    public String primaryService() { return primaryService; }
    public String rootCause() { return rootCause; }
    public List<String> rootCauseKeywords() { return rootCauseKeywords; }
    public Set<FixAction> fixes() { return fixes; }
    public boolean falseAlarm() { return falseAlarm; }
}
