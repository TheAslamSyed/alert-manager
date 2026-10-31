package com.democorp.warroom.incident.domain;

import com.democorp.warroom.common.contracts.IncidentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, String> {

    List<Incident> findByStatusNotInAndUpdatedAtAfterOrderByUpdatedAtDesc(Collection<IncidentStatus> excluded,
                                                                          Instant since);

    List<Incident> findAllByOrderByOpenedAtDesc(Pageable page);

    List<Incident> findByStatusOrderByOpenedAtDesc(IncidentStatus status, Pageable page);

    @Query(value = "select nextval('incident_number_seq')", nativeQuery = true)
    long nextNumber();
}
