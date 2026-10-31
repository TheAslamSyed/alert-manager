CREATE SEQUENCE action_number_seq START WITH 5512;

CREATE TABLE remediation_action (
    id                VARCHAR(20)  PRIMARY KEY,
    incident_id       VARCHAR(20)  NOT NULL,
    investigation_id  VARCHAR(40),
    action            VARCHAR(40)  NOT NULL,
    target            VARCHAR(64)  NOT NULL,
    risk              VARCHAR(10)  NOT NULL,
    approvals_needed  INTEGER      NOT NULL,
    description       TEXT         NOT NULL,
    reason            TEXT,
    status            VARCHAR(24)  NOT NULL,
    proposed_by       VARCHAR(100) NOT NULL,
    submitted_by      VARCHAR(100),
    approvals         JSONB        NOT NULL,
    rejected_by       VARCHAR(100),
    decision_reason   TEXT,
    signature         VARCHAR(100),
    outcome           VARCHAR(24),
    result_message    TEXT,
    verification      TEXT,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX remediation_action_incident_idx ON remediation_action (incident_id);
CREATE INDEX remediation_action_status_idx ON remediation_action (status);

CREATE TABLE remediation_plan (
    incident_id       VARCHAR(20)  PRIMARY KEY,
    root_cause        TEXT,
    preventive        JSONB        NOT NULL,
    close_as_false_positive BOOLEAN NOT NULL,
    rationale         TEXT,
    created_at        TIMESTAMPTZ  NOT NULL
);

CREATE TABLE processed_message (
    message_id    VARCHAR(64)  NOT NULL,
    consumer      VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (message_id, consumer)
);
