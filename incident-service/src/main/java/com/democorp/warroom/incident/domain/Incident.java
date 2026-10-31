package com.democorp.warroom.incident.domain;

import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.contracts.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incident")
public class Incident {

    @Id
    private String id;
    @Column(nullable = false)
    private String title;
    @Column(name = "primary_service")
    private String primaryService;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> services = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correlation_notes", nullable = false, columnDefinition = "jsonb")
    private List<String> correlationNotes = new ArrayList<>();
    @Column(name = "rca_summary")
    private String rcaSummary;
    @Column(name = "rca_confidence")
    private Double rcaConfidence;
    @Version
    private long version;

    protected Incident() {
    }

    public Incident(String id, String title, String primaryService, Severity severity, Instant now) {
        this.id = id;
        this.title = title;
        this.primaryService = primaryService;
        this.severity = severity;
        this.status = IncidentStatus.OPEN;
        this.openedAt = now;
        this.updatedAt = now;
        if (primaryService != null) {
            services.add(primaryService);
        }
    }

    /** State pattern guard: only allowed transitions pass. */
    public IncidentStatus moveTo(IncidentStatus target, Instant now) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStatusChangeException(id, status, target);
        }
        IncidentStatus previous = status;
        status = target;
        updatedAt = now;
        if (target.isClosed()) {
            resolvedAt = now;
        }
        return previous;
    }

    public void addService(String service) {
        if (service != null && !services.contains(service)) {
            services.add(service);
        }
    }

    public void raiseSeverity(Severity candidate) {
        severity = severity.moreSevere(candidate);
    }

    public void addNote(String note) {
        if (!correlationNotes.contains(note)) {
            correlationNotes.add(note);
        }
    }

    public void touch(Instant now) {
        updatedAt = now;
    }

    public void recordRca(String summary, Double confidence) {
        this.rcaSummary = summary;
        this.rcaConfidence = confidence;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getPrimaryService() { return primaryService; }
    public List<String> getServices() { return List.copyOf(services); }
    public Severity getSeverity() { return severity; }
    public IncidentStatus getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public List<String> getCorrelationNotes() { return List.copyOf(correlationNotes); }
    public String getRcaSummary() { return rcaSummary; }
    public Double getRcaConfidence() { return rcaConfidence; }

    public static class IllegalStatusChangeException extends RuntimeException {
        public IllegalStatusChangeException(String id, IncidentStatus from, IncidentStatus to) {
            super("Incident " + id + " cannot move from " + from + " to " + to + " (allowed: " + from.next() + ")");
        }
    }
}
