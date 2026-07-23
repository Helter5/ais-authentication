CREATE TABLE incident_tickets (
    channel_id  VARCHAR(32) PRIMARY KEY,
    guild_id    VARCHAR(32) NOT NULL,
    user_id     VARCHAR(32) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'open',
    closed_by   VARCHAR(32),
    closed_at   TIMESTAMP,
    transcript  JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
