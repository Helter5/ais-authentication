package sk.gkanocz.aisauth.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;

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
        applyField(getOrCreate(guildId), field, value);
    }

    /**
     * Applies every field in one load-mutate-save, so a caller updating several fields at once
     * (e.g. the dashboard's log-channels form) doesn't fire N concurrent single-field PATCHes
     * against this same row - Hibernate writes every mapped column on flush, so N racing
     * transactions each loading their own stale snapshot would otherwise silently clobber each
     * other's changes, leaving only the last writer's single field actually saved.
     */
    @Transactional
    public void updateFields(String guildId, Map<String, Object> fields) {
        GuildSettings settings = getOrCreate(guildId);
        fields.forEach((field, value) -> applyField(settings, field, value));
    }

    private void applyField(GuildSettings settings, String field, Object value) {
        switch (field) {
            case "verified_role_id" -> settings.setVerifiedRoleId(asString(value));
            case "inactive_role_id" -> settings.setInactiveRoleId(asString(value));
            case "spam_trap_channel_id" -> settings.setSpamTrapChannelId(asString(value));
            case "spam_delete_interval" -> settings.setSpamDeleteInterval(((Number) value).intValue());
            case "verification_enabled" -> settings.setVerificationEnabled(
                    Boolean.TRUE.equals(value) || "true".equals(value));
            case "timezone" -> settings.setTimezone(validateTimezone(asString(value)));
            default -> throw UnknownSettingFieldException.forField(field);
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "UTC";
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw InvalidRequestException.withMessage("Invalid timezone: " + timezone);
        }
        return timezone;
    }
}
