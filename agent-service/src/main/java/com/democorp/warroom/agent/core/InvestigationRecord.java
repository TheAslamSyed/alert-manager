package com.democorp.warroom.agent.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Durable investigation state, so a crashed or restarted agent-service picks up where it left off. */
@Entity
@Table(name = "investigation")
public class InvestigationRecord {

    @Id
    private String id;
    @Column(name = "incident_id", nullable = false, unique = true)
    private String incidentId;
    @Column(nullable = false)
    private String status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String snapshot;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String result;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(nullable = false)
    private int attempts;

    protected InvestigationRecord() {
    }

    public InvestigationRecord(String id, String incidentId, String snapshot) {
        this.id = id;
        this.incidentId = incidentId;
        this.snapshot = snapshot;
        this.status = "PENDING";
        this.startedAt = Instant.now();
    }

    public void running() {
        status = "RUNNING";
        attempts++;
    }

    public void finish(String status, String result) {
        this.status = status;
        this.result = result;
        this.completedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getIncidentId() { return incidentId; }
    public String getStatus() { return status; }
    public String getSnapshot() { return snapshot; }
    public String getResult() { return result; }
    public int getAttempts() { return attempts; }
}
