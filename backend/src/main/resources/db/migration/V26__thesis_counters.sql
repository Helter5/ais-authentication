CREATE TABLE thesis_counter_configs (
    id                    BIGSERIAL PRIMARY KEY,
    guild_id              VARCHAR(32)  NOT NULL,
    channel_id            VARCHAR(32)  NOT NULL,
    label                 VARCHAR(2)   NOT NULL,
    target_date           DATE         NOT NULL,
    original_channel_name VARCHAR(100) NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_thesis_counter_configs_guild_id ON thesis_counter_configs (guild_id);
CREATE UNIQUE INDEX idx_thesis_counter_configs_guild_channel ON thesis_counter_configs (guild_id, channel_id);
