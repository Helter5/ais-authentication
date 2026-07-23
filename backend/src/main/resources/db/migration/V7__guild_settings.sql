CREATE TABLE guild_settings (
    guild_id                  VARCHAR(32) PRIMARY KEY,
    verified_role_id          VARCHAR(32),
    inactive_role_id          VARCHAR(32),
    log_channel_id            VARCHAR(32),
    warn_log_channel_id       VARCHAR(32),
    spam_trap_channel_id      VARCHAR(32),
    spam_log_channel_id       VARCHAR(32),
    spam_delete_interval      INTEGER NOT NULL DEFAULT 60,
    verification_enabled      BOOLEAN NOT NULL DEFAULT true,
    transcript_log_channel_id VARCHAR(32),
    created_at                TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP NOT NULL DEFAULT now()
);
