package sk.gkanocz.aisauth.automod;

import java.util.List;

public record HackedAccountTrapSettings(
        boolean enabled,
        String trapChannelId,
        String action,
        int timeoutMinutes,
        String logChannelId,
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

    public static HackedAccountTrapSettings defaults(String trapChannelId, String logChannelId, int cleanupMinutes) {
        return new HackedAccountTrapSettings(
                trapChannelId != null, trapChannelId, "timeout", 1440, logChannelId,
                true, true, cleanupMinutes, List.of(), true,
                false, "Your account triggered the hacked-account trap in {server}. Please contact a server administrator if this was a mistake.",
                "Hacked account trap triggered",
                false, null, null, "hacked-{user}", false,
                "Hacked account trap triggered by {user}.", false, false, List.of());
    }

    public HackedAccountTrapSettings withLogChannelId(String resolvedLogChannelId) {
        return new HackedAccountTrapSettings(
                enabled, trapChannelId, action, timeoutMinutes, resolvedLogChannelId,
                deleteTriggerMessage, deleteRecentMessages, cleanupMinutes, exemptRoleIds, ignoreAdministrators,
                dmUser, dmMessage, reason,
                incidentChannelEnabled, incidentChannelCategoryId, incidentChannelClosedCategoryId,
                incidentChannelNameTemplate, incidentChannelIncludeUser, incidentChannelMessage,
                incidentChannelPostDmStatus, incidentChannelTagRoles, incidentChannelTagRoleIds);
    }
}
