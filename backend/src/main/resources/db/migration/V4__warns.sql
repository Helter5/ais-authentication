CREATE TABLE warns (
    id           BIGSERIAL PRIMARY KEY,
    guild_id     VARCHAR(32) NOT NULL,
    discord_id   VARCHAR(32) NOT NULL,
    moderator_id VARCHAR(32) NOT NULL,
    reason       VARCHAR(1000) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE warn_thresholds (
    id         BIGSERIAL PRIMARY KEY,
    guild_id   VARCHAR(32) NOT NULL,
    warn_limit INTEGER NOT NULL,
    action     VARCHAR(16) NOT NULL DEFAULT 'none',
    UNIQUE (guild_id, warn_limit)
);
