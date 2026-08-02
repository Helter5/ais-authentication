ALTER TABLE guild_settings
    ADD COLUMN last_sync_at TIMESTAMP,
    ADD COLUMN last_sync_checked_count INTEGER,
    ADD COLUMN last_sync_removed_count INTEGER;
