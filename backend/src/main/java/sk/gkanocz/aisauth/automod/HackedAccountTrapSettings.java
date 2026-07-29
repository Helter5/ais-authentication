package sk.gkanocz.aisauth.automod;

import java.util.List;

public record HackedAccountTrapSettings(
        boolean enabled,
        String trapChannelId,
        String action,
        int timeoutMinutes,
        boolean deleteTriggerMessage,
        boolean deleteRecentMessages,
        int cleanupMinutes,
        List<String> exemptRoleIds,
        boolean ignoreAdministrators,
        boolean dmUser,
        String dmMessage,
        String reason,
        boolean incidentChannelEnabled,
        String incidentChannelCategoryId,
        String incidentChannelClosedCategoryId,
        String incidentChannelNameTemplate,
        boolean incidentChannelIncludeUser,
        String incidentChannelMessage,
        boolean incidentChannelPostDmStatus,
        boolean incidentChannelTagRoles,
        List<String> incidentChannelTagRoleIds) {

    public static HackedAccountTrapSettings defaults(String trapChannelId, int cleanupMinutes) {
        return new HackedAccountTrapSettings(
                trapChannelId != null, trapChannelId, "timeout", 1440,
                true, true, cleanupMinutes, List.of(), true,
                false, "Your account triggered the hacked-account trap in {server}. Please contact a server administrator if this was a mistake.",
                "Hacked account trap triggered",
                false, null, null, "hacked-{user}", false,
                "Hacked account trap triggered by {user}.", false, false, List.of());
    }
}
