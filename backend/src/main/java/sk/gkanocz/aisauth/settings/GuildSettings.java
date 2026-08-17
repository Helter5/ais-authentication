package sk.gkanocz.aisauth.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "guild_settings")
public class GuildSettings {

    @Id
    @Column(name = "guild_id", length = 32)
    private String guildId;

    @Column(name = "verified_role_id", length = 32)
    private String verifiedRoleId;

    @Column(name = "inactive_role_id", length = 32)
    private String inactiveRoleId;

    @Column(name = "spam_trap_channel_id", length = 32)
    private String spamTrapChannelId;

    @Column(name = "spam_delete_interval", nullable = false)
    private int spamDeleteInterval = 60;

    @Column(name = "verification_enabled", nullable = false)
    private boolean verificationEnabled = true;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "last_sync_checked_count")
    private Integer lastSyncCheckedCount;

    @Column(name = "last_sync_removed_count")
    private Integer lastSyncRemovedCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected GuildSettings() {
        // JPA
    }

    public GuildSettings(String guildId) {
        this.guildId = guildId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void setVerifiedRoleId(String verifiedRoleId) {
        this.verifiedRoleId = verifiedRoleId;
        touch();
    }

    public void setInactiveRoleId(String inactiveRoleId) {
        this.inactiveRoleId = inactiveRoleId;
        touch();
    }

    public void setSpamTrapChannelId(String spamTrapChannelId) {
        this.spamTrapChannelId = spamTrapChannelId;
        touch();
    }

    public void setSpamDeleteInterval(int spamDeleteInterval) {
        this.spamDeleteInterval = spamDeleteInterval;
        touch();
    }

    public void setVerificationEnabled(boolean verificationEnabled) {
        this.verificationEnabled = verificationEnabled;
        touch();
    }

    /**
     * Records a database-sync run without touching updatedAt - that timestamp reflects admin
     * changes to settings, not this system-driven housekeeping pass.
     */
    public void recordDatabaseSync(int checkedCount, int removedCount) {
        this.lastSyncAt = LocalDateTime.now();
        this.lastSyncCheckedCount = checkedCount;
        this.lastSyncRemovedCount = removedCount;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
