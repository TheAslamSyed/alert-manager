package com.democorp.warroom.incident.service;

import com.democorp.warroom.common.contracts.IncidentSnapshot;
import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.incident.api.IncidentViews.IncidentDetail;
import com.democorp.warroom.incident.api.IncidentViews.IncidentSummary;
import com.democorp.warroom.incident.api.IncidentViews.SignalView;
import com.democorp.warroom.incident.api.IncidentViews.TimelineView;
import com.democorp.warroom.incident.domain.Incident;
import com.democorp.warroom.incident.domain.IncidentSignal;
import com.democorp.warroom.incident.domain.TimelineEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncidentMapper {

    private final ObjectMapper mapper;

    public IncidentMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public IncidentSnapshot snapshot(Incident i, List<IncidentSignal> signals, IncidentStatus previous, String changedBy) {
        return new IncidentSnapshot(i.getId(), i.getTitle(), i.getPrimaryService(), i.getServices(), i.getSeverity(),
                i.getStatus(), i.getOpenedAt(), i.getUpdatedAt(),
                signals.stream().map(this::payload).toList(), i.getCorrelationNotes(), previous, changedBy);
    }

    public IncidentSummary summary(Incident i, long signalCount) {
        return new IncidentSummary(i.getId(), i.getTitle(), i.getPrimaryService(), i.getServices(), i.getSeverity(),
                i.getStatus(), i.getOpenedAt(), i.getUpdatedAt(), signalCount, i.getRcaSummary(), i.getRcaConfidence());
    }

    public IncidentDetail detail(Incident i, List<IncidentSignal> signals, List<TimelineEntry> timeline) {
        return new IncidentDetail(summary(i, signals.size()), i.getCorrelationNotes(),
                signals.stream().map(s -> new SignalView(s.getSignalId(), s.getKind(), s.getService(), s.getTitle(),
                        s.getOccurredAt(), s.getLinkReason())).toList(),
                timeline.stream().map(t -> new TimelineView(t.getAt(), t.getType(), t.getText(), t.getActor())).toList(),
                List.copyOf(i.getStatus().next()));
    }

    public String json(SignalPayload signal) {
        try {
            return mapper.writeValueAsString(signal);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise signal", e);
        }
    }

    public SignalPayload payload(IncidentSignal s) {
        try {
            return mapper.readValue(s.getPayload(), SignalPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored signal " + s.getSignalId() + " is unreadable", e);
        }
    }
}
