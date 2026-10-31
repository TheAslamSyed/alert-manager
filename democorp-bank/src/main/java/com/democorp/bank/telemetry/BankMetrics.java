package com.democorp.bank.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DemoCorp business metrics. Every meter has a {@code service} tag so Prometheus alerts
 * and the War Room agents can tell banking-api, payment-service and account-service apart.
 */
@Component
public class BankMetrics {

    public static final int DB_POOL_MAX = 100;

    private final MeterRegistry registry;
    private final Map<String, AtomicInteger> poolActive = new ConcurrentHashMap<>();
    private final AtomicInteger ledgerDepth = new AtomicInteger();
    private final AtomicInteger cacheUp = new AtomicInteger(1);

    public BankMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (String service : List.of("payment-service", "account-service")) {
            AtomicInteger active = new AtomicInteger(10);
            poolActive.put(service, active);
            Gauge.builder("democorp.db.pool.active", active, AtomicInteger::get)
                    .tag("service", service).description("Active DB connections").register(registry);
            Gauge.builder("democorp.db.pool.max", () -> DB_POOL_MAX)
                    .tag("service", service).description("Max DB connections").register(registry);
        }
        Gauge.builder("democorp.ledger.queue.depth", ledgerDepth, AtomicInteger::get)
                .tag("service", "account-service").description("Ledger messages waiting").register(registry);
        Gauge.builder("democorp.cache.up", cacheUp, AtomicInteger::get)
                .tag("service", "account-service").description("1 if the account cache is reachable").register(registry);

        // Pre-register counters so rate()/increase() work from the first real event.
        for (String outcome : List.of("approved", "declined_fraud", "error_timeout", "error_tls", "throttled")) {
            cardCounter("primary", outcome);
        }
        loginCounter("success");
        loginCounter("failure");
        Counter.builder("democorp.duplicate.debits").tag("service", "payment-service").register(registry);
    }

    public void request(String service, String operation, boolean success, long latencyMillis) {
        Counter.builder("democorp.requests")
                .tag("service", service).tag("operation", operation).tag("outcome", success ? "success" : "error")
                .register(registry).increment();
        Timer.builder("democorp.request")
                .tag("service", service).tag("operation", operation)
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(300), Duration.ofSeconds(1))
                .register(registry).record(Duration.ofMillis(latencyMillis));
    }

    public void cardAuth(String route, String outcome) {
        cardCounter(route, outcome).increment();
    }

    public void login(boolean success) {
        loginCounter(success ? "success" : "failure").increment();
    }

    public void duplicateDebit() {
        Counter.builder("democorp.duplicate.debits").tag("service", "payment-service").register(registry).increment();
    }

    public void partnerRecords(String token, int records) {
        Counter.builder("democorp.partner.api.records")
                .tag("service", "banking-api").tag("token", token)
                .register(registry).increment(records);
    }

    public void setPoolActive(String service, int active) {
        poolActive.get(service).set(Math.max(0, Math.min(DB_POOL_MAX, active)));
    }

    public int poolActive(String service) {
        return poolActive.get(service).get();
    }

    public void setLedgerDepth(int depth) {
        ledgerDepth.set(depth);
    }

    public void setCacheUp(boolean up) {
        cacheUp.set(up ? 1 : 0);
    }

    private Counter cardCounter(String route, String outcome) {
        return Counter.builder("democorp.card.auth")
                .tag("service", "payment-service").tag("route", route).tag("outcome", outcome)
                .register(registry);
    }

    private Counter loginCounter(String outcome) {
        return Counter.builder("democorp.login.attempts")
                .tag("service", "banking-api").tag("outcome", outcome)
                .register(registry);
    }
}
