package sk.gkanocz.aisauth.automod;

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

@Getter
@Entity
@Table(name = "autodelete_configs", uniqueConstraints = @UniqueConstraint(columnNames = {"guild_id", "channel_id"}))
public class AutoDeleteConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    @Column(name = "delay_seconds", nullable = false)
    private int delaySeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ignore_role_ids", nullable = false)
    private String ignoreRoleIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ignore_user_ids", nullable = false)
    private String ignoreUserIds;

    @Column(name = "ignore_bots", nullable = false)
    private boolean ignoreBots;

    @Column(name = "ignore_pinned", nullable = false)
    private boolean ignorePinned;

    @Column(name = "notify_user", nullable = false)
    private boolean notifyUser;

    @Column(name = "notify_via", nullable = false, length = 16)
    private String notifyVia;

    @Column(name = "notify_message", nullable = false)
    private String notifyMessage;

    @Column(name = "notify_delete_bot_msg", nullable = false)
    private boolean notifyDeleteBotMsg;

    @Column(name = "notify_delete_delay", nullable = false)
    private int notifyDeleteDelay;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AutoDeleteConfig() {
        // JPA
    }

    public AutoDeleteConfig(
            String guildId, String channelId, int delaySeconds, String ignoreRoleIds, String ignoreUserIds,
            boolean ignoreBots, boolean ignorePinned, boolean notifyUser, String notifyVia, String notifyMessage,
            boolean notifyDeleteBotMsg, int notifyDeleteDelay) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.delaySeconds = delaySeconds;
        this.ignoreRoleIds = ignoreRoleIds;
        this.ignoreUserIds = ignoreUserIds;
        this.ignoreBots = ignoreBots;
        this.ignorePinned = ignorePinned;
        this.notifyUser = notifyUser;
        this.notifyVia = notifyVia;
        this.notifyMessage = notifyMessage;
        this.notifyDeleteBotMsg = notifyDeleteBotMsg;
        this.notifyDeleteDelay = notifyDeleteDelay;
        this.createdAt = LocalDateTime.now();
    }

    public void update(
            String channelId, int delaySeconds, String ignoreRoleIds, String ignoreUserIds,
            boolean ignoreBots, boolean ignorePinned, boolean notifyUser, String notifyVia, String notifyMessage,
            boolean notifyDeleteBotMsg, int notifyDeleteDelay) {
        this.channelId = channelId;
        this.delaySeconds = delaySeconds;
        this.ignoreRoleIds = ignoreRoleIds;
        this.ignoreUserIds = ignoreUserIds;
        this.ignoreBots = ignoreBots;
        this.ignorePinned = ignorePinned;
        this.notifyUser = notifyUser;
        this.notifyVia = notifyVia;
        this.notifyMessage = notifyMessage;
        this.notifyDeleteBotMsg = notifyDeleteBotMsg;
        this.notifyDeleteDelay = notifyDeleteDelay;
    }
}
