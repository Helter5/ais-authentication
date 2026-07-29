package sk.gkanocz.aisauth.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "log_channel_subscription", uniqueConstraints = @UniqueConstraint(columnNames = {"guild_id", "event_type"}))
public class LogChannelSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", length = 32, nullable = false)
    private String guildId;

    @Column(name = "channel_id", length = 32, nullable = false)
    private String channelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 64, nullable = false)
    private LogEventType eventType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected LogChannelSubscription() {
        // JPA
    }

    public LogChannelSubscription(String guildId, String channelId, LogEventType eventType) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.eventType = eventType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
        this.updatedAt = LocalDateTime.now();
    }
}
