package sk.gkanocz.aisauth.discordbot;

import sk.gkanocz.aisauth.semester.SemesterDefinition;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleService;
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
    /** Roles allowed in BOTH semesters - unioned into whichever semester's list is asked for. Lets an
     *  admin place a year-round subject once instead of in the winter and summer lists separately. */
    private static final String BOTH_FIELD = "allowedRoleIdsBoth";
    /** Pre-split configs only ever wrote this flat field - kept as a fallback so an admin who saved
     *  Settings before the ZS/LS split isn't silently left with an empty allowlist on both. */
    private static final String LEGACY_FIELD = "allowedRoleIds";
    private static final String AUTO_GRANT_LIMIT_FIELD = "autoGrantLimit";
    /** Members holding any of these roles skip the auto-grant limit entirely - every selection is
     *  granted immediately, nothing queues for approval. */
    private static final String BYPASS_LIMIT_ROLE_IDS_FIELD = "bypassLimitRoleIds";

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
        Set<String> both = roleIdSet(settings, BOTH_FIELD);
        if (winter.isEmpty() && summer.isEmpty() && both.isEmpty()) {
            Set<String> legacy = roleIdSet(settings, LEGACY_FIELD);
            if (!legacy.isEmpty()) {
                return legacy;
            }
        }
        Set<String> combined = new LinkedHashSet<>(both);
        if (SemesterDefinition.TYPE_WINTER.equals(semesterType)) {
            combined.addAll(winter);
            return combined;
        }
        if (SemesterDefinition.TYPE_SUMMER.equals(semesterType)) {
            combined.addAll(summer);
            return combined;
        }
        combined.addAll(winter);
        combined.addAll(summer);
        return combined;
    }

    /** True once an admin has allowed at least one subject role for either semester, for both, or (pre-split) the old flat list. */
    static boolean anyAllowedRoleIdsConfigured(AdminSettingsService adminSettingsService, String guildId) {
        Map<String, Object> settings = settingsMap(adminSettingsService, guildId);
        return !roleIdSet(settings, WINTER_FIELD).isEmpty()
                || !roleIdSet(settings, SUMMER_FIELD).isEmpty()
                || !roleIdSet(settings, BOTH_FIELD).isEmpty()
                || !roleIdSet(settings, LEGACY_FIELD).isEmpty();
    }

    /**
     * How many /pridatpredmet selections auto-grant per guild+user+semester before the rest queue for
     * approval. Admin-configurable; missing, non-numeric, or negative falls back to
     * {@link SubjectRoleService#DEFAULT_AUTO_GRANT_LIMIT}.
     */
    static int autoGrantLimit(AdminSettingsService adminSettingsService, String guildId) {
        Object raw = settingsMap(adminSettingsService, guildId).get(AUTO_GRANT_LIMIT_FIELD);
        if (raw instanceof Number number && number.intValue() >= 0) {
            return number.intValue();
        }
        return SubjectRoleService.DEFAULT_AUTO_GRANT_LIMIT;
    }

    /** Roles whose holders skip the auto-grant limit on /pridatpredmet entirely. */
    static Set<String> bypassLimitRoleIds(AdminSettingsService adminSettingsService, String guildId) {
        return roleIdSet(settingsMap(adminSettingsService, guildId), BYPASS_LIMIT_ROLE_IDS_FIELD);
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
