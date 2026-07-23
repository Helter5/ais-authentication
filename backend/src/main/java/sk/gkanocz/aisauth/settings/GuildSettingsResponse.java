package sk.gkanocz.aisauth.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GuildSettingsResponse(
        @JsonProperty("verified_role_id") String verifiedRoleId,
        @JsonProperty("inactive_role_id") String inactiveRoleId,
        @JsonProperty("log_channel_id") String logChannelId,
        @JsonProperty("warn_log_channel_id") String warnLogChannelId,
        @JsonProperty("spam_trap_channel_id") String spamTrapChannelId,
        @JsonProperty("spam_log_channel_id") String spamLogChannelId,
        @JsonProperty("spam_delete_interval") int spamDeleteInterval,
        @JsonProperty("verification_enabled") boolean verificationEnabled,
        @JsonProperty("transcript_log_channel_id") String transcriptLogChannelId,
        @JsonProperty("log_channel_health") LogChannelHealth logChannelHealth) {

    public record LogChannelHealth(
            LogChannelHealthEntry verification,
            LogChannelHealthEntry moderation,
            LogChannelHealthEntry automod,
            LogChannelHealthEntry transcript) {
    }

    public record LogChannelHealthEntry(
            boolean ok,
            boolean configured,
            String channelId,
            String channelName,
            String error) {
    }
}
