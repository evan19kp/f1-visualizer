CREATE TABLE race_sessions (
    session_key   BIGINT PRIMARY KEY,
    meeting_key   BIGINT NOT NULL,
    session_name  VARCHAR(100),
    circuit_name  VARCHAR(100),
    date_start    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
