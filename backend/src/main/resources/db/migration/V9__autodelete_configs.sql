CREATE TABLE autodelete_configs (
    id                     BIGSERIAL PRIMARY KEY,
    guild_id               VARCHAR(32) NOT NULL,
    channel_id             VARCHAR(32) NOT NULL,
    delay_seconds          INT NOT NULL DEFAULT 60,
    ignore_role_ids        TEXT NOT NULL DEFAULT '[]',
    ignore_user_ids        TEXT NOT NULL DEFAULT '[]',
    ignore_bots            BOOLEAN NOT NULL DEFAULT TRUE,
    ignore_pinned          BOOLEAN NOT NULL DEFAULT TRUE,
    notify_user            BOOLEAN NOT NULL DEFAULT FALSE,
    notify_via             VARCHAR(16) NOT NULL DEFAULT 'channel',
    notify_message         TEXT NOT NULL DEFAULT 'Your message in {channel} was automatically deleted.',
    notify_delete_bot_msg  BOOLEAN NOT NULL DEFAULT TRUE,
    notify_delete_delay    INT NOT NULL DEFAULT 5,
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (guild_id, channel_id)
);

CREATE INDEX idx_autodelete_configs_guild ON autodelete_configs (guild_id);
