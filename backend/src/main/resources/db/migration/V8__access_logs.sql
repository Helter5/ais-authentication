CREATE TABLE access_logs (
    id         BIGSERIAL PRIMARY KEY,
    discord_id VARCHAR(32) NOT NULL,
    username   VARCHAR(255) NOT NULL,
    action     VARCHAR(64) NOT NULL,
    guild_id   VARCHAR(32) NOT NULL,
    ip         VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_access_logs_guild_created ON access_logs (guild_id, created_at DESC);
