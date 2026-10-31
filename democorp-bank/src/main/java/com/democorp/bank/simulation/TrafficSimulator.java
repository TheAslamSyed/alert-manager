package com.democorp.bank.simulation;

import com.democorp.bank.DemocorpProperties;
import com.democorp.bank.scenario.FaultState;
import com.democorp.bank.scenario.Scenario;
import com.democorp.bank.telemetry.BankLog;
import com.democorp.bank.telemetry.BankMetrics;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static com.democorp.bank.scenario.FaultState.ACCOUNT;
import static com.democorp.bank.scenario.FaultState.BANKING;
import static com.democorp.bank.scenario.FaultState.PAYMENT;

/**
 * Simulated customer traffic. Four times a second it runs transfers, balance checks, logins
 * and partner API calls, and records metrics + logs. The active fault changes what happens.
 * Latency is recorded, not slept, so the simulation stays cheap.
 */
@Component
public class TrafficSimulator {

    private static final String NORMAL_PARTNER_TOKEN = "tok_3a91";
    private static final String ABUSED_PARTNER_TOKEN = "tok_8f2c";

    private final FaultState state;
    private final BankMetrics metrics;
    private final DemocorpProperties props;
    private final AtomicLong tick = new AtomicLong();

    public TrafficSimulator(FaultState state, BankMetrics metrics, DemocorpProperties props) {
        this.state = state;
        this.metrics = metrics;
        this.props = props;
    }

    @Scheduled(fixedRate = 250)
    public void tick() {
        if (!props.trafficEnabled()) {
            return;
        }
        long n = tick.incrementAndGet();
        Scenario s = state.active().orElse(null);

        int transfers = 3 + (s == Scenario.FIN_04 ? 5 : 0);
        for (int i = 0; i < transfers; i++) {
            transfer(s, n);
        }
        for (int i = 0; i < 3; i++) {
            balance(s, n);
        }
        login(false, n);
        if (s == Scenario.FIN_11) {
            for (int i = 0; i < 6; i++) {
                login(true, n);
            }
        }
        partnerCall(NORMAL_PARTNER_TOKEN, 5, false);
        if (s == Scenario.FIN_12) {
            partnerCall(ABUSED_PARTNER_TOKEN, 500, true);
        }
        ledger(s, n);
        pools(s);
        metrics.setCacheUp(s != Scenario.FIN_08);
    }

    // ---------------------------------------------------------------- transfers

    private void transfer(Scenario s, long n) {
        Result payment = payment(s, n);
        long bankingLatency = payment.latencyMs + rnd(10, 30);
        metrics.request(BANKING, "transfer", payment.ok, bankingLatency);
        if (!payment.ok && chance(0.2)) {
            BankLog.error(BANKING, "Transfer failed: upstream payment-service error", "status", 502);
        } else if (payment.ok && n % 40 == 0) {
            BankLog.info(BANKING, "Transfer accepted", "latency_ms", bankingLatency);
        }
    }

    private Result payment(Scenario s, long n) {
        String version = state.version(PAYMENT);

        if (s == Scenario.FIN_01 && chance(0.38)) {
            BankLog.error(PAYMENT, "HikariPool-1 - Connection is not available, request timed out after 30000ms",
                    "version", version, "pool", "exhausted");
            metrics.request(PAYMENT, "transfer", false, 30000);
            return new Result(false, 30000);
        }

        String route = state.cardRoute();
        String outcome = cardOutcome(s);
        metrics.cardAuth(route, outcome);
        long latency = rnd(40, 140);
        boolean ok = true;
        switch (outcome) {
            case "error_timeout" -> {
                latency = 5000;
                ok = false;
                if (s == Scenario.FIN_02) {
                    BankLog.error(PAYMENT, "Card network authorization timed out after 5000ms",
                            "route", route, "peer", "card-network." + route);
                }
            }
            case "error_tls" -> {
                latency = rnd(20, 40);
                ok = false;
                BankLog.error(PAYMENT, "PKIX path validation failed: certificate expired",
                        "peer", "card-network.primary", "cert_not_after", "2026-10-31T00:00:00Z");
            }
            case "throttled" -> {
                latency = rnd(15, 30);
                ok = false;
                BankLog.warn(PAYMENT, "Card processor returned HTTP 429 Too Many Requests",
                        "quota", "2000/min", "route", route);
            }
            case "declined_fraud" -> {
                if (s == Scenario.FIN_06) {
                    double score = 0.36 + ThreadLocalRandom.current().nextDouble(0.3);
                    BankLog.warn(PAYMENT, "Transaction declined by fraud rule FR-12",
                            "score", String.format("%.2f", score), "threshold", "0.35");
                }
            }
            default -> { /* approved */ }
        }

        if (s == Scenario.FIN_05 && ok && chance(0.05)) {
            metrics.duplicateDebit();
            BankLog.warn(PAYMENT, "Duplicate debit detected for retried payment",
                    "txn", "txn-" + rnd(100000, 999999), "attempt", 2, "idempotency_key", "missing");
        }
        if (ok && n % 40 == 0) {
            BankLog.info(PAYMENT, "Payment authorized", "version", version, "route", route, "latency_ms", latency);
        }
        metrics.request(PAYMENT, "transfer", ok, latency);
        return new Result(ok, latency);
    }

    private String cardOutcome(Scenario s) {
        if (s == Scenario.FIN_02 && chance(0.6)) {
            return "error_timeout";
        }
        if (s == Scenario.FIN_03) {
            return "error_tls";
        }
        if (s == Scenario.FIN_04 && chance(0.3)) {
            return "throttled";
        }
        if (s == Scenario.FIN_06 && chance(0.35)) {
            return "declined_fraud";
        }
        if (chance(0.02)) {
            return "declined_fraud";
        }
        if (chance(0.003)) {
            return "error_timeout";
        }
        return "approved";
    }

    // ---------------------------------------------------------------- balances

    private void balance(Scenario s, long n) {
        long latency = rnd(20, 60);
        boolean ok = true;
        if (s == Scenario.FIN_08) {
            latency = rnd(1500, 3000);
            ok = !chance(0.05);
            if (n % 10 == 0) {
                BankLog.warn(ACCOUNT, "Redis connection refused, falling back to database", "cache", "account-cache");
            }
            if (!ok) {
                BankLog.error(ACCOUNT, "Balance query timed out", "db_pool_active", metrics.poolActive(ACCOUNT));
            }
        } else if (s == Scenario.FIN_13) {
            latency = rnd(1200, 1600);
        }
        metrics.request(ACCOUNT, "balance", ok, latency);
        metrics.request(BANKING, "balance", ok, latency + rnd(5, 20));
    }

    // ---------------------------------------------------------------- logins

    private void login(boolean attack, long n) {
        if (attack) {
            metrics.login(false);
            metrics.request(BANKING, "login", true, rnd(80, 160));
            if (chance(0.5)) {
                BankLog.warn(BANKING, "Login failed", "user", "cust-" + rnd(1000, 9999),
                        "ip", "185.220." + rnd(100, 120) + "." + rnd(1, 254), "reason", "bad_password");
            }
            return;
        }
        boolean ok = !chance(0.03);
        metrics.login(ok);
        metrics.request(BANKING, "login", true, rnd(60, 140));
        if (n % 40 == 0) {
            BankLog.info(BANKING, "Login", "outcome", ok ? "success" : "failure");
        }
    }

    // ---------------------------------------------------------------- partner API

    private void partnerCall(String token, int records, boolean suspicious) {
        metrics.partnerRecords(token, records);
        metrics.request(BANKING, "partner_api", true, rnd(30, 90));
        if (suspicious) {
            BankLog.info(BANKING, "Partner API request", "token", token, "endpoint", "/open-banking/accounts",
                    "records", records, "client_ip", "203.0.113." + rnd(2, 250));
        }
    }

    // ---------------------------------------------------------------- ledger, pools

    private void ledger(Scenario s, long n) {
        int depth;
        if (s == Scenario.FIN_07) {
            depth = state.ledgerDepth().addAndGet(3);
            if (n % 4 == 0) {
                BankLog.error(ACCOUNT, "Failed to process ledger message, retrying", "messageId", "led-88412",
                        "error", "NumberFormatException: For input string: '12,50'", "attempt", n % 100);
            }
        } else {
            depth = state.ledgerDepth().updateAndGet(d -> Math.max(0, d - 10));
        }
        metrics.setLedgerDepth(depth);
    }

    private void pools(Scenario s) {
        if (s == Scenario.FIN_01) {
            metrics.setPoolActive(PAYMENT, metrics.poolActive(PAYMENT) + 8);
        } else {
            metrics.setPoolActive(PAYMENT, (int) rnd(12, 25));
        }
        metrics.setPoolActive(ACCOUNT, s == Scenario.FIN_08 ? (int) rnd(92, 100) : (int) rnd(10, 22));
    }

    private static boolean chance(double p) {
        return ThreadLocalRandom.current().nextDouble() < p;
    }

    private static long rnd(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private record Result(boolean ok, long latencyMs) {
    }
}
