package com.democorp.warroom.agent.agents;

import com.democorp.warroom.agent.core.EvidenceAgent;
import com.democorp.warroom.agent.core.InvestigationContext;
import com.democorp.warroom.agent.tools.LokiTool;
import com.democorp.warroom.agent.tools.LokiTool.LogLine;
import com.democorp.warroom.common.contracts.Investigation.Evidence;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Log agent: groups warning/error lines in Loki into patterns, with counts and first-seen time. */
@Component
public class LogAgent implements EvidenceAgent {

    static final String ERRORS = "{app=\"democorp-bank\", level=~\"ERROR|WARN\"}";
    static final String BATCH = "{app=\"democorp-bank\"} |= \"batch\"";
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    /** Keyword → evidence tag. Lower-case match on the log message. */
    private static final Map<String, String> PATTERN_TAGS = new LinkedHashMap<>();

    static {
        PATTERN_TAGS.put("connection is not available", "log_pool_exhausted");
        PATTERN_TAGS.put("pkix", "log_cert_expired");
        PATTERN_TAGS.put("card network authorization timed out", "log_card_timeout");
        PATTERN_TAGS.put("429", "log_rate_limited");
        PATTERN_TAGS.put("duplicate debit", "log_duplicate_debit");
        PATTERN_TAGS.put("fraud rule", "log_fraud_declines");
        PATTERN_TAGS.put("ledger message", "log_ledger_poison");
        PATTERN_TAGS.put("redis connection refused", "log_cache_down");
        PATTERN_TAGS.put("login failed", "log_login_failures");
        PATTERN_TAGS.put("upstream payment-service", "log_upstream_errors");
    }

    private final LokiTool loki;

    public LogAgent(LokiTool loki) {
        this.loki = loki;
    }

    @Override
    public String name() {
        return "Log agent";
    }

    @Override
    public Output investigate(InvestigationContext ctx) {
        Instant now = Instant.now();
        List<LogLine> lines = loki.query(ERRORS, ctx.from(), now, 2000);
        Map<String, Group> groups = new LinkedHashMap<>();
        for (LogLine l : lines) {
            String service = l.field("service");
            String msg = l.field("msg");
            if (msg.isBlank()) {
                continue;
            }
            groups.computeIfAbsent(service + "|" + msg, k -> new Group(service, msg, l)).add(l);
        }

        List<Evidence> out = new ArrayList<>();
        groups.values().stream()
                .sorted((a, b) -> Integer.compare(b.count, a.count))
                .limit(4)
                .forEach(g -> out.add(ctx.evidence(name(), "LOKI",
                        "\"" + g.msg + "\" ×" + g.count + " on " + g.service,
                        "First seen " + HHMMSS.format(g.first.at()) + " UTC. Sample: " + truncate(g.first.raw(), 260),
                        ERRORS + " | logfmt | service=\"" + g.service + "\"", tags(g))));

        List<LogLine> batch = loki.query(BATCH, ctx.from(), now, 50);
        if (!batch.isEmpty()) {
            LogLine b = batch.get(0);
            out.add(ctx.evidence(name(), "LOKI", "Scheduled batch job running: " + b.field("msg"),
                    "Seen at " + HHMMSS.format(b.at()) + " UTC on " + b.field("service") + ".", BATCH,
                    List.of("log_batch_job:" + b.field("service"))));
        }
        if (out.isEmpty()) {
            out.add(ctx.evidence(name(), "LOKI", "No warning or error logs in the window",
                    "Searched since " + HHMMSS.format(ctx.from()) + " UTC.", ERRORS, List.of("logs_clean")));
            return new Output("No warning or error logs.", out);
        }
        return new Output(lines.size() + " warning/error lines in " + groups.size() + " patterns; top: "
                + out.get(0).title(), out);
    }

    private static List<String> tags(Group g) {
        String m = g.msg.toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        PATTERN_TAGS.forEach((needle, tag) -> {
            if (m.contains(needle)) {
                tags.add(tag + ":" + g.service);
            }
        });
        if (!g.first.field("version").isBlank()) {
            tags.add("log_version:" + g.service + ":" + g.first.field("version"));
        }
        if (tags.isEmpty()) {
            tags.add("log_errors:" + g.service);
        }
        return tags;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static final class Group {
        final String service;
        final String msg;
        final LogLine first;
        int count;

        Group(String service, String msg, LogLine first) {
            this.service = service;
            this.msg = msg;
            this.first = first;
        }

        void add(LogLine l) {
            count++;
        }
    }
}
