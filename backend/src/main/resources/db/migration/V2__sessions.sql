CREATE TABLE sessions (
    jti         VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);
