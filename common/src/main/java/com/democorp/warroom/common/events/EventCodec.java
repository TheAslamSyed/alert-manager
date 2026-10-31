package com.democorp.warroom.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads and writes {@link EventEnvelope}s as JSON strings (the format stored on Kafka). */
public class EventCodec {

    private final ObjectMapper mapper;

    public EventCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public EventEnvelope decode(String json) {
        try {
            return mapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException("Could not read event: " + e.getOriginalMessage(), e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        try {
            return mapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException(
                    "Could not read payload of " + envelope.type() + " as " + type.getSimpleName(), e);
        }
    }

    public String encode(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write event " + envelope.type(), e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /** Thrown for events that can never be processed; the Kafka error handler sends them straight to the DLT. */
    public static class MalformedEventException extends RuntimeException {
        public MalformedEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
