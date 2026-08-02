CREATE TABLE role_menu_configs (
    id               BIGSERIAL PRIMARY KEY,
    guild_id         VARCHAR(32)  NOT NULL,
    channel_id       VARCHAR(32)  NOT NULL,
    message_id       VARCHAR(32),
    title            VARCHAR(256) NOT NULL,
    description      TEXT         NOT NULL,
    ui_type          VARCHAR(16)  NOT NULL,
    selection_mode   VARCHAR(16)  NOT NULL,
    require_verified BOOLEAN      NOT NULL DEFAULT false,
    options          JSONB        NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_role_menu_configs_guild_id ON role_menu_configs (guild_id);
