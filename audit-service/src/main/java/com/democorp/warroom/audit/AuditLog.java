package com.democorp.warroom.audit;

import com.democorp.warroom.common.events.EventCodec;
import com.democorp.warroom.common.events.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * FR-09: every event from every topic is stored once, in order, with
 * {@code hash = SHA-256(prev_hash | topic | type | messageId | occurredAt | payload)}.
 * Editing any old record changes its hash and breaks every link after it, which {@link #verify()} detects.
 */
@Service
public class AuditLog {

    static final String GENESIS = "0".repeat(64);
    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private static final long CHAIN_LOCK = 777L;

    private final JdbcTemplate jdbc;
    private final EventCodec codec;

    public AuditLog(JdbcTemplate jdbc, EventCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Transactional
    public void append(String topic, String json) {
        EventEnvelope e = codec.decode(json);
        jdbc.execute("SELECT pg_advisory_xact_lock(" + CHAIN_LOCK + ")");
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM audit_record WHERE message_id = ?", Integer.class,
                e.messageId());
        if (exists != null && exists > 0) {
            return;
        }
        List<String> last = jdbc.queryForList("SELECT hash FROM audit_record ORDER BY seq DESC LIMIT 1", String.class);
        String prev = last.isEmpty() ? GENESIS : last.get(0);
        String payload = codec.encode(e);
        String hash = hash(prev, topic, e.type(), e.messageId(), e.occurredAt(), payload);
        jdbc.update("INSERT INTO audit_record (message_id, topic, type, incident_id, producer, trace_id, occurred_at, "
                        + "recorded_at, payload_text, prev_hash, hash) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                e.messageId(), topic, e.type(), e.incidentId(), e.producer(), e.traceId(),
                Timestamp.from(e.occurredAt()), Timestamp.from(Instant.now()), payload, prev, hash);
        log.debug("Audited {} {} ({})", topic, e.type(), e.incidentId());
    }

    /** Recomputes the whole chain. */
    @Transactional(readOnly = true)
    public Verification verify() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT seq, topic, type, message_id, occurred_at, payload_text, prev_hash, hash FROM audit_record ORDER BY seq");
        String expectedPrev = GENESIS;
        for (Map<String, Object> r : rows) {
            long seq = ((Number) r.get("seq")).longValue();
            String prev = ((String) r.get("prev_hash")).trim();
            Instant occurred = ((Timestamp) r.get("occurred_at")).toInstant();
            String recomputed = hash(prev, (String) r.get("topic"), (String) r.get("type"),
                    (String) r.get("message_id"), occurred, (String) r.get("payload_text"));
            if (!prev.equals(expectedPrev) || !recomputed.equals(((String) r.get("hash")).trim())) {
                return new Verification(false, rows.size(), seq, expectedPrev);
            }
            expectedPrev = recomputed;
        }
        return new Verification(true, rows.size(), null, expectedPrev);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> records(String incidentId, int limit) {
        String select = "SELECT seq, topic, type, incident_id, producer, trace_id, occurred_at, recorded_at, hash, "
                + "payload_text FROM audit_record ";
        int n = Math.min(Math.max(limit, 1), 1000);
        return incidentId == null
                ? jdbc.queryForList(select + "ORDER BY seq DESC LIMIT " + n)
                : jdbc.queryForList(select + "WHERE incident_id = ? ORDER BY seq LIMIT " + n, incidentId);
    }

    static String hash(String prev, String topic, String type, String messageId, Instant occurredAt, String payload) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String material = prev + "|" + topic + "|" + type + "|" + messageId + "|"
                    + occurredAt.toEpochMilli() + "|" + payload;
            return HexFormat.of().formatHex(sha.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param brokenAt first record whose hash or link does not match (null when valid)
     * @param lastHash head of the chain
     */
    public record Verification(boolean valid, int records, Long brokenAt, String lastHash) {
    }
}
