package com.democorp.bank.evaluation;

import com.democorp.bank.DemocorpProperties;
import com.democorp.bank.scenario.FixAction;
import com.democorp.bank.scenario.Scenario;
import com.democorp.bank.scenario.ScenarioService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

/**
 * Evaluation report (deliverable): runs practice incidents one by one and compares the War Room's answer
 * with the known root cause. MTTR with agents = time to diagnosis + assumed approval + assumed fix time;
 * the manual baseline is the time-to-resolve of the most similar past incident in the knowledge base.
 */
@Service
public class EvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);
    private static final double DEFAULT_MANUAL_MINUTES = 45;

    /** Ground truth: the cause category the RCA agent should name for each scenario. */
    static final Map<Scenario, String> EXPECTED = new EnumMap<>(Map.ofEntries(
            Map.entry(Scenario.FIN_01, "BAD_RELEASE"), Map.entry(Scenario.FIN_02, "CARD_NETWORK_OUTAGE"),
            Map.entry(Scenario.FIN_03, "EXPIRED_CERTIFICATE"), Map.entry(Scenario.FIN_04, "PROCESSOR_RATE_LIMIT"),
            Map.entry(Scenario.FIN_05, "DUPLICATE_DEBITS"), Map.entry(Scenario.FIN_06, "FRAUD_RULE_CHANGE"),
            Map.entry(Scenario.FIN_07, "LEDGER_POISON_MESSAGE"), Map.entry(Scenario.FIN_08, "CACHE_FAILURE"),
            Map.entry(Scenario.FIN_11, "CREDENTIAL_STUFFING"), Map.entry(Scenario.FIN_12, "API_TOKEN_ABUSE"),
            Map.entry(Scenario.FIN_13, "FALSE_ALARM")));

    private final ScenarioService scenarios;
    private final DemocorpProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile Run current;

    public EvaluationRunner(ScenarioService scenarios, DemocorpProperties props, ObjectMapper mapper,
                            RestClient.Builder builder) {
        this.scenarios = scenarios;
        this.props = props;
        this.mapper = mapper;
        this.http = builder.build();
    }

    public synchronized Run start(List<Scenario> selected) {
        if (current != null && "RUNNING".equals(current.status)) {
            throw new IllegalStateException("An evaluation is already running");
        }
        Run run = new Run(Instant.now(), selected.isEmpty() ? List.copyOf(EXPECTED.keySet()) : selected);
        current = run;
        executor.submit(() -> execute(run));
        return run;
    }

    public Run current() {
        return current;
    }

    private void execute(Run run) {
        try {
            for (Scenario s : run.scenarios) {
                run.active = s.code();
                cleanUp();
                pause(props.evalSettleSeconds());
                run.results.add(evaluate(s));
                scenarios.stop();
                cleanUp();
            }
            run.status = "COMPLETED";
        } catch (RuntimeException e) {
            log.error("Evaluation failed", e);
            run.status = "FAILED: " + e.getMessage();
        } finally {
            run.active = null;
            run.finishedAt = Instant.now();
            scenarios.stop();
        }
    }

    private Result evaluate(Scenario s) {
        String expected = EXPECTED.get(s);
        Instant t0 = scenarios.start(s);
        log.info("Evaluating {} (expect {})", s.code(), expected);
        Instant deadline = t0.plusSeconds(props.evalTimeoutSeconds());
        JsonNode incident = null;
        while (Instant.now().isBefore(deadline)) {
            incident = firstWithRca(t0);
            if (incident != null) {
                break;
            }
            pause(5);
        }
        double manual = manualBaseline(s);
        if (incident == null) {
            return new Result(s.code(), s.title(), expected, null, false, false, null, null, null, null, null, false,
                    manual, null, "No incident with RCA within " + props.evalTimeoutSeconds() + " s");
        }
        JsonNode rca = read(incident.path("rca").asText("{}"));
        JsonNode plan = read(incident.path("plan").asText("{}"));
        JsonNode hyps = rca.path("hypotheses");
        String top = hyps.path(0).path("category").asText(null);
        boolean top3 = false;
        for (int i = 0; i < Math.min(3, hyps.size()); i++) {
            top3 |= expected.equals(hyps.path(i).path("category").asText());
        }
        double confidence = hyps.path(0).path("confidence").asDouble(0);
        Instant opened = java.time.OffsetDateTime.parse(incident.path("opened_at").asText().replace(" ", "T")).toInstant();
        Instant rcaAt = java.time.OffsetDateTime.parse(incident.path("rca_at").asText().replace(" ", "T")).toInstant();
        double detect = seconds(t0, opened);
        double diagnose = seconds(t0, rcaAt);
        String action = plan.path("corrective").path(0).path("action").asText(null);
        boolean correctFix = s.falseAlarm() ? plan.path("closeAsFalsePositive").asBoolean(false)
                : action != null && s.fixes().contains(FixAction.valueOf(action));
        double agentMttr = diagnose / 60.0 + props.evalApprovalMinutes() + (s.falseAlarm() ? 0 : props.evalFixMinutes());
        return new Result(s.code(), s.title(), expected, top, expected.equals(top), top3, round(confidence),
                round(detect), round(diagnose), rca.path("method").asText(null), action, correctFix, manual,
                round(agentMttr), incident.path("id").asText());
    }

    private JsonNode firstWithRca(Instant since) {
        JsonNode list = http.get().uri(props.incidentUrl() + "/internal/evaluation/incidents?since={s}", since.toString())
                .retrieve().body(JsonNode.class);
        if (list == null) {
            return null;
        }
        for (JsonNode i : list) {
            if (!i.path("rca_at").isNull() && !i.path("rca_at").isMissingNode() && !i.path("plan").isNull()) {
                return i;
            }
        }
        return null;
    }

    /** Time-to-resolve of the most similar past incident with the same kind of fix (seeded history). */
    private double manualBaseline(Scenario s) {
        try {
            JsonNode r = http.post().uri(props.knowledgeUrl() + "/internal/search")
                    .body(Map.of("query", s.title() + ". " + s.rootCause(), "k", 5)).retrieve().body(JsonNode.class);
            if (r != null) {
                for (JsonNode h : r.path("hits")) {
                    if ("PAST_INCIDENT".equals(h.path("kind").asText()) && h.path("mttrMinutes").isNumber()) {
                        return h.path("mttrMinutes").asDouble();
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Knowledge baseline unavailable for {}: {}", s.code(), e.getMessage());
        }
        return DEFAULT_MANUAL_MINUTES;
    }

    private void cleanUp() {
        try {
            JsonNode open = http.get().uri(props.incidentUrl() + "/internal/evaluation/open").retrieve().body(JsonNode.class);
            if (open != null) {
                for (JsonNode id : open) {
                    http.post().uri(props.incidentUrl() + "/internal/evaluation/incidents/{id}/close", id.asText())
                            .retrieve().toBodilessEntity();
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not close open incidents: {}", e.getMessage());
        }
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static void pause(int seconds) {
        LockSupport.parkNanos(Duration.ofSeconds(seconds).toNanos());
    }

    private static double seconds(Instant a, Instant b) {
        return Duration.between(a, b).toMillis() / 1000.0;
    }

    private static Double round(Double v) {
        return v == null ? null : Math.round(v * 100) / 100.0;
    }

    public static final class Run {
        public final Instant startedAt;
        public final List<Scenario> scenarios;
        public final List<Result> results = new java.util.concurrent.CopyOnWriteArrayList<>();
        public volatile String status = "RUNNING";
        public volatile String active;
        public volatile Instant finishedAt;

        Run(Instant startedAt, List<Scenario> scenarios) {
            this.startedAt = startedAt;
            this.scenarios = scenarios;
        }
    }

    /**
     * One scenario's outcome. Times are seconds from fault start; MTTR values are minutes.
     */
    public record Result(String code, String title, String expected, String topCause, boolean top1, boolean top3,
                         Double confidence, Double detectSeconds, Double diagnoseSeconds, String method,
                         String proposedAction, boolean correctFix, double manualMttrMinutes, Double agentMttrMinutes,
                         String incidentId) {
    }

    public static List<Scenario> parse(List<String> codes) {
        List<Scenario> out = new ArrayList<>();
        if (codes != null) {
            codes.forEach(c -> out.add(Scenario.byCode(c)));
        }
        return out;
    }
}
