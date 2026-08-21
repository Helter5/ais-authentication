ALTER TABLE semester_switch_history
    ADD COLUMN rolled_back_by_actor_id VARCHAR(32),
    ADD COLUMN rolled_back_by_actor_name VARCHAR(255);
