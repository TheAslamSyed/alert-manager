package com.democorp.warroom.ingestion.api;

import com.democorp.warroom.common.contracts.SignalKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request bodies for each webhook. */
public final class IngestRequests {

    private IngestRequests() {
    }

    /** Prometheus Alertmanager webhook (version 4). Unknown fields are ignored. */
    public record AlertmanagerWebhook(String version, String status, String receiver,
                                      @NotEmpty List<@Valid Alert> alerts) {
        public record Alert(String status, Map<String, String> labels, Map<String, String> annotations,
                            Instant startsAt, Instant endsAt, String generatorURL, String fingerprint) {
        }
    }

    public record DeployRequest(@NotBlank String service, String fromVersion, @NotBlank String toVersion,
                                String changeSummary, String deployedBy, Instant occurredAt) {
    }

    public record ConfigChangeRequest(@NotBlank String service, @NotBlank String key, String oldValue,
                                      String newValue, String changedBy, Instant occurredAt) {
    }

    public record TicketRequest(String ticketId, String customerRef, @NotBlank String subject,
                                String description, String service, Instant occurredAt) {
    }

    /** Any other tool (e.g. a log-alert rule): already close to the standard shape. */
    public record GenericSignalRequest(@NotNull SignalKind kind, @NotBlank String source, String service,
                                       String severity, @NotBlank String title, String description,
                                       String fingerprint, Instant occurredAt, Map<String, String> attributes) {
    }

    public record IngestResponse(int received, int accepted, int duplicates, List<String> signalIds) {
    }
}
