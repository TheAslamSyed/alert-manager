package com.democorp.warroom.remediation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RemediationActionRepository extends JpaRepository<RemediationAction, String> {

    List<RemediationAction> findByIncidentIdOrderByCreatedAtAsc(String incidentId);

    List<RemediationAction> findByStatusOrderByCreatedAtAsc(RemediationAction.Status status);

    List<RemediationAction> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('action_number_seq')", nativeQuery = true)
    long nextNumber();
}
