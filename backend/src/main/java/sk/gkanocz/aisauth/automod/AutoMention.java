package sk.gkanocz.aisauth.automod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "auto_mentions", uniqueConstraints = @UniqueConstraint(columnNames = {"guild_id", "channel_id"}))
public class AutoMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    @Column(name = "role_id", nullable = false, length = 32)
    private String roleId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AutoMention() {
        // JPA
    }

    public AutoMention(String guildId, String channelId, String roleId) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.roleId = roleId;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public boolean toggle() {
        this.enabled = !this.enabled;
        return this.enabled;
    }
}
