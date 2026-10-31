CREATE TABLE investigation (
    id            VARCHAR(40)  PRIMARY KEY,
    incident_id   VARCHAR(20)  NOT NULL UNIQUE,
    status        VARCHAR(20)  NOT NULL,   -- PENDING, RUNNING, COMPLETED, FAILED
    snapshot      JSONB        NOT NULL,
    result        JSONB,
    started_at    TIMESTAMPTZ  NOT NULL,
    completed_at  TIMESTAMPTZ,
    attempts      INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE processed_message (
    message_id    VARCHAR(64)  NOT NULL,
    consumer      VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (message_id, consumer)
);
