-- Records one row per admin-triggered operation: a standalone Setup run, or a Plan run (a named
-- bundle of switch/setup steps executed together). old_name/new_name only apply to the retired
-- bare single-pair switch shape and are unused going forward, kept nullable for that history.
CREATE TABLE semester_switch_history (
    id             BIGSERIAL PRIMARY KEY,
    guild_id       VARCHAR(32) NOT NULL,
    migration_id   VARCHAR(36) NOT NULL,
    operation_type VARCHAR(10) NOT NULL,
    old_name       VARCHAR(255),
    new_name       VARCHAR(255),
    plan_id        VARCHAR(64),
    plan_name      VARCHAR(255),
    from_plan_id   VARCHAR(64),
    actor_id       VARCHAR(32),
    actor_name     VARCHAR(255),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    rolled_back    BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_semester_switch_history_migration_id UNIQUE (migration_id)
);

CREATE INDEX idx_semester_switch_history_guild ON semester_switch_history (guild_id, created_at DESC);

-- step_index/step_label identify which step of a (possibly multi-step) Plan run this row belongs
-- to - 0/null for a standalone Setup run, so the rollback UI can section a multi-step Plan's
-- changes per step instead of dumping everything into one flat list.
CREATE TABLE semester_role_migrations (
    id             BIGSERIAL PRIMARY KEY,
    guild_id       VARCHAR(32) NOT NULL,
    migration_id   VARCHAR(36) NOT NULL,
    step_index     INT NOT NULL DEFAULT 0,
    step_label     VARCHAR(255),
    discord_id     VARCHAR(32) NOT NULL,
    role_from_id   VARCHAR(32) NOT NULL,
    role_to_id     VARCHAR(32),
    kept_from_role BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    rolled_back    BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_semester_role_migrations_migration ON semester_role_migrations (guild_id, migration_id);
