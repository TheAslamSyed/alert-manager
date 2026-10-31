CREATE TABLE raw_signal (
    id           VARCHAR(64)  PRIMARY KEY,
    kind         VARCHAR(32)  NOT NULL,
    source       VARCHAR(64)  NOT NULL,
    service      VARCHAR(64),
    fingerprint  VARCHAR(255) NOT NULL,
    duplicate    BOOLEAN      NOT NULL,
    payload      JSONB        NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX raw_signal_received_idx ON raw_signal (received_at DESC);

CREATE TABLE signal_fingerprint (
    fingerprint  VARCHAR(255) PRIMARY KEY,
    first_seen   TIMESTAMPTZ  NOT NULL,
    last_seen    TIMESTAMPTZ  NOT NULL,
    occurrences  INTEGER      NOT NULL
);
