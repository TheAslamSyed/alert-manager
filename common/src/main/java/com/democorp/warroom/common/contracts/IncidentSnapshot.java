package com.democorp.warroom.common.contracts;

import java.time.Instant;
import java.util.List;

/**
 * The incident as other services see it on {@code incident.v1}.
 * Includes the correlated signals so agents have the deploy/ticket context without calling back.
 */
public record IncidentSnapshot(
        String incidentId,
        String title,
        String primaryService,
        List<String> services,
        Severity severity,
        IncidentStatus status,
        Instant openedAt,
        Instant updatedAt,
        List<SignalPayload> signals,
        List<String> correlationNotes,
        IncidentStatus previousStatus,
        String changedBy) {
}
