package com.democorp.warroom.remediation.messaging;

import com.democorp.warroom.common.contracts.Investigation.RemediationProposal;
import com.democorp.warroom.common.contracts.Remediation.Decision;
import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.EventEnvelope;
import com.democorp.warroom.common.events.EventTypes;
import com.democorp.warroom.common.events.Topics;
import com.democorp.warroom.common.messaging.IdempotencyGuard;
import com.democorp.warroom.remediation.service.RemediationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * investigation.v1 → store proposals.
 * remediation.v1 ACTION_APPROVED → executor (its own consumer, so it could move to a separate service later).
 */
@Component
public class RemediationListeners {

    private final RemediationService remediation;
    private final EventCodec codec;
    private final IdempotencyGuard guard;

    public RemediationListeners(RemediationService remediation, EventCodec codec, IdempotencyGuard guard) {
        this.remediation = remediation;
        this.codec = codec;
        this.guard = guard;
    }

    @KafkaListener(id = "remediation-proposals", topics = Topics.INVESTIGATION)
    public void onInvestigation(String json) {
        EventEnvelope e = codec.decode(json);
        if (EventTypes.REMEDIATION_PROPOSED.equals(e.type()) && guard.firstTime(e.messageId(), "remediation.proposals")) {
            remediation.onProposal(codec.payload(e, RemediationProposal.class));
        }
    }

    @KafkaListener(id = "remediation-executor", topics = Topics.REMEDIATION)
    public void onApproved(String json) {
        EventEnvelope e = codec.decode(json);
        if (EventTypes.ACTION_APPROVED.equals(e.type()) && guard.firstTime(e.messageId(), "remediation.executor")) {
            remediation.execute(codec.payload(e, Decision.class));
        }
    }
}
