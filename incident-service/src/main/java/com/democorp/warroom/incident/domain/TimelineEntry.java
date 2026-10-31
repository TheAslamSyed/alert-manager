package com.democorp.warroom.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "timeline_entry")
public class TimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "incident_id", nullable = false)
    private String incidentId;
    @Column(nullable = false)
    private Instant at;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private String text;
    @Column(nullable = false)
    private String actor;

    protected TimelineEntry() {
    }

    public TimelineEntry(String incidentId, Instant at, String type, String text, String actor) {
        this.incidentId = incidentId;
        this.at = at;
        this.type = type;
        this.text = text;
        this.actor = actor;
    }

    public Long getId() { return id; }
    public String getIncidentId() { return incidentId; }
    public Instant getAt() { return at; }
    public String getType() { return type; }
    public String getText() { return text; }
    public String getActor() { return actor; }
}
