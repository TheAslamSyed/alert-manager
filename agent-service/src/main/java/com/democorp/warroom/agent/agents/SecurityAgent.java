package com.democorp.warroom.agent.agents;

import com.democorp.warroom.agent.core.EvidenceAgent;
import com.democorp.warroom.agent.core.InvestigationContext;
import com.democorp.warroom.agent.tools.LokiTool;
import com.democorp.warroom.agent.tools.LokiTool.LogLine;
import com.democorp.warroom.agent.tools.PrometheusTool;
import com.democorp.warroom.agent.tools.PrometheusTool.Sample;
import com.democorp.warroom.common.contracts.Investigation.Evidence;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Security agent: looks for login attacks and API-token abuse. Saying "ruled out" is a useful answer too. */
@Component
public class SecurityAgent implements EvidenceAgent {

    static final String LOGIN_FAILURES = "sum(rate(democorp_login_attempts_total{outcome=\"failure\"}[2m]))";
    static final String PARTNER = "sum by (token) (rate(democorp_partner_api_records_total[2m]))";
    static final String FAILED_LOGINS_LOG = "{app=\"democorp-bank\"} |= \"Login failed\"";
    static final String PARTNER_LOG = "{app=\"democorp-bank\"} |= \"Partner API request\"";

    private final PrometheusTool prometheus;
    private final LokiTool loki;

    public SecurityAgent(PrometheusTool prometheus, LokiTool loki) {
        this.prometheus = prometheus;
        this.loki = loki;
    }

    @Override
    public String name() {
        return "Security agent";
    }

    @Override
    public Output investigate(InvestigationContext ctx) {
        List<Evidence> out = new ArrayList<>();
        Instant now = Instant.now();

        double failures = prometheus.query(LOGIN_FAILURES).stream().filter(Sample::valid)
                .mapToDouble(Sample::value).findFirst().orElse(0);
        if (failures > 3) {
            List<LogLine> lines = loki.query(FAILED_LOGINS_LOG, ctx.from(), now, 2000);
            Set<String> ips = lines.stream().map(l -> l.field("ip")).filter(ip -> !ip.isBlank()).collect(Collectors.toSet());
            boolean stuffing = ips.size() >= 20;
            out.add(ctx.evidence(name(), "SECURITY",
                    String.format("%.1f failed logins/s from %d IP addresses", failures, ips.size()),
                    stuffing ? "Many IPs trying many accounts: credential-stuffing pattern."
                            : "Failed logins are high but come from few IPs.",
                    LOGIN_FAILURES + "  +  " + FAILED_LOGINS_LOG,
                    stuffing ? List.of("credential_stuffing:banking-api") : List.of("login_failures_high:banking-api")));
        }

        for (Sample s : prometheus.query(PARTNER)) {
            if (s.valid() && s.value() > 50) {
                String token = s.label("token");
                List<LogLine> lines = loki.query(PARTNER_LOG + " |= \"" + token + "\"", ctx.from(), now, 500);
                long ips = lines.stream().map(l -> l.field("client_ip")).filter(ip -> !ip.isBlank()).distinct().count();
                out.add(ctx.evidence(name(), "SECURITY",
                        String.format("Partner token %s reading %.0f records/s from %d IPs", token, s.value(), ips),
                        "Normal partner volume is about 20 records/s from one IP.", PARTNER,
                        List.of("token_abuse:" + token)));
            }
        }

        if (out.isEmpty()) {
            out.add(ctx.evidence(name(), "SECURITY", "No attack signals: security cause ruled out",
                    String.format("Failed logins %.1f/s (normal), no partner token above normal volume.", failures),
                    LOGIN_FAILURES + "  +  " + PARTNER, List.of("no_attack_signals")));
            return new Output("No attack signals. Security cause ruled out.", out);
        }
        return new Output(out.get(0).title(), out);
    }
}
