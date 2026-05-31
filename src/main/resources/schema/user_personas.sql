CREATE TABLE IF NOT EXISTS user_personas (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ai_context_json TEXT,
    display_summary VARCHAR(500),
    analysis_count_used INTEGER DEFAULT 0,
    signal_version INTEGER DEFAULT 1,
    updated_at TIMESTAMP,
    last_refreshed_at TIMESTAMP
);
