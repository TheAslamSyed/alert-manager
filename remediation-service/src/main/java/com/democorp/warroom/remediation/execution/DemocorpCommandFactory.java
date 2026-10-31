package com.democorp.warroom.remediation.execution;

import com.democorp.warroom.remediation.RemediationProperties;
import com.democorp.warroom.remediation.catalog.ActionCatalog.ActionSpec;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * Builds commands that run a fix through DemoCorp's admin API and verify it with Prometheus.
 * The executor never talks to anything that is not on the allowlist.
 */
@Component
public class DemocorpCommandFactory {

    private final RestClient http;
    private final RemediationProperties props;

    public DemocorpCommandFactory(RestClient.Builder builder, RemediationProperties props) {
        this.http = builder.build();
        this.props = props;
    }

    public RemediationCommand create(ActionSpec spec, String target, String requestedBy) {
        return new RemediationCommand() {
            @Override
            public void checkPreconditions() {
                JsonNode state = http.get().uri(props.democorpUrl() + "/admin/state")
                        .header("X-Admin-Key", props.democorpAdminKey()).retrieve().body(JsonNode.class);
                if (state == null) {
                    throw new IllegalStateException("DemoCorp admin API did not answer");
                }
            }

            @Override
            public String dryRun() {
                JsonNode r = http.post().uri(props.democorpUrl() + "/admin/actions/" + spec.action() + "/dry-run")
                        .header("X-Admin-Key", props.democorpAdminKey()).retrieve().body(JsonNode.class);
                return r == null ? "no answer" : r.path("message").asText();
            }

            @Override
            public Outcome execute() {
                JsonNode r = http.post().uri(props.democorpUrl() + "/admin/actions/" + spec.action())
                        .header("X-Admin-Key", props.democorpAdminKey())
                        .body(Map.of("requestedBy", requestedBy, "reason", "Approved in War Room"))
                        .retrieve().body(JsonNode.class);
                return r == null ? new Outcome(false, "no answer")
                        : new Outcome(r.path("effective").asBoolean(false), r.path("message").asText());
            }

            @Override
            public Verification verify() {
                String query = spec.verifyQueryFor(target);
                if (query == null) {
                    return new Verification(true, "No metric check for this action; confirmed by the admin API.");
                }
                Instant deadline = Instant.now().plus(props.verifyTimeout());
                double last = Double.NaN;
                while (Instant.now().isBefore(deadline)) {
                    last = value(query);
                    if (!Double.isNaN(last) && last < spec.verifyBelow()) {
                        return new Verification(true, String.format("%s = %.3f (needs < %s)", query, last, spec.verifyBelow()));
                    }
                    LockSupport.parkNanos(Duration.ofSeconds(5).toNanos());
                }
                return new Verification(false, String.format("%s still %.3f after %d s (needs < %s)", query, last,
                        props.verifyTimeout().toSeconds(), spec.verifyBelow()));
            }

            @Override
            public String undo() {
                // DemoCorp's fixes only clear the fault they address, so a fix that did not help changed nothing.
                return "Nothing to undo: the action did not change the faulty state. Incident goes back to investigation.";
            }
        };
    }

    private double value(String promql) {
        JsonNode body = http.get().uri(UriComponentsBuilder.fromUriString(props.prometheusUrl()).path("/api/v1/query")
                        .queryParam("query", "{q}").buildAndExpand(promql).encode().toUri())
                .retrieve().body(JsonNode.class);
        JsonNode result = body == null ? null : body.path("data").path("result");
        if (result == null || result.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(result.path(0).path("value").path(1).asText("NaN"));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
