package com.democorp.warroom.incident.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface IncidentSignalRepository extends JpaRepository<IncidentSignal, String> {

    List<IncidentSignal> findByIncidentIdOrderByOccurredAtAsc(String incidentId);

    List<IncidentSignal> findByIncidentIdIsNullAndReceivedAtAfterOrderByOccurredAtAsc(Instant since);

    long countByIncidentId(String incidentId);
}
