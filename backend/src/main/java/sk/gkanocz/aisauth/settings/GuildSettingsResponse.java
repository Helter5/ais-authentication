package sk.gkanocz.aisauth.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GuildSettingsResponse(
        @JsonProperty("verified_role_id") String verifiedRoleId,
        @JsonProperty("inactive_role_id") String inactiveRoleId,
        @JsonProperty("spam_trap_channel_id") String spamTrapChannelId,
        @JsonProperty("spam_delete_interval") int spamDeleteInterval,
        @JsonProperty("verification_enabled") boolean verificationEnabled,
        @JsonProperty("ticket_retention_enabled") boolean ticketRetentionEnabled,
        @JsonProperty("ticket_retention_days") int ticketRetentionDays) {
}
