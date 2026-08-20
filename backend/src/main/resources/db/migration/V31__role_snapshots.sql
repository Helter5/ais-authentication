CREATE TABLE role_snapshots (
    id          BIGSERIAL PRIMARY KEY,
    guild_id    VARCHAR(32) NOT NULL,
    discord_id  VARCHAR(32) NOT NULL,
    role_ids    JSONB NOT NULL DEFAULT '[]'::jsonb,
    left_at     TIMESTAMP NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    CONSTRAINT uq_role_snapshots_guild_discord UNIQUE (guild_id, discord_id)
);

CREATE INDEX idx_role_snapshots_expires_at ON role_snapshots (expires_at);
