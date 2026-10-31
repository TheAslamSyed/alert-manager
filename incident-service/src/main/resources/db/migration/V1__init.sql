CREATE SEQUENCE incident_number_seq START WITH 2041;

CREATE TABLE incident (
    id                 VARCHAR(20)  PRIMARY KEY,
    title              VARCHAR(300) NOT NULL,
    primary_service    VARCHAR(64),
    services           JSONB        NOT NULL,
    severity           VARCHAR(8)   NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    opened_at          TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    resolved_at        TIMESTAMPTZ,
    correlation_notes  JSONB        NOT NULL,
    rca_summary        TEXT,
    rca_confidence     DOUBLE PRECISION,
    version            BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX incident_status_idx ON incident (status, updated_at DESC);

-- Signals linked to an incident. incident_id is NULL for context signals still waiting for one.
CREATE TABLE incident_signal (
    signal_id    VARCHAR(64)  PRIMARY KEY,
    incident_id  VARCHAR(20)  REFERENCES incident (id),
    kind         VARCHAR(32)  NOT NULL,
    service      VARCHAR(64),
    title        VARCHAR(300) NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL,
    link_reason  TEXT,
    payload      JSONB        NOT NULL
);
CREATE INDEX incident_signal_incident_idx ON incident_signal (incident_id);
CREATE INDEX incident_signal_pending_idx ON incident_signal (received_at) WHERE incident_id IS NULL;

CREATE TABLE timeline_entry (
    id           BIGSERIAL    PRIMARY KEY,
    incident_id  VARCHAR(20)  NOT NULL REFERENCES incident (id),
    at           TIMESTAMPTZ  NOT NULL,
    type         VARCHAR(40)  NOT NULL,
    text         TEXT         NOT NULL,
    actor        VARCHAR(100) NOT NULL
);
CREATE INDEX timeline_incident_idx ON timeline_entry (incident_id, at);

CREATE TABLE processed_message (
    message_id    VARCHAR(64)  NOT NULL,
    consumer      VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (message_id, consumer)
);
