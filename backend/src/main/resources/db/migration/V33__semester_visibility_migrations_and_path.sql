-- True once a rollback has moved the tracked plan position back to the run's from_plan_id.
ALTER TABLE semester_switch_history ADD COLUMN position_reverted BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE semester_visibility_migrations (
    id                    BIGSERIAL PRIMARY KEY,
    guild_id              VARCHAR(32) NOT NULL,
    migration_id          VARCHAR(36) NOT NULL,
    step_index            INT NOT NULL DEFAULT 0,
    step_label            VARCHAR(255),
    category_id           VARCHAR(32) NOT NULL,
    category_name         VARCHAR(255),
    direction             VARCHAR(10) NOT NULL,
    everyone_view_channel BOOLEAN NOT NULL DEFAULT false,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    rolled_back           BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_semester_visibility_migrations_migration ON semester_visibility_migrations (guild_id, migration_id);
