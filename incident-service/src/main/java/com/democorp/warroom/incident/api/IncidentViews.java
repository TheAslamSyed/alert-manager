package com.democorp.warroom.incident.api;

import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.contracts.Severity;
import com.democorp.warroom.common.contracts.SignalKind;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** Read models returned by the REST API. */
public final class IncidentViews {

    private IncidentViews() {
    }

    public record IncidentSummary(String id, String title, String primaryService, List<String> services,
                                  Severity severity, IncidentStatus status, Instant openedAt, Instant updatedAt,
                                  long signalCount, String rcaSummary, Double rcaConfidence) {
    }

    public record IncidentDetail(IncidentSummary incident, List<String> correlationNotes,
                                 List<SignalView> signals, List<TimelineView> timeline,
                                 List<IncidentStatus> allowedNextStatuses) {
    }

    public record SignalView(String signalId, SignalKind kind, String service, String title, Instant occurredAt,
                             String linkReason) {
    }

    public record TimelineView(Instant at, String type, String text, String actor) {
    }

    public record StatusChangeRequest(@NotNull IncidentStatus status, String note) {
    }
}
