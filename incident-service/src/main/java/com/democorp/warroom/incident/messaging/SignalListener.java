package com.democorp.warroom.incident.messaging;

import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.Topics;
import com.democorp.warroom.incident.service.IncidentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes signals.v1. One consumer thread: correlation decisions must not race each other. */
@Component
public class SignalListener {

    private final IncidentService incidents;
    private final EventCodec codec;

    public SignalListener(IncidentService incidents, EventCodec codec) {
        this.incidents = incidents;
        this.codec = codec;
    }

    @KafkaListener(id = "incident-signals", topics = Topics.SIGNALS, concurrency = "1")
    public void onSignal(String json) {
        incidents.onSignalEvent(codec.decode(json));
    }
}
