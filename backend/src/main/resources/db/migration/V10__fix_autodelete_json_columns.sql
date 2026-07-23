ALTER TABLE autodelete_configs
    ALTER COLUMN ignore_role_ids DROP DEFAULT,
    ALTER COLUMN ignore_user_ids DROP DEFAULT,
    ALTER COLUMN ignore_role_ids TYPE JSONB USING ignore_role_ids::jsonb,
    ALTER COLUMN ignore_user_ids TYPE JSONB USING ignore_user_ids::jsonb,
    ALTER COLUMN ignore_role_ids SET DEFAULT '[]'::jsonb,
    ALTER COLUMN ignore_user_ids SET DEFAULT '[]'::jsonb;
