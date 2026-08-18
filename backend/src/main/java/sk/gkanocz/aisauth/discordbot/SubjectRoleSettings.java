package sk.gkanocz.aisauth.discordbot;

import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads /pridatpredmet's admin-configured allowlists out of the generic cmd_settings_ blob
 * (same convention every command's Settings modal already saves through) - shared by the slash
 * command handler, its autocomplete, and the approve/reject button listener so all three agree on
 * what "configured" and "allowed" mean without three separate readings of the same JSON.
 */
final class SubjectRoleSettings {

    static final String COMMAND_NAME = "pridatpredmet";
    private static final String SETTINGS_KEY_PREFIX = "cmd_settings_";
    private static final String SETTINGS_KEY_SUFFIX = "_" + COMMAND_NAME;

    private SubjectRoleSettings() {
    }

    static Set<String> allowedRoleIds(AdminSettingsService adminSettingsService, String guildId) {
        return roleIdSet(adminSettingsService, guildId, "allowedRoleIds");
    }

    static Set<String> approverRoleIds(AdminSettingsService adminSettingsService, String guildId) {
        return roleIdSet(adminSettingsService, guildId, "approverRoleIds");
    }

    private static Set<String> roleIdSet(AdminSettingsService adminSettingsService, String guildId, String field) {
        Map<String, Object> settings = adminSettingsService.get(
                SETTINGS_KEY_PREFIX + guildId + SETTINGS_KEY_SUFFIX, new TypeReference<Map<String, Object>>() { }, Map.of());
        Object raw = settings.get(field);
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).collect(Collectors.toSet());
    }
}
