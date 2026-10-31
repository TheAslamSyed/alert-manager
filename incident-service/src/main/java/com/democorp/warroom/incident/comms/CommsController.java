package com.democorp.warroom.incident.comms;

import com.democorp.warroom.common.events.EventPublisher;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.events.Topics;
import com.democorp.warroom.incident.service.IncidentService;
import com.democorp.warroom.incident.service.InvestigationProjection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@Tag(name = "Communications", description = "Stakeholder message drafts: review, edit, approve and send (FR-07)")
public class CommsController {

    private static final Logger log = LoggerFactory.getLogger(CommsController.class);

    private final CommsDraftRepository drafts;
    private final IncidentService incidents;
    private final InvestigationProjection projection;
    private final EventPublisher publisher;

    public CommsController(CommsDraftRepository drafts, IncidentService incidents, InvestigationProjection projection,
                           EventPublisher publisher) {
        this.drafts = drafts;
        this.incidents = incidents;
        this.projection = projection;
        this.publisher = publisher;
    }

    @GetMapping("/api/incidents/{id}/comms")
    public List<CommsDraftEntity> forIncident(@PathVariable String id) {
        return drafts.findByIncidentIdOrderByCreatedAtDesc(id);
    }

    @GetMapping("/api/incidents/{id}/investigation")
    @Operation(summary = "Agent findings with evidence, ranked root causes and the fix plan (RCA dashboard)")
    public Map<String, Object> investigation(@PathVariable String id) {
        incidents.find(id);
        return projection.investigation(id);
    }

    @PutMapping("/api/comms/{draftId}")
    @PreAuthorize("hasAnyRole('RESPONDER', 'APPROVER')")
    @Transactional
    public CommsDraftEntity edit(@PathVariable String draftId, @RequestBody Edit edit, Authentication user) {
        CommsDraftEntity d = find(draftId);
        d.edit(edit.subject(), edit.body(), user.getName());
        return drafts.save(d);
    }

    @PostMapping("/api/comms/{draftId}/send")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Approve and send a draft (recorded on the incident and in the audit log)")
    @Transactional
    public CommsDraftEntity send(@PathVariable String draftId, Authentication user) {
        CommsDraftEntity d = find(draftId);
        d.send(user.getName());
        drafts.save(d);
        // Channel adapters (email, chat, status page) would plug in here; the demo records the send.
        log.info("Sent {} message {} for {} via {} (approved by {})", d.getAudience(), d.getId(), d.getIncidentId(),
                d.getChannel(), user.getName());
        publisher.publish(Topics.INCIDENT, d.getIncidentId(), EventTypes.COMMS_MESSAGE_SENT, d.getIncidentId(),
                Map.of("messageId", d.getId(), "audience", d.getAudience(), "channel", d.getChannel(),
                        "subject", d.getSubject(), "sentBy", user.getName()));
        incidents.addTimelineEntry(d.getIncidentId(), "COMMS_SENT",
                d.getAudience().toLowerCase() + " update sent: " + d.getSubject(), user.getName());
        return d;
    }

    private CommsDraftEntity find(String id) {
        return drafts.findById(id).orElseThrow(() -> new NoSuchElementException("No message " + id));
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    public record Edit(String subject, String body) {
    }
}
