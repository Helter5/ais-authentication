CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    category     VARCHAR(32) NOT NULL,
    action       VARCHAR(255) NOT NULL,
    guild_id     VARCHAR(32),
    guild_name   VARCHAR(255),
    channel_id   VARCHAR(32),
    channel_name VARCHAR(255),
    user_id      VARCHAR(32),
    username     VARCHAR(255),
    details      JSONB,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_category_created ON audit_logs (category, created_at DESC);
