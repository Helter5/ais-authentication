package sk.gkanocz.aisauth.rolemenu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "role_menu_configs")
public class RoleMenuConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    @Column(name = "message_id", length = 32)
    private String messageId;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(nullable = false)
    private String description;

    /** BUTTONS or SELECT_MENU. */
    @Column(name = "ui_type", nullable = false, length = 16)
    private String uiType;

    /** SINGLE or MULTI. */
    @Column(name = "selection_mode", nullable = false, length = 16)
    private String selectionMode;

    @Column(name = "require_verified", nullable = false)
    private boolean requireVerified;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String options;

    /** Roles allowed to use this menu - empty means everyone (subject to blockedRoleIds/requireVerified). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_role_ids", nullable = false)
    private String allowedRoleIds;

    /** Roles blocked from using this menu - takes priority over allowedRoleIds/requireVerified. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocked_role_ids", nullable = false)
    private String blockedRoleIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RoleMenuConfig() {
        // JPA
    }

    public RoleMenuConfig(
            String guildId, String channelId, String title, String description,
            String uiType, String selectionMode, boolean requireVerified, String options,
            String allowedRoleIds, String blockedRoleIds) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.title = title;
        this.description = description;
        this.uiType = uiType;
        this.selectionMode = selectionMode;
        this.requireVerified = requireVerified;
        this.options = options;
        this.allowedRoleIds = allowedRoleIds;
        this.blockedRoleIds = blockedRoleIds;
        this.createdAt = LocalDateTime.now();
    }

    public void update(
            String channelId, String title, String description,
            String uiType, String selectionMode, boolean requireVerified, String options,
            String allowedRoleIds, String blockedRoleIds) {
        this.channelId = channelId;
        this.title = title;
        this.description = description;
        this.uiType = uiType;
        this.selectionMode = selectionMode;
        this.requireVerified = requireVerified;
        this.options = options;
        this.allowedRoleIds = allowedRoleIds;
        this.blockedRoleIds = blockedRoleIds;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
