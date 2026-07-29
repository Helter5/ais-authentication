CREATE TABLE log_channel_subscription (
    id          BIGSERIAL PRIMARY KEY,
    guild_id    VARCHAR(32) NOT NULL,
    channel_id  VARCHAR(32) NOT NULL,
    event_type  VARCHAR(64) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (guild_id, event_type)
);

CREATE INDEX idx_log_channel_subscription_guild ON log_channel_subscription (guild_id);

-- Backfill from the old fixed guild_settings columns. log_channel_id's only real producer today
-- is the wipe's inactive-user-removed embed, despite being labelled "Verification Log" in the UI.
INSERT INTO log_channel_subscription (guild_id, channel_id, event_type)
SELECT guild_id, log_channel_id, 'WIPE_INACTIVE_USER_REMOVED' FROM guild_settings WHERE log_channel_id IS NOT NULL;

INSERT INTO log_channel_subscription (guild_id, channel_id, event_type)
SELECT guild_id, warn_log_channel_id, event_type
FROM guild_settings, (VALUES ('WARN_ISSUED'), ('WARN_REMOVED'), ('WARNS_CLEARED'), ('WARN_THRESHOLD_ACTION')) AS t(event_type)
WHERE warn_log_channel_id IS NOT NULL;

INSERT INTO log_channel_subscription (guild_id, channel_id, event_type)
SELECT guild_id, spam_log_channel_id, 'HACKED_ACCOUNT_TRAP_TRIGGERED' FROM guild_settings WHERE spam_log_channel_id IS NOT NULL;

INSERT INTO log_channel_subscription (guild_id, channel_id, event_type)
SELECT guild_id, transcript_log_channel_id, 'TICKET_TRANSCRIPT_SAVED' FROM guild_settings WHERE transcript_log_channel_id IS NOT NULL;

ALTER TABLE guild_settings
    DROP COLUMN log_channel_id,
    DROP COLUMN warn_log_channel_id,
    DROP COLUMN spam_log_channel_id,
    DROP COLUMN transcript_log_channel_id;
