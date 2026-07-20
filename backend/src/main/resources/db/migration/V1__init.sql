CREATE TABLE verification_codes (
    id          BIGSERIAL PRIMARY KEY,
    discord_id  VARCHAR(32)  NOT NULL,
    guild_id    VARCHAR(32)  NOT NULL,
    code        VARCHAR(16)  NOT NULL,
    email       VARCHAR(255) NOT NULL,
    ais_id      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at  TIMESTAMP    NOT NULL,
    UNIQUE (discord_id, guild_id)
);

CREATE TABLE verified_users (
    id           BIGSERIAL PRIMARY KEY,
    ais_id       VARCHAR(32)  NOT NULL,
    discord_id   VARCHAR(32)  NOT NULL,
    guild_id     VARCHAR(32)  NOT NULL,
    email        VARCHAR(255) NOT NULL,
    verified_at  TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (discord_id, guild_id),
    UNIQUE (ais_id, guild_id)
);
