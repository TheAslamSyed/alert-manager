package com.democorp.warroom.incident.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimelineRepository extends JpaRepository<TimelineEntry, Long> {

    List<TimelineEntry> findByIncidentIdOrderByAtAscIdAsc(String incidentId);
}
