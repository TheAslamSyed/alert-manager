package com.democorp.warroom.ingestion.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Every signal as received, kept for replay and debugging (including dropped duplicates). */
@Entity
@Table(name = "raw_signal")
public class RawSignal {

    @Id
    private String id;
    @Column(nullable = false)
    private String kind;
    @Column(nullable = false)
    private String source;
    private String service;
    @Column(nullable = false)
    private String fingerprint;
    @Column(nullable = false)
    private boolean duplicate;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected RawSignal() {
    }

    public RawSignal(String id, String kind, String source, String service, String fingerprint,
                     boolean duplicate, String payload, Instant receivedAt) {
        this.id = id;
        this.kind = kind;
        this.source = source;
        this.service = service;
        this.fingerprint = fingerprint;
        this.duplicate = duplicate;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getService() { return service; }
    public boolean isDuplicate() { return duplicate; }
    public Instant getReceivedAt() { return receivedAt; }
}
