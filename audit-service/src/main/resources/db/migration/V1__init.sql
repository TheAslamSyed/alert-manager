-- Append-only, hash-chained audit log (FR-09).
CREATE TABLE audit_record (
    seq           BIGSERIAL    PRIMARY KEY,
    message_id    VARCHAR(64)  NOT NULL UNIQUE,
    topic         VARCHAR(64)  NOT NULL,
    type          VARCHAR(64)  NOT NULL,
    incident_id   VARCHAR(20),
    producer      VARCHAR(64)  NOT NULL,
    trace_id      VARCHAR(64),
    occurred_at   TIMESTAMPTZ  NOT NULL,
    recorded_at   TIMESTAMPTZ  NOT NULL,
    payload_text  TEXT         NOT NULL,   -- exact bytes that were hashed
    prev_hash     CHAR(64)     NOT NULL,
    hash          CHAR(64)     NOT NULL
);
CREATE INDEX audit_record_incident_idx ON audit_record (incident_id, seq);

-- Nobody (not even this service) can change or remove a record.
CREATE FUNCTION audit_record_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_record is append-only (% blocked)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_record_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_record
    FOR EACH ROW EXECUTE FUNCTION audit_record_is_append_only();
