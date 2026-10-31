package com.democorp.warroom.agent.core;

import com.democorp.warroom.agent.comms.CommunicationAgent;
import com.democorp.warroom.common.contracts.IncidentSnapshot;
import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.EventEnvelope;
import com.democorp.warroom.common.events.EventPublisher;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.events.Topics;
import com.democorp.warroom.common.messaging.IdempotencyGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/** incident.v1 → start an investigation when an incident opens; draft status updates when it changes. */
@Component
public class IncidentListener {

    private static final Set<IncidentStatus> UPDATE_ON = EnumSet.of(IncidentStatus.MITIGATED, IncidentStatus.RESOLVED,
            IncidentStatus.CLOSED_FALSE_POSITIVE);

    private final Supervisor supervisor;
    private final CommunicationAgent comms;
    private final InvestigationRepository investigations;
    private final EventCodec codec;
    private final EventPublisher publisher;
    private final IdempotencyGuard guard;
    private final ObjectMapper mapper;

    public IncidentListener(Supervisor supervisor, CommunicationAgent comms, InvestigationRepository investigations,
                            EventCodec codec, EventPublisher publisher, IdempotencyGuard guard, ObjectMapper mapper) {
        this.supervisor = supervisor;
        this.comms = comms;
        this.investigations = investigations;
        this.codec = codec;
        this.publisher = publisher;
        this.guard = guard;
        this.mapper = mapper;
    }

    @KafkaListener(id = "agent-incidents", topics = Topics.INCIDENT)
    @Transactional
    public void onIncident(String json) {
        EventEnvelope e = codec.decode(json);
        if (!guard.firstTime(e.messageId(), "agent.incident")) {
            return;
        }
        if (EventTypes.INCIDENT_OPENED.equals(e.type())) {
            supervisor.open(codec.payload(e, IncidentSnapshot.class));
        } else if (EventTypes.INCIDENT_STATUS_CHANGED.equals(e.type())) {
            IncidentSnapshot s = codec.payload(e, IncidentSnapshot.class);
            if (UPDATE_ON.contains(s.status())) {
                String investigationId = investigations.findByIncidentId(s.incidentId())
                        .map(InvestigationRecord::getId).orElse(null);
                String rootCause = investigations.findByIncidentId(s.incidentId())
                        .map(InvestigationRecord::getResult).map(this::topCause).orElse(null);
                publisher.publish(Topics.INVESTIGATION, s.incidentId(), EventTypes.COMMS_DRAFTED, s.incidentId(),
                        comms.statusUpdate(investigationId, s, rootCause));
            }
        }
    }

    private String topCause(String resultJson) {
        try {
            JsonNode h = mapper.readTree(resultJson).path("rca").path("hypotheses").path(0).path("title");
            return h.isMissingNode() ? null : h.asText();
        } catch (Exception ex) {
            return null;
        }
    }
}
