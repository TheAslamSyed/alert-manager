package com.democorp.warroom.common.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * The one message shape used on every Kafka topic.
 *
 * @param messageId     unique id, used by consumers to ignore duplicates
 * @param type          event type, see {@link EventTypes}
 * @param schemaVersion version of the payload shape
 * @param occurredAt    when the producer created the event
 * @param producer      spring.application.name of the producer
 * @param incidentId    incident this event belongs to (null for raw signals)
 * @param traceId       distributed trace id, so one incident can be followed in Zipkin
 * @param payload       event-specific body
 */
public record EventEnvelope(
        String messageId,
        String type,
        int schemaVersion,
        Instant occurredAt,
        String producer,
        String incidentId,
        String traceId,
        JsonNode payload) {
}
