package sk.gkanocz.aisauth.discordbot;

import sk.gkanocz.aisauth.semester.SemesterDefinition;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashSet;
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
    private static final String WINTER_FIELD = "allowedRoleIdsWinter";
    private static final String SUMMER_FIELD = "allowedRoleIdsSummer";
    /** Pre-split configs only ever wrote this flat field - kept as a fallback so an admin who saved
     *  Settings before the ZS/LS split isn't silently left with an empty allowlist on both. */
    private static final String LEGACY_FIELD = "allowedRoleIds";

    private SubjectRoleSettings() {
    }

    /**
     * Subject roles grantable right now. {@code semesterType} is the guild's current semester
     * (SemesterDefinition.TYPE_WINTER/TYPE_SUMMER) - null or unrecognized falls back to the union of
     * both lists rather than blocking everything over an unrelated/not-yet-run semester setup.
     */
    static Set<String> allowedRoleIds(AdminSettingsService adminSettingsService, String guildId, String semesterType) {
        Map<String, Object> settings = settingsMap(adminSettingsService, guildId);
        Set<String> winter = roleIdSet(settings, WINTER_FIELD);
        Set<String> summer = roleIdSet(settings, SUMMER_FIELD);
        if (winter.isEmpty() && summer.isEmpty()) {
            Set<String> legacy = roleIdSet(settings, LEGACY_FIELD);
            if (!legacy.isEmpty()) {
                return legacy;
            }
        }
        if (SemesterDefinition.TYPE_WINTER.equals(semesterType)) {
            return winter;
        }
        if (SemesterDefinition.TYPE_SUMMER.equals(semesterType)) {
            return summer;
        }
        Set<String> combined = new LinkedHashSet<>(winter);
        combined.addAll(summer);
        return combined;
    }

    /** True once an admin has allowed at least one subject role for either semester (or, pre-split, the old flat list). */
    static boolean anyAllowedRoleIdsConfigured(AdminSettingsService adminSettingsService, String guildId) {
        Map<String, Object> settings = settingsMap(adminSettingsService, guildId);
        return !roleIdSet(settings, WINTER_FIELD).isEmpty()
                || !roleIdSet(settings, SUMMER_FIELD).isEmpty()
                || !roleIdSet(settings, LEGACY_FIELD).isEmpty();
    }

    static Set<String> approverRoleIds(AdminSettingsService adminSettingsService, String guildId) {
        return roleIdSet(settingsMap(adminSettingsService, guildId), "approverRoleIds");
    }

    private static Map<String, Object> settingsMap(AdminSettingsService adminSettingsService, String guildId) {
        return adminSettingsService.get(
                SETTINGS_KEY_PREFIX + guildId + SETTINGS_KEY_SUFFIX, new TypeReference<Map<String, Object>>() { }, Map.of());
    }

    private static Set<String> roleIdSet(Map<String, Object> settings, String field) {
        Object raw = settings.get(field);
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).collect(Collectors.toSet());
    }
}
