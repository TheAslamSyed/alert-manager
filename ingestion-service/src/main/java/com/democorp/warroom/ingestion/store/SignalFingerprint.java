package com.democorp.warroom.ingestion.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "signal_fingerprint")
public class SignalFingerprint {

    @Id
    private String fingerprint;
    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;
    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;
    @Column(nullable = false)
    private int occurrences;

    protected SignalFingerprint() {
    }

    public SignalFingerprint(String fingerprint, Instant now) {
        this.fingerprint = fingerprint;
        this.firstSeen = now;
        this.lastSeen = now;
        this.occurrences = 1;
    }

    public void seenAgain(Instant now) {
        this.lastSeen = now;
        this.occurrences++;
    }

    public void restart(Instant now) {
        this.firstSeen = now;
        this.lastSeen = now;
        this.occurrences = 1;
    }

    public Instant getLastSeen() { return lastSeen; }
}
