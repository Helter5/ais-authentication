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
 * One row per category (or, when {@code isChannel} is true, one plan-wide additional channel - see
 * {@link SwitchSemesterSettings.AdditionalChannel}) whose visibility one step/run actually changed -
 * either {@code "hide"} or {@code "show"}. {@code everyoneViewChannel} snapshots the @everyone value
 * applied at that time, so a later rollback reproduces exactly what was applied then even if the
 * semester's config (or the plan's additional-channels list) has since changed.
 *
 * <p>{@code categoryId}/{@code categoryName} double as {@code channelId}/{@code channelName} for an
 * {@code isChannel} row - reverting one calls {@link SemesterVisibilityService#applyChannels} instead
 * of {@link SemesterVisibilityService#apply}, since a plain channel isn't a category and has no
 * child channels to cascade into - see {@link SemesterRollbackService}.
 *
 * <p>{@code stepIndex}/{@code stepLabel} identify which step of the run this row came from, same as
 * {@link SemesterRoleMigration} - lets the rollback UI offer per-row revert sectioned per step
 * alongside per-role-group revert.
 */
@Getter
@Entity
@Table(name = "semester_visibility_migrations")
public class SemesterVisibilityMigration {

    public static final String DIRECTION_HIDE = "hide";
    public static final String DIRECTION_SHOW = "show";

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

    @Column(name = "category_id", nullable = false, length = 32)
    private String categoryId;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "everyone_view_channel", nullable = false)
    private boolean everyoneViewChannel;

    /** True when categoryId/categoryName actually hold a plan-wide additional channel, not a category. */
    @Column(name = "is_channel", nullable = false)
    private boolean channel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "rolled_back", nullable = false)
    private boolean rolledBack;

    protected SemesterVisibilityMigration() {
        // JPA
    }

    public SemesterVisibilityMigration(
            String guildId, String migrationId, int stepIndex, String stepLabel, String categoryId, String categoryName,
            String direction, boolean everyoneViewChannel, boolean channel, LocalDateTime createdAt) {
        this.guildId = guildId;
        this.migrationId = migrationId;
        this.stepIndex = stepIndex;
        this.stepLabel = stepLabel;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.direction = direction;
        this.everyoneViewChannel = everyoneViewChannel;
        this.channel = channel;
        this.createdAt = createdAt;
        this.rolledBack = false;
    }

    public boolean isHide() {
        return DIRECTION_HIDE.equals(direction);
    }

    public void markRolledBack() {
        this.rolledBack = true;
    }
}
