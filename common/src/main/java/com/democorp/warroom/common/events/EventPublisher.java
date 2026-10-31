package com.democorp.warroom.common.events;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes events to Kafka in the standard envelope.
 *
 * <p>{@link #publishAfterCommit} waits until the surrounding database transaction commits,
 * so consumers never see an event for data that was rolled back.
 * Sends are synchronous (acks=all, idempotent producer) so failures surface in the logs.
 * A transactional outbox would close the remaining gap (crash between commit and send);
 * it is a planned stretch item.
 */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafka;
    private final EventCodec codec;
    private final String producer;
    private final ObjectProvider<Tracer> tracer;

    public EventPublisher(KafkaTemplate<String, String> kafka, EventCodec codec, String producer,
                          ObjectProvider<Tracer> tracer) {
        this.kafka = kafka;
        this.codec = codec;
        this.producer = producer;
        this.tracer = tracer;
    }

    /** Builds the envelope and sends it now. */
    public EventEnvelope publish(String topic, String key, String type, String incidentId, Object payload) {
        EventEnvelope envelope = envelope(type, incidentId, payload);
        send(topic, key, envelope);
        return envelope;
    }

    /** Sends after the current transaction commits, or now if there is no transaction. */
    public EventEnvelope publishAfterCommit(String topic, String key, String type, String incidentId, Object payload) {
        EventEnvelope envelope = envelope(type, incidentId, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, key, envelope);
                }
            });
        } else {
            send(topic, key, envelope);
        }
        return envelope;
    }

    private EventEnvelope envelope(String type, String incidentId, Object payload) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                type,
                1,
                Instant.now(),
                producer,
                incidentId,
                currentTraceId(),
                codec.mapper().valueToTree(payload));
    }

    private void send(String topic, String key, EventEnvelope envelope) {
        try {
            kafka.send(topic, key, codec.encode(envelope)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Published {} to {} key={}", envelope.type(), topic, key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(topic, envelope, e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to publish {} (messageId={}) to {}", envelope.type(), envelope.messageId(), topic, e);
            throw new EventPublishException(topic, envelope, e);
        }
    }

    private String currentTraceId() {
        Tracer t = tracer.getIfAvailable();
        if (t == null) {
            return null;
        }
        Span span = t.currentSpan();
        return span == null ? null : span.context().traceId();
    }

    public static class EventPublishException extends RuntimeException {
        public EventPublishException(String topic, EventEnvelope envelope, Throwable cause) {
            super("Could not publish " + envelope.type() + " to " + topic, cause);
        }
    }
}
