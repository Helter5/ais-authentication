package sk.gkanocz.aisauth.semester;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * One row per admin-triggered operation that touched channels/roles - either a standalone Setup run
 * ({@code operationType = "setup"}) or a Plan run ({@code operationType = "plan"}, a named bundle of
 * switch/setup steps executed together, see {@link SwitchSemesterSettings.SwitchPlan}).
 *
 * <p>{@code migrationId} ties this row to every {@link SemesterRoleMigration} /
 * {@link SemesterVisibilityMigration} row the run wrote, so "roll back this run" can find exactly
 * which changes belong to it, sectioned per plan step via those rows' own stepIndex/stepLabel.
 *
 * <p>{@code fromPlanId} is the plan-path position this run started from - only meaningful for
 * {@code operationType = "plan"} - and is what a rollback's position-revert moves {@code current_plan}
 * back to. {@code rolledBack} only ever flips true once every row plus the position (when
 * applicable) is reverted - see {@link SemesterRollbackService}.
 */
@Getter
@Entity
@Table(name = "semester_switch_history", uniqueConstraints = @UniqueConstraint(columnNames = "migration_id"))
public class SemesterSwitchHistory {

    public static final String TYPE_SETUP = "setup";
    public static final String TYPE_PLAN = "plan";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "migration_id", nullable = false, length = 36)
    private String migrationId;

    @Column(name = "operation_type", nullable = false, length = 10)
    private String operationType;

    /** Retired bare single-pair switch fields - unused by new rows, kept nullable for the shape. */
    @Column(name = "old_name")
    private String oldName;

    @Column(name = "new_name")
    private String newName;

    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "from_plan_id", length = 64)
    private String fromPlanId;

    @Column(name = "actor_id", length = 32)
    private String actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Best-effort "fully reverted" cache, recomputed and rewritten after every rollback call (see
     * SemesterRollbackService) rather than trusted as the sole source of truth.
     */
    @Column(name = "rolled_back", nullable = false)
    private boolean rolledBack;

    /** True once a rollback has moved the tracked plan position back to {@link #fromPlanId}. */
    @Column(name = "position_reverted", nullable = false)
    private boolean positionReverted;

    /**
     * Who most recently ran a (partial or full) rollback against this run - null until the first
     * rollback. A run selectively rolled back more than once just tracks the latest rollback actor,
     * not a full history of every partial rollback.
     */
    @Column(name = "rolled_back_by_actor_id", length = 32)
    private String rolledBackByActorId;

    @Column(name = "rolled_back_by_actor_name")
    private String rolledBackByActorName;

    protected SemesterSwitchHistory() {
        // JPA
    }

    private SemesterSwitchHistory(
            String guildId, String migrationId, String operationType, String planId, String planName,
            String fromPlanId, String actorId, String actorName, LocalDateTime createdAt) {
        this.guildId = guildId;
        this.migrationId = migrationId;
        this.operationType = operationType;
        this.planId = planId;
        this.planName = planName;
        this.fromPlanId = fromPlanId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.createdAt = createdAt;
        this.rolledBack = false;
        this.positionReverted = false;
    }

    public static SemesterSwitchHistory forSetup(
            String guildId, String migrationId, String semesterName, String actorId, String actorName, LocalDateTime createdAt) {
        SemesterSwitchHistory history = new SemesterSwitchHistory(
                guildId, migrationId, TYPE_SETUP, null, null, null, actorId, actorName, createdAt);
        history.newName = semesterName;
        return history;
    }

    public static SemesterSwitchHistory forPlan(
            String guildId, String migrationId, String planId, String planName, String fromPlanId,
            String actorId, String actorName, LocalDateTime createdAt) {
        return new SemesterSwitchHistory(guildId, migrationId, TYPE_PLAN, planId, planName, fromPlanId, actorId, actorName, createdAt);
    }

    public void setRolledBack(boolean rolledBack) {
        this.rolledBack = rolledBack;
    }

    public void recordRollbackActor(String actorId, String actorName) {
        this.rolledBackByActorId = actorId;
        this.rolledBackByActorName = actorName;
    }

    public void markPositionReverted() {
        this.positionReverted = true;
    }
}
