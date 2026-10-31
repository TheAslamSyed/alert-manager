CREATE TABLE kb_article (
    id                 VARCHAR(40)  PRIMARY KEY,
    kind               VARCHAR(20)  NOT NULL,   -- PAST_INCIDENT, RUNBOOK, POSTMORTEM
    ref                VARCHAR(40)  NOT NULL,   -- INC-1873, RB-PAY-03 ...
    title              VARCHAR(300) NOT NULL,
    service            VARCHAR(64),
    summary            TEXT         NOT NULL,
    root_cause         TEXT,
    resolution_action  VARCHAR(40),
    resolution         TEXT,
    mttr_minutes       INTEGER,
    body               TEXT,
    status             VARCHAR(20)  NOT NULL,   -- DRAFT, IN_REVIEW, PUBLISHED
    indexed            BOOLEAN      NOT NULL DEFAULT FALSE,
    occurred_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    published_at       TIMESTAMPTZ,
    reviewed_by        VARCHAR(100),
    search             TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(title, '') || ' ' || coalesce(summary, '') || ' ' ||
                               coalesce(root_cause, '') || ' ' || coalesce(resolution, ''))) STORED
);
CREATE INDEX kb_article_search_idx ON kb_article USING GIN (search);
CREATE INDEX kb_article_status_idx ON kb_article (status, kind);

-- Spring AI PgVectorStore table (nomic-embed-text = 768 dimensions)
CREATE TABLE vector_store (
    id         UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    content    TEXT,
    metadata   JSON,
    embedding  vector(768)
);
CREATE INDEX vector_store_embedding_idx ON vector_store USING HNSW (embedding vector_cosine_ops);

-- Facts gathered per incident (RCA, actions) so a postmortem can be written when it resolves
CREATE TABLE incident_fact (
    incident_id  VARCHAR(20)  PRIMARY KEY,
    facts        JSONB        NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE processed_message (
    message_id    VARCHAR(64)  NOT NULL,
    consumer      VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (message_id, consumer)
);
