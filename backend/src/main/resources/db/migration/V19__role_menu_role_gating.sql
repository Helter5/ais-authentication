ALTER TABLE role_menu_configs
    ADD COLUMN allowed_role_ids JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN blocked_role_ids JSONB NOT NULL DEFAULT '[]';
