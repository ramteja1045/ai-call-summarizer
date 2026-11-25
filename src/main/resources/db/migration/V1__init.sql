CREATE TABLE calls (
    id              UUID PRIMARY KEY,
    agent_name      VARCHAR(100) NOT NULL,
    transcript      TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    summary         TEXT,
    score           INT CHECK (score BETWEEN 0 AND 100),
    analysis_json   TEXT,
    failure_reason  VARCHAR(255),
    attempts        INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    analyzed_at     TIMESTAMPTZ
);

CREATE INDEX idx_calls_agent_created ON calls (agent_name, created_at DESC);
CREATE INDEX idx_calls_failed ON calls (status) WHERE status = 'FAILED';
