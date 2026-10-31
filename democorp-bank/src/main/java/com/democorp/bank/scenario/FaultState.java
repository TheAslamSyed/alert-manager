package com.democorp.bank.scenario;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The bank's current "health": which fault is active, deployed versions and live config. */
@Component
public class FaultState {

    public static final String PAYMENT = "payment-service";
    public static final String BANKING = "banking-api";
    public static final String ACCOUNT = "account-service";

    public static final String CFG_IDEMPOTENCY = "payment.retry.idempotency-key.enabled";
    public static final String CFG_MAX_ATTEMPTS = "payment.retry.max-attempts";
    public static final String CFG_FRAUD_THRESHOLD = "fraud.score-threshold";

    private static final Map<String, String> BASE_VERSIONS = Map.of(
            BANKING, "4.1.0", PAYMENT, "2.2.0", ACCOUNT, "3.0.2");
    private static final Map<String, String> BASE_CONFIG = Map.of(
            CFG_IDEMPOTENCY, "true", CFG_MAX_ATTEMPTS, "1", CFG_FRAUD_THRESHOLD, "0.85");

    private volatile Scenario active;
    private volatile Instant startedAt;
    private volatile String cardRoute = "primary";
    private final Map<String, String> versions = new ConcurrentHashMap<>(BASE_VERSIONS);
    private final Map<String, String> config = new ConcurrentHashMap<>(BASE_CONFIG);
    private final AtomicInteger ledgerDepth = new AtomicInteger();

    public synchronized void start(Scenario scenario) {
        reset();
        active = scenario;
        startedAt = Instant.now();
        switch (scenario) {
            case FIN_01 -> versions.put(PAYMENT, "2.3.0");
            case FIN_05 -> {
                config.put(CFG_IDEMPOTENCY, "false");
                config.put(CFG_MAX_ATTEMPTS, "3");
            }
            case FIN_06 -> config.put(CFG_FRAUD_THRESHOLD, "0.35");
            default -> { /* fault is purely behavioural */ }
        }
    }

    /** Clears the fault. Versions/config go back to their healthy values. */
    public synchronized void resolve() {
        reset();
    }

    private void reset() {
        active = null;
        startedAt = null;
        cardRoute = "primary";
        versions.clear();
        versions.putAll(BASE_VERSIONS);
        config.clear();
        config.putAll(BASE_CONFIG);
    }

    public Optional<Scenario> active() {
        return Optional.ofNullable(active);
    }

    public boolean is(Scenario scenario) {
        return active == scenario;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public String version(String service) {
        return versions.get(service);
    }

    public void setCardRoute(String route) {
        this.cardRoute = route;
    }

    public String cardRoute() {
        return cardRoute;
    }

    public AtomicInteger ledgerDepth() {
        return ledgerDepth;
    }

    public Map<String, Object> describe() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeScenario", active == null ? null : active.code());
        m.put("startedAt", startedAt);
        m.put("versions", Map.copyOf(versions));
        m.put("config", Map.copyOf(config));
        m.put("cardRoute", cardRoute);
        m.put("ledgerQueueDepth", ledgerDepth.get());
        return m;
    }
}
