package com.democorp.warroom.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** What we learned about an incident from the event stream; used to write its postmortem. */
@Entity
@Table(name = "incident_fact")
public class IncidentFact {

    @Id
    @Column(name = "incident_id")
    private String incidentId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> facts = new HashMap<>();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IncidentFact() {
    }

    public IncidentFact(String incidentId) {
        this.incidentId = incidentId;
        this.updatedAt = Instant.now();
    }

    public void put(String key, Object value) {
        Map<String, Object> copy = new HashMap<>(facts);
        copy.put(key, value);
        facts = copy;
        updatedAt = Instant.now();
    }

    public String getIncidentId() { return incidentId; }
    public Map<String, Object> getFacts() { return facts; }
}
