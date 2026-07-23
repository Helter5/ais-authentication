package sk.gkanocz.aisauth.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuildSettingsService {

    private final GuildSettingsRepository guildSettingsRepository;

    public GuildSettings getOrCreate(String guildId) {
        return guildSettingsRepository.findById(guildId)
                .orElseGet(() -> guildSettingsRepository.save(new GuildSettings(guildId)));
    }

    @Transactional
    public void updateField(String guildId, String field, Object value) {
        GuildSettings settings = getOrCreate(guildId);
        switch (field) {
            case "verified_role_id" -> settings.setVerifiedRoleId(asString(value));
            case "inactive_role_id" -> settings.setInactiveRoleId(asString(value));
            case "log_channel_id" -> settings.setLogChannelId(asString(value));
            case "warn_log_channel_id" -> settings.setWarnLogChannelId(asString(value));
            case "spam_trap_channel_id" -> settings.setSpamTrapChannelId(asString(value));
            case "spam_log_channel_id" -> settings.setSpamLogChannelId(asString(value));
            case "spam_delete_interval" -> settings.setSpamDeleteInterval(((Number) value).intValue());
            case "verification_enabled" -> settings.setVerificationEnabled(
                    Boolean.TRUE.equals(value) || "true".equals(value));
            case "transcript_log_channel_id" -> settings.setTranscriptLogChannelId(asString(value));
            default -> throw UnknownSettingFieldException.forField(field);
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
