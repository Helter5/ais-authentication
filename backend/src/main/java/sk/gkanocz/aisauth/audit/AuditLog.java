package sk.gkanocz.aisauth.audit;

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
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false)
    private String action;

    @Column(name = "guild_id", length = 32)
    private String guildId;

    @Column(name = "guild_name")
    private String guildName;

    @Column(name = "channel_id", length = 32)
    private String channelId;

    @Column(name = "channel_name")
    private String channelName;

    @Column(name = "user_id", length = 32)
    private String userId;

    private String username;

    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(String category, String action, String guildId, String guildName,
                     String channelId, String channelName, String userId, String username,
                     String details) {
        this.category = category;
        this.action = action;
        this.guildId = guildId;
        this.guildName = guildName;
        this.channelId = channelId;
        this.channelName = channelName;
        this.userId = userId;
        this.username = username;
        this.details = details;
        this.createdAt = LocalDateTime.now();
    }
}
