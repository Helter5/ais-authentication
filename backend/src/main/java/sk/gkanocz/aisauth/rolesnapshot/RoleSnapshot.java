package sk.gkanocz.aisauth.rolesnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One row per (guild, member) - captures the trackable roles a member had at the moment they
 * left/were kicked/were banned, so {@code /refresh} can hand them back if the member returns
 * within the retention window. Always at most one row per member: a repeat departure before the
 * previous snapshot was ever consumed overwrites it rather than piling up history, since only the
 * most recent departure's roles are ever meaningful to restore.
 */
@Getter
@Entity
@Table(name = "role_snapshots", uniqueConstraints = @UniqueConstraint(columnNames = {"guild_id", "discord_id"}))
public class RoleSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "discord_id", nullable = false, length = 32)
    private String discordId;

    /** JSON array of Discord role ID strings - already filtered to trackable roles at snapshot time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "role_ids", nullable = false)
    private String roleIds;

    @Column(name = "left_at", nullable = false)
    private LocalDateTime leftAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected RoleSnapshot() {
        // JPA
    }

    public RoleSnapshot(String guildId, String discordId, String roleIds, LocalDateTime leftAt, LocalDateTime expiresAt) {
        this.guildId = guildId;
        this.discordId = discordId;
        this.roleIds = roleIds;
        this.leftAt = leftAt;
        this.expiresAt = expiresAt;
    }

    public void overwrite(String roleIds, LocalDateTime leftAt, LocalDateTime expiresAt) {
        this.roleIds = roleIds;
        this.leftAt = leftAt;
        this.expiresAt = expiresAt;
    }
}
