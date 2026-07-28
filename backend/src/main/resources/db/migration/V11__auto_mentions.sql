CREATE TABLE auto_mentions (
    id          BIGSERIAL PRIMARY KEY,
    guild_id    VARCHAR(32) NOT NULL,
    channel_id  VARCHAR(32) NOT NULL,
    role_id     VARCHAR(32) NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (guild_id, channel_id)
);

CREATE INDEX idx_auto_mentions_guild ON auto_mentions (guild_id);
