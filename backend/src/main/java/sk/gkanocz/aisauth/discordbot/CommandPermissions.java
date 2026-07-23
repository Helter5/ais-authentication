package sk.gkanocz.aisauth.discordbot;

import java.util.List;

public record CommandPermissions(
        List<String> allowedChannels,
        List<String> ignoredChannels,
        List<String> allowedRoles,
        List<String> ignoredRoles,
        boolean adminOnly) {

    public static CommandPermissions empty() {
        return new CommandPermissions(List.of(), List.of(), List.of(), List.of(), false);
    }

    public boolean isConfigured() {
        return !allowedChannels.isEmpty() || !ignoredChannels.isEmpty()
                || !allowedRoles.isEmpty() || !ignoredRoles.isEmpty() || adminOnly;
    }

    /**
     * Mirrors the old bot's interactionCreate gate order: ignored channel, then allowed
     * channel, then ignored role, then allowed role, then admin-only. Returns a block reason,
     * or null if the interaction may proceed.
     */
    public String blockReason(String channelId, List<String> memberRoleIds, boolean isAdministrator) {
        if (!ignoredChannels.isEmpty() && ignoredChannels.contains(channelId)) {
            return "Channel is ignored";
        }
        if (!allowedChannels.isEmpty() && !allowedChannels.contains(channelId)) {
            return "Channel is not allowed";
        }
        if (!ignoredRoles.isEmpty() && memberRoleIds.stream().anyMatch(ignoredRoles::contains)) {
            return "User has an ignored role";
        }
        if (!allowedRoles.isEmpty() && memberRoleIds.stream().noneMatch(allowedRoles::contains)) {
            return "User lacks an allowed role";
        }
        if (adminOnly && !isAdministrator) {
            return "Admin only command";
        }
        return null;
    }
}
