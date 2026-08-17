CREATE TABLE refresh_tokens (
    token       VARCHAR(64)   PRIMARY KEY,
    user_id     VARCHAR(32)   NOT NULL,
    username    VARCHAR(64)   NOT NULL,
    avatar      VARCHAR(64),
    super_admin BOOLEAN       NOT NULL,
    guild_ids   VARCHAR(2000) NOT NULL DEFAULT '',
    expires_at  TIMESTAMP     NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT now()
);
