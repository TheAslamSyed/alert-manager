package com.democorp.warroom.common.messaging;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Makes Kafka consumers idempotent: Kafka may deliver a message more than once,
 * so each consumer records the messageId in the same database transaction as its work.
 *
 * <p>Each service's Flyway migration creates:
 * <pre>
 * CREATE TABLE processed_message (
 *   message_id   VARCHAR(64)  NOT NULL,
 *   consumer     VARCHAR(100) NOT NULL,
 *   processed_at TIMESTAMPTZ  NOT NULL,
 *   PRIMARY KEY (message_id, consumer));
 * </pre>
 */
public class IdempotencyGuard {

    private final JdbcTemplate jdbc;

    public IdempotencyGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns true the first time this consumer sees the message, false for a duplicate. */
    public boolean firstTime(String messageId, String consumer) {
        int inserted = jdbc.update(
                "INSERT INTO processed_message (message_id, consumer, processed_at) VALUES (?, ?, now()) "
                        + "ON CONFLICT DO NOTHING",
                messageId, consumer);
        return inserted == 1;
    }
}
