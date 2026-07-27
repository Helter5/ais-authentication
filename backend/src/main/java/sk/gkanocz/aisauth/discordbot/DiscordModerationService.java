package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Single place for "ban" / "kick" / "timeout" against a guild member, used by every
 * auto-moderation feature (warn thresholds, the hacked-account trap, ...). Centralizing this
 * means a missing-permission or role-hierarchy failure is described the same way everywhere,
 * instead of each feature re-implementing (or forgetting) its own try/catch around the JDA call.
 */
@Component
public class DiscordModerationService {

    public record Outcome(boolean success, String detail) {
    }

    /**
     * Checked before actually attempting the action, so a caller can refuse to act at all
     * (e.g. not record a warning whose matching punishment can't be enforced) instead of
     * discovering the failure only after the fact.
     */
    public String missingPermission(Member target, String action) {
        Permission required = requiredPermission(action);
        if (required == null) {
            return null;
        }
        Member self = target.getGuild().getSelfMember();
        if (!self.hasPermission(required)) {
            return "bot is missing the " + required + " permission";
        }
        if (!self.canInteract(target)) {
            return "target's role is equal to or higher than the bot's role";
        }
        return null;
    }

    /**
     * Performs the action. Returns null for an unrecognized action (nothing to do), otherwise
     * an Outcome describing success or - critically - exactly why it failed, instead of the
     * exception being swallowed by a bare .queue() or logged only to the console.
     */
    public Outcome apply(Member target, String action, String reason, Duration timeoutDuration) {
        try {
            String result = switch (action) {
                case "ban" -> {
                    target.ban(0, TimeUnit.SECONDS).reason(reason).complete();
                    yield "banned";
                }
                case "kick" -> {
                    target.kick().reason(reason).complete();
                    yield "kicked";
                }
                case "timeout" -> {
                    target.timeoutFor(timeoutDuration).reason(reason).complete();
                    yield "timed out (" + formatDuration(timeoutDuration) + ")";
                }
                default -> null;
            };
            return result == null ? null : new Outcome(true, result);
        } catch (Exception e) {
            return new Outcome(false, describeFailure(e));
        }
    }

    private Permission requiredPermission(String action) {
        return switch (action) {
            case "ban" -> Permission.BAN_MEMBERS;
            case "kick" -> Permission.KICK_MEMBERS;
            case "timeout" -> Permission.MODERATE_MEMBERS;
            default -> null;
        };
    }

    /**
     * Turns a JDA exception from any member-targeting action (not just ban/kick/timeout - also
     * role add/remove, channel edits, etc.) into a human-readable reason. Public so callers doing
     * their own bulk member operations (e.g. semester role sync) can report *why* an action failed
     * instead of just counting it as a failure.
     */
    public String describeFailure(Exception e) {
        if (e instanceof InsufficientPermissionException ipe) {
            return "bot is missing the " + ipe.getPermission() + " permission";
        }
        if (e instanceof HierarchyException) {
            return "target's role is equal to or higher than the bot's role";
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        return (minutes >= 60 && minutes % 60 == 0) ? (minutes / 60) + "h" : minutes + "m";
    }
}
