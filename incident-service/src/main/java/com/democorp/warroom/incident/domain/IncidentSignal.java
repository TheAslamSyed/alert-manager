package com.democorp.warroom.incident.domain;

import com.democorp.warroom.common.contracts.SignalKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A signal linked to an incident (or waiting, with incidentId = null, for one to open). */
@Entity
@Table(name = "incident_signal")
public class IncidentSignal {

    @Id
    @Column(name = "signal_id")
    private String signalId;
    @Column(name = "incident_id")
    private String incidentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalKind kind;
    private String service;
    @Column(nullable = false)
    private String title;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "link_reason")
    private String linkReason;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    protected IncidentSignal() {
    }

    public IncidentSignal(String signalId, SignalKind kind, String service, String title, Instant occurredAt,
                          Instant receivedAt, String payload) {
        this.signalId = signalId;
        this.kind = kind;
        this.service = service;
        this.title = title;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.payload = payload;
    }

    public void linkTo(String incidentId, String reason) {
        this.incidentId = incidentId;
        this.linkReason = reason;
    }

    public String getSignalId() { return signalId; }
    public String getIncidentId() { return incidentId; }
    public SignalKind getKind() { return kind; }
    public String getService() { return service; }
    public String getTitle() { return title; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getLinkReason() { return linkReason; }
    public String getPayload() { return payload; }
}
