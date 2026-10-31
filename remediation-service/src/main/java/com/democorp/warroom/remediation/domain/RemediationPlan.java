package com.democorp.warroom.remediation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** The planner's preventive actions and rationale for an incident (FR-06). */
@Entity
@Table(name = "remediation_plan")
public class RemediationPlan {

    @Id
    @Column(name = "incident_id")
    private String incidentId;
    @Column(name = "root_cause")
    private String rootCause;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> preventive;
    @Column(name = "close_as_false_positive", nullable = false)
    private boolean closeAsFalsePositive;
    private String rationale;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RemediationPlan() {
    }

    public RemediationPlan(String incidentId, String rootCause, List<String> preventive, boolean closeAsFalsePositive,
                           String rationale) {
        this.incidentId = incidentId;
        this.rootCause = rootCause;
        this.preventive = List.copyOf(preventive);
        this.closeAsFalsePositive = closeAsFalsePositive;
        this.rationale = rationale;
        this.createdAt = Instant.now();
    }

    public String getIncidentId() { return incidentId; }
    public String getRootCause() { return rootCause; }
    public List<String> getPreventive() { return preventive; }
    public boolean isCloseAsFalsePositive() { return closeAsFalsePositive; }
    public String getRationale() { return rationale; }
    public Instant getCreatedAt() { return createdAt; }
}
