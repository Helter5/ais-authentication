package sk.gkanocz.aisauth.thesiscounter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "thesis_counter_configs")
public class ThesisCounterConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    /** BP or DP. */
    @Column(nullable = false, length = 2)
    private String label;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** Captured once at creation, so the channel name can be restored when the counter is removed. */
    @Column(name = "original_channel_name", nullable = false, length = 100)
    private String originalChannelName;

    /** Template for the channel name while days remain, e.g. "{days}-{daysWord}-do-{label}". Null = use the service default. */
    @Column(name = "name_format", length = 100)
    private String nameFormat;

    /** Template used once the target date has arrived, e.g. "dnes-{label}". Null = use the service default. */
    @Column(name = "today_format", length = 100)
    private String todayFormat;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ThesisCounterConfig() {
        // JPA
    }

    public ThesisCounterConfig(
            String guildId, String channelId, String label, LocalDate targetDate, String originalChannelName,
            String nameFormat, String todayFormat) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.label = label;
        this.targetDate = targetDate;
        this.originalChannelName = originalChannelName;
        this.nameFormat = nameFormat;
        this.todayFormat = todayFormat;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public void update(String label, LocalDate targetDate, boolean active, String nameFormat, String todayFormat) {
        this.label = label;
        this.targetDate = targetDate;
        this.active = active;
        this.nameFormat = nameFormat;
        this.todayFormat = todayFormat;
    }

    /** Re-points this counter at a different room. {@code originalChannelName} must be the new
     *  channel's current name, captured before it gets renamed, so a future remove can restore it. */
    public void moveToChannel(String channelId, String originalChannelName) {
        this.channelId = channelId;
        this.originalChannelName = originalChannelName;
    }

    public void deactivate() {
        this.active = false;
    }
}
