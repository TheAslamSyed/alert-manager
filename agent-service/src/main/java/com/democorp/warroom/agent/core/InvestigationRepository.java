package com.democorp.warroom.agent.core;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvestigationRepository extends JpaRepository<InvestigationRecord, String> {

    Optional<InvestigationRecord> findByIncidentId(String incidentId);

    List<InvestigationRecord> findByStatusInAndAttemptsLessThan(Collection<String> statuses, int attempts);
}
