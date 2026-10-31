package com.democorp.warroom.agent.agents;

import com.democorp.warroom.agent.core.EvidenceAgent;
import com.democorp.warroom.agent.core.InvestigationContext;
import com.democorp.warroom.agent.tools.PrometheusTool;
import com.democorp.warroom.agent.tools.PrometheusTool.Sample;
import com.democorp.warroom.common.contracts.Investigation.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Monitoring agent: reads Prometheus and reports what is abnormal, with the exact PromQL used. */
@Component
public class MonitoringAgent implements EvidenceAgent {

    static final String ERROR_RATIO = "sum by (service) (rate(democorp_requests_total{outcome=\"error\"}[2m])) "
            + "/ sum by (service) (rate(democorp_requests_total[2m]))";
    static final String P95 = "histogram_quantile(0.95, sum by (le, service) (rate(democorp_request_seconds_bucket[2m])))";
    static final String POOL = "max by (service) (democorp_db_pool_active / democorp_db_pool_max)";
    static final String CARD = "sum by (outcome) (rate(democorp_card_auth_total[2m]))";
    static final String LEDGER = "max(democorp_ledger_queue_depth)";
    static final String CACHE = "min(democorp_cache_up)";
    static final String DUPLICATES = "sum(increase(democorp_duplicate_debits_total[5m]))";

    private final PrometheusTool prometheus;

    public MonitoringAgent(PrometheusTool prometheus) {
        this.prometheus = prometheus;
    }

    @Override
    public String name() {
        return "Monitoring agent";
    }

    @Override
    public Output investigate(InvestigationContext ctx) {
        List<Evidence> out = new ArrayList<>();

        for (Sample s : prometheus.query(ERROR_RATIO)) {
            if (s.valid() && s.value() > 0.05) {
                out.add(ctx.evidence(name(), "PROMETHEUS", "Error rate " + pct(s.value()) + " on " + s.label("service"),
                        "Share of failing requests over the last 2 minutes.", ERROR_RATIO,
                        List.of("error_rate_high:" + s.label("service"))));
            }
        }
        for (Sample s : prometheus.query(P95)) {
            if (s.valid() && s.value() > 1.0) {
                out.add(ctx.evidence(name(), "PROMETHEUS", "p95 latency " + String.format("%.1f s", s.value()) + " on "
                        + s.label("service"), "95% of requests finish faster than this.", P95,
                        List.of("latency_high:" + s.label("service"))));
            }
        }
        for (Sample s : prometheus.query(POOL)) {
            if (s.valid() && s.value() > 0.9) {
                out.add(ctx.evidence(name(), "PROMETHEUS", "DB connection pool " + pct(s.value()) + " full on "
                        + s.label("service"), "Active / maximum database connections.", POOL,
                        List.of("db_pool_saturated:" + s.label("service"))));
            }
        }

        Map<String, Double> card = new HashMap<>();
        double total = 0;
        for (Sample s : prometheus.query(CARD)) {
            if (s.valid()) {
                card.put(s.label("outcome"), s.value());
                total += s.value();
            }
        }
        if (total > 0) {
            cardEvidence(ctx, out, card, total, "error_timeout", 0.2, "card_error_timeout", "Card authorizations timing out");
            cardEvidence(ctx, out, card, total, "error_tls", 0.2, "card_error_tls", "Card authorizations failing TLS");
            cardEvidence(ctx, out, card, total, "throttled", 0.1, "card_throttled", "Card authorizations throttled (429)");
            cardEvidence(ctx, out, card, total, "declined_fraud", 0.15, "fraud_declines_high", "Card payments declined as fraud");
        }

        single(prometheus.query(LEDGER)).filter(v -> v > 50).ifPresent(v -> out.add(ctx.evidence(name(), "PROMETHEUS",
                "Ledger queue has " + Math.round(v) + " messages waiting", "Postings waiting to update balances.",
                LEDGER, List.of("ledger_backlog:account-service"))));
        single(prometheus.query(CACHE)).filter(v -> v == 0).ifPresent(v -> out.add(ctx.evidence(name(), "PROMETHEUS",
                "Account cache is down", "democorp_cache_up = 0", CACHE, List.of("cache_down:account-service"))));
        single(prometheus.query(DUPLICATES)).filter(v -> v > 0).ifPresent(v -> out.add(ctx.evidence(name(), "PROMETHEUS",
                Math.round(v) + " duplicate debits in 5 minutes", "Same payment debited more than once.", DUPLICATES,
                List.of("duplicate_debits:payment-service"))));

        if (out.isEmpty()) {
            out.add(ctx.evidence(name(), "PROMETHEUS", "No metric anomalies right now",
                    "Error rate, latency, DB pool, card outcomes, ledger, cache and duplicates are all normal.",
                    ERROR_RATIO, List.of("metrics_normal")));
            return new Output("No metric anomalies.", out);
        }
        return new Output(out.size() + " metric anomalies: " + out.get(0).title(), out);
    }

    private void cardEvidence(InvestigationContext ctx, List<Evidence> out, Map<String, Double> card, double total,
                              String outcome, double threshold, String tag, String title) {
        double share = card.getOrDefault(outcome, 0.0) / total;
        if (share > threshold) {
            out.add(ctx.evidence(name(), "PROMETHEUS", title + ": " + pct(share),
                    "Share of card authorizations with outcome " + outcome + ".", CARD,
                    List.of(tag + ":payment-service")));
        }
    }

    private static java.util.Optional<Double> single(List<Sample> samples) {
        return samples.stream().filter(Sample::valid).map(Sample::value).findFirst();
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
