ALTER TABLE guild_settings ADD COLUMN ticket_retention_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE guild_settings ADD COLUMN ticket_retention_days INTEGER NOT NULL DEFAULT 90;
