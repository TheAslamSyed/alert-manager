package com.democorp.bank.ingest;

import com.democorp.bank.DemocorpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sends DemoCorp's "change" signals (deploys, config changes, customer tickets) to the War Room's
 * ingestion-service, the way a CI pipeline or ticketing tool would. Failures are logged, never fatal.
 */
@Component
public class IngestClient {

    private static final Logger log = LoggerFactory.getLogger(IngestClient.class);

    private final RestClient rest;

    public IngestClient(RestClient.Builder builder, DemocorpProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.rest = builder.baseUrl(props.ingestUrl())
                .requestFactory(factory)
                .defaultHeader("X-API-Key", props.ingestApiKey())
                .build();
    }

    public void deploy(String service, String fromVersion, String toVersion, String summary, String by) {
        post("/ingest/deploy", new DeployEvent(service, fromVersion, toVersion, summary, by, Instant.now()));
    }

    public void configChange(String service, String key, String oldValue, String newValue, String by) {
        post("/ingest/config-change", new ConfigChangeEvent(service, key, oldValue, newValue, by, Instant.now()));
    }

    public void ticket(String service, String subject, String description) {
        String id = "TCK-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        post("/ingest/ticket", new TicketEvent(id, "cust-" + (1000 + (int) (Math.random() * 9000)),
                subject, description, service, Instant.now()));
    }

    /** Fallback path used only when DEMOCORP_DIRECT_ALERTS=true. */
    public void alert(String service, String alertName, String severity, String summary) {
        post("/ingest/events", new GenericEvent("ALERT", "democorp-direct", service, severity, summary, summary,
                alertName + ":" + service, Instant.now(), Map.of("alertname", alertName)));
    }

    private void post(String path, Object body) {
        try {
            rest.post().uri(path).body(body).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not send signal to ingestion {}: {}", path, e.getMessage());
        }
    }

    record DeployEvent(String service, String fromVersion, String toVersion, String changeSummary,
                       String deployedBy, Instant occurredAt) {
    }

    record ConfigChangeEvent(String service, String key, String oldValue, String newValue,
                             String changedBy, Instant occurredAt) {
    }

    record TicketEvent(String ticketId, String customerRef, String subject, String description,
                       String service, Instant occurredAt) {
    }

    record GenericEvent(String kind, String source, String service, String severity, String title,
                        String description, String fingerprint, Instant occurredAt, Map<String, String> attributes) {
    }
}
