package com.democorp.warroom.incident.api;

import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.incident.api.IncidentViews.IncidentDetail;
import com.democorp.warroom.incident.api.IncidentViews.IncidentSummary;
import com.democorp.warroom.incident.api.IncidentViews.StatusChangeRequest;
import com.democorp.warroom.incident.domain.Incident.IllegalStatusChangeException;
import com.democorp.warroom.incident.live.IncidentStream;
import com.democorp.warroom.incident.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Incidents, their signals and timeline (FR-02, FR-09)")
public class IncidentController {

    private final IncidentService incidents;
    private final IncidentStream stream;

    public IncidentController(IncidentService incidents, IncidentStream stream) {
        this.incidents = incidents;
        this.stream = stream;
    }

    @GetMapping
    @Operation(summary = "List incidents, newest first")
    public List<IncidentSummary> list(@RequestParam(required = false) IncidentStatus status,
                                      @RequestParam(defaultValue = "50") int limit) {
        return incidents.list(status, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One incident with its correlated signals and timeline")
    public IncidentDetail detail(@PathVariable String id) {
        return incidents.detail(id);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('RESPONDER', 'APPROVER')")
    @Operation(summary = "Move the incident to a new status (e.g. RESOLVED, CLOSED_FALSE_POSITIVE)")
    public IncidentDetail changeStatus(@PathVariable String id, @Valid @RequestBody StatusChangeRequest request,
                                       Authentication user) {
        return incidents.changeStatus(id, request.status(), user.getName(), request.note());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live updates (Server-Sent Events)")
    public SseEmitter stream() {
        return stream.subscribe();
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalStatusChangeException.class)
    ProblemDetail conflict(IllegalStatusChangeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
