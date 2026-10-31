package com.democorp.warroom.incident.api;

import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.incident.service.IncidentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Internal endpoints for the evaluation runner (Docker network only; the gateway does not route /internal/**).
 * Lets it read how fast and how accurately each practice incident was diagnosed, and tidy up afterwards.
 */
@RestController
public class EvaluationSupportController {

    private final JdbcTemplate jdbc;
    private final IncidentService incidents;

    public EvaluationSupportController(JdbcTemplate jdbc, IncidentService incidents) {
        this.jdbc = jdbc;
        this.incidents = incidents;
    }

    @GetMapping("/internal/evaluation/incidents")
    public List<Map<String, Object>> since(@RequestParam Instant since) {
        return jdbc.queryForList("""
                SELECT i.id, i.status, i.opened_at, i.title, r.updated_at AS rca_at,
                       r.payload::text AS rca, r.plan::text AS plan
                FROM incident i LEFT JOIN incident_rca r ON r.incident_id = i.id
                WHERE i.opened_at >= ? ORDER BY i.opened_at""", Timestamp.from(since));
    }

    @GetMapping("/internal/evaluation/open")
    public List<String> open() {
        return jdbc.queryForList("SELECT id FROM incident WHERE status NOT IN ('RESOLVED','CLOSED_FALSE_POSITIVE')",
                String.class);
    }

    /** Close a practice incident so the next scenario starts clean. */
    @PostMapping("/internal/evaluation/incidents/{id}/close")
    public Map<String, Object> close(@PathVariable String id) {
        IncidentStatus status = incidents.find(id).getStatus();
        boolean closed = status.canMoveTo(IncidentStatus.CLOSED_FALSE_POSITIVE)
                ? incidents.moveIfAllowed(id, IncidentStatus.CLOSED_FALSE_POSITIVE, "evaluation-runner", "Practice run finished")
                : incidents.moveIfAllowed(id, IncidentStatus.RESOLVED, "evaluation-runner", "Practice run finished");
        return Map.of("id", id, "closed", closed);
    }
}
