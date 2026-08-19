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

    /** NORMAL, UNIQUE, VERIFY, DROP, or BINDING - see RoleMenuInteractionListener for what each does. */
    @Column(name = "message_type", nullable = false, length = 16)
    private String messageType;

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

    /** Optional cap on how many of this menu's roles a member may hold at once. Only meaningful for
     *  NORMAL/VERIFY message types (UNIQUE/BINDING already cap at one, DROP never adds); null = no limit. */
    @Column(name = "max_selectable")
    private Integer maxSelectable;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RoleMenuConfig() {
        // JPA
    }

    public RoleMenuConfig(
            String guildId, String channelId, String title, String description,
            String uiType, String messageType, boolean requireVerified, String options,
            String allowedRoleIds, String blockedRoleIds, Integer maxSelectable) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.title = title;
        this.description = description;
        this.uiType = uiType;
        this.messageType = messageType;
        this.requireVerified = requireVerified;
        this.options = options;
        this.allowedRoleIds = allowedRoleIds;
        this.blockedRoleIds = blockedRoleIds;
        this.maxSelectable = maxSelectable;
        this.createdAt = LocalDateTime.now();
    }

    public void update(
            String channelId, String title, String description,
            String uiType, String messageType, boolean requireVerified, String options,
            String allowedRoleIds, String blockedRoleIds, Integer maxSelectable) {
        this.channelId = channelId;
        this.title = title;
        this.description = description;
        this.uiType = uiType;
        this.messageType = messageType;
        this.requireVerified = requireVerified;
        this.options = options;
        this.allowedRoleIds = allowedRoleIds;
        this.blockedRoleIds = blockedRoleIds;
        this.maxSelectable = maxSelectable;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
