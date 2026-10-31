package com.democorp.warroom.incident.comms;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommsDraftRepository extends JpaRepository<CommsDraftEntity, String> {

    List<CommsDraftEntity> findByIncidentIdOrderByCreatedAtDesc(String incidentId);

    List<CommsDraftEntity> findByIncidentIdAndAudienceAndStatus(String incidentId, String audience, String status);

    @Query(value = "select nextval('comms_number_seq')", nativeQuery = true)
    long nextNumber();
}
