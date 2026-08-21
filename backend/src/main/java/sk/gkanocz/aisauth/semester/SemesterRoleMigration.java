package sk.gkanocz.aisauth.semester;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * One row per (member, role change) actually applied during one step of a Setup/Plan run - either
 * a role-mapping hop ({@code roleToId} set, member went fromRole -> toRole) or a plain semester-role
 * cleanup removal ({@code roleToId} null, role just stripped, nothing granted in its place).
 *
 * <p>{@code stepIndex}/{@code stepLabel} identify which step of the run this row came from (e.g.
 * step 0 = "1zs → 2ls", step 1 = "Setup 3zs") so the rollback UI can section a multi-step Plan's
 * changes per step. Grouped further by (roleFromId, roleToId, keptFromRole) within a step, this is
 * what lets the rollback UI show "@Rola2 -> @Rola3 (12 members)" as one revertible unit - see
 * {@link SemesterRollbackService}.
 */
@Getter
@Entity
@Table(name = "semester_role_migrations")
public class SemesterRoleMigration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "migration_id", nullable = false, length = 36)
    private String migrationId;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "step_label")
    private String stepLabel;

    @Column(name = "discord_id", nullable = false, length = 32)
    private String discordId;

    @Column(name = "role_from_id", nullable = false, length = 32)
    private String roleFromId;

    /** Null for a plain cleanup-roles removal - no replacement role was granted. */
    @Column(name = "role_to_id", length = 32)
    private String roleToId;

    @Column(name = "kept_from_role", nullable = false)
    private boolean keptFromRole;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "rolled_back", nullable = false)
    private boolean rolledBack;

    protected SemesterRoleMigration() {
        // JPA
    }

    public SemesterRoleMigration(
            String guildId, String migrationId, int stepIndex, String stepLabel, String discordId,
            String roleFromId, String roleToId, boolean keptFromRole, LocalDateTime createdAt) {
        this.guildId = guildId;
        this.migrationId = migrationId;
        this.stepIndex = stepIndex;
        this.stepLabel = stepLabel;
        this.discordId = discordId;
        this.roleFromId = roleFromId;
        this.roleToId = roleToId;
        this.keptFromRole = keptFromRole;
        this.createdAt = createdAt;
        this.rolledBack = false;
    }

    public void markRolledBack() {
        this.rolledBack = true;
    }
}
