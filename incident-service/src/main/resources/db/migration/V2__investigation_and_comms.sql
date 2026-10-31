-- Read models for the RCA dashboard and the communications screen.
CREATE TABLE incident_finding (
    id           BIGSERIAL    PRIMARY KEY,
    incident_id  VARCHAR(20)  NOT NULL REFERENCES incident (id),
    agent        VARCHAR(80)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    summary      TEXT,
    payload      JSONB        NOT NULL,
    at           TIMESTAMPTZ  NOT NULL
);
CREATE INDEX incident_finding_incident_idx ON incident_finding (incident_id, id);

CREATE TABLE incident_rca (
    incident_id  VARCHAR(20)  PRIMARY KEY REFERENCES incident (id),
    payload      JSONB        NOT NULL,
    plan         JSONB,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE SEQUENCE comms_number_seq START WITH 301;

-- FR-07: drafts wait for a human to approve and send them.
CREATE TABLE comms_draft (
    id           VARCHAR(20)  PRIMARY KEY,
    incident_id  VARCHAR(20)  NOT NULL REFERENCES incident (id),
    trigger      VARCHAR(40)  NOT NULL,
    audience     VARCHAR(20)  NOT NULL,
    channel      VARCHAR(80)  NOT NULL,
    subject      VARCHAR(300) NOT NULL,
    body         TEXT         NOT NULL,
    method       VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,  -- DRAFT, SENT, SUPERSEDED
    edited_by    VARCHAR(100),
    sent_by      VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL,
    sent_at      TIMESTAMPTZ
);
CREATE INDEX comms_draft_incident_idx ON comms_draft (incident_id, created_at);
