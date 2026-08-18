CREATE TABLE subject_role_requests (
    id           BIGSERIAL PRIMARY KEY,
    guild_id     VARCHAR(32) NOT NULL,
    discord_id   VARCHAR(32) NOT NULL,
    subject_code VARCHAR(64) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    decided_by   VARCHAR(32),
    decided_at   TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_subject_role_requests_guild_user ON subject_role_requests (guild_id, discord_id);
