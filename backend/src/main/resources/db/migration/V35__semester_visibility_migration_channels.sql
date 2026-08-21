ALTER TABLE semester_visibility_migrations
    ADD COLUMN is_channel BOOLEAN NOT NULL DEFAULT FALSE;
