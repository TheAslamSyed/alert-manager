package com.democorp.warroom.ingestion.pipeline;

import com.democorp.warroom.common.contracts.SignalPayload;

import java.util.Optional;

/**
 * Chain of Responsibility: each step may change the signal or drop it (empty result).
 * Steps run in {@link org.springframework.core.annotation.Order} order.
 */
public interface SignalStep {

    Optional<SignalPayload> apply(SignalPayload signal);

    /** Thrown when a signal is invalid; the request gets HTTP 400. */
    class InvalidSignalException extends RuntimeException {
        public InvalidSignalException(String message) {
            super(message);
        }
    }
}
