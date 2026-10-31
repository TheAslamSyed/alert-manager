package com.democorp.warroom.incident.domain;

import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.contracts.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentLifecycleTest {

    @Test
    void followsTheNormalPath() {
        Incident incident = new Incident("INC-1", "t", "payment-service", Severity.SEV1, Instant.now());
        incident.moveTo(IncidentStatus.INVESTIGATING, Instant.now());
        incident.moveTo(IncidentStatus.IDENTIFIED, Instant.now());
        incident.moveTo(IncidentStatus.MITIGATING, Instant.now());
        incident.moveTo(IncidentStatus.MITIGATED, Instant.now());
        incident.moveTo(IncidentStatus.RESOLVED, Instant.now());
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
    }

    @Test
    void rejectsSkippingStraightToMitigated() {
        Incident incident = new Incident("INC-2", "t", "payment-service", Severity.SEV1, Instant.now());
        assertThatThrownBy(() -> incident.moveTo(IncidentStatus.MITIGATED, Instant.now()))
                .isInstanceOf(Incident.IllegalStatusChangeException.class);
    }

    @Test
    void closedIncidentsCannotReopen() {
        Incident incident = new Incident("INC-3", "t", "account-service", Severity.SEV4, Instant.now());
        incident.moveTo(IncidentStatus.CLOSED_FALSE_POSITIVE, Instant.now());
        assertThat(incident.getStatus().next()).isEmpty();
    }

    @Test
    void severityOnlyGetsWorse() {
        Incident incident = new Incident("INC-4", "t", "payment-service", Severity.SEV3, Instant.now());
        incident.raiseSeverity(Severity.SEV1);
        incident.raiseSeverity(Severity.SEV4);
        assertThat(incident.getSeverity()).isEqualTo(Severity.SEV1);
    }
}
