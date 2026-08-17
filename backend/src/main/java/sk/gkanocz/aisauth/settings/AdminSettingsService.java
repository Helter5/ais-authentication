package sk.gkanocz.aisauth.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AdminSettingsService {

    private final AdminSettingRepository adminSettingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Whether the guild has been added via the Admin dashboard's "Allowed Servers" panel. Every
     * bot-facing feature (slash commands, automod listeners) must check this
     * before doing anything, so a server removed from the allowlist stops working everywhere,
     * not just for commands.
     */
    public boolean isGuildAllowed(String guildId) {
        return get("allowed_guild_ids", new TypeReference<List<String>>() { }, List.of()).contains(guildId);
    }

    public boolean isMaintenanceMode() {
        return get("maintenance_mode", Boolean.class, false);
    }

    /** The guild's manager-role configuration (Admin dashboard access), read via one shared key convention. */
    public DashboardSettings dashboardSettings(String guildId) {
        return get("dashboard_settings_" + guildId, DashboardSettings.class, DashboardSettings.empty());
    }

    public <T> T get(String key, Class<T> type, T fallback) {
        return adminSettingRepository.findById(key)
                .map(setting -> readOrFallback(fallback, () -> objectMapper.readValue(setting.getValue(), type)))
                .orElse(fallback);
    }

    public <T> T get(String key, TypeReference<T> typeReference, T fallback) {
        return adminSettingRepository.findById(key)
                .map(setting -> readOrFallback(fallback, () -> objectMapper.readValue(setting.getValue(), typeReference)))
                .orElse(fallback);
    }

    @Transactional
    public void set(String key, Object value) {
        String json = objectMapper.writeValueAsString(value);
        adminSettingRepository.findById(key)
                .ifPresentOrElse(
                        existing -> existing.updateValue(json),
                        () -> adminSettingRepository.save(new AdminSetting(key, json)));
    }

    @Transactional
    public void remove(String key) {
        adminSettingRepository.deleteById(key);
    }

    private <T> T readOrFallback(T fallback, Supplier<T> reader) {
        try {
            return reader.get();
        } catch (Exception e) {
            return fallback;
        }
    }
}
