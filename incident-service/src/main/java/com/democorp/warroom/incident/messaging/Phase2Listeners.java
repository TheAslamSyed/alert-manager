package com.democorp.warroom.incident.messaging;

import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.Topics;
import com.democorp.warroom.incident.service.InvestigationProjection;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** investigation.v1 and remediation.v1 → incident status, timeline, RCA dashboard and message drafts. */
@Component
public class Phase2Listeners {

    private final InvestigationProjection projection;
    private final EventCodec codec;

    public Phase2Listeners(InvestigationProjection projection, EventCodec codec) {
        this.projection = projection;
        this.codec = codec;
    }

    @KafkaListener(id = "incident-investigations", topics = Topics.INVESTIGATION, concurrency = "1")
    public void onInvestigation(String json) {
        projection.onInvestigationEvent(codec.decode(json));
    }

    @KafkaListener(id = "incident-remediations", topics = Topics.REMEDIATION, concurrency = "1")
    public void onRemediation(String json) {
        projection.onRemediationEvent(codec.decode(json));
    }
}
