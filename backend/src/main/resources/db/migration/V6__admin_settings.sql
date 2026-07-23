CREATE TABLE admin_settings (
    key        VARCHAR(255) PRIMARY KEY,
    value      JSONB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
