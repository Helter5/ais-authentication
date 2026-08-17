package sk.gkanocz.aisauth.automod;

import java.util.List;

/**
 * The trap always bans permanently on trigger - there is no configurable moderation action, and
 * the ban reason is always {@link #REASON}, not user-editable. Message cleanup is delegated to
 * Discord's own "delete message history" option on the ban itself (deleteMessageHistorySeconds);
 * the trigger message itself is deliberately left alone (deleting recent history covers it).
 */
public record HackedAccountTrapSettings(
        boolean enabled,
        String trapChannelId,
        boolean ignoreAdministrators,
        List<String> exemptRoleIds,
        boolean deleteMessageHistory,
        int deleteMessageHistorySeconds,
        boolean dmUser,
        String dmMessage) {

    public static final String REASON = "Hacked account trap triggered";

    /** The exact duration options Discord's own ban dialog offers, in seconds. */
    public static final List<Integer> DELETE_MESSAGE_HISTORY_SECONDS_OPTIONS =
            List.of(3600, 21600, 43200, 86400, 259200, 604800);

    public static HackedAccountTrapSettings defaults(String trapChannelId) {
        return new HackedAccountTrapSettings(
                trapChannelId != null, trapChannelId, true, List.of(),
                false, 3600,
                false, "Your account triggered the hacked-account trap in {server}. Please contact a server administrator if this was a mistake.");
    }
}
