ALTER TABLE role_menu_configs ADD COLUMN message_type VARCHAR(16);

UPDATE role_menu_configs SET message_type = CASE WHEN selection_mode = 'SINGLE' THEN 'UNIQUE' ELSE 'NORMAL' END;

ALTER TABLE role_menu_configs ALTER COLUMN message_type SET NOT NULL;
ALTER TABLE role_menu_configs DROP COLUMN selection_mode;
