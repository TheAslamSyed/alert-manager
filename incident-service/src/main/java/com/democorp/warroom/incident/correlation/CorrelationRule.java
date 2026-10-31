package com.democorp.warroom.incident.correlation;

import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.incident.domain.Incident;

import java.util.Optional;

/**
 * Strategy pattern: one way of deciding that a signal belongs to an existing incident.
 * Rules add up; the engine attaches the signal when the total reaches the threshold.
 */
public interface CorrelationRule {

    Optional<Match> evaluate(SignalPayload signal, Incident candidate);

    record Match(double score, String reason) {
    }
}
