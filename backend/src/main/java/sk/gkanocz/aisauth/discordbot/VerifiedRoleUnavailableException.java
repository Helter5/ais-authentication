package sk.gkanocz.aisauth.discordbot;

/**
 * Raised by {@link VerifiedRoleResolver} when the bot could not actually assign the configured
 * verified role right now - not a REST-facing DomainException, this only ever surfaces as a
 * Discord reply from the slash-command listeners.
 */
public class VerifiedRoleUnavailableException extends RuntimeException {

    private VerifiedRoleUnavailableException(String message) {
        super(message);
    }

    public static VerifiedRoleUnavailableException notConfigured() {
        return new VerifiedRoleUnavailableException(
                "Verified role is not configured. Set it in dashboard Settings before verifying users.");
    }

    public static VerifiedRoleUnavailableException roleDeleted() {
        return new VerifiedRoleUnavailableException(
                "The configured verified role no longer exists. Select a valid role in dashboard Settings.");
    }

    public static VerifiedRoleUnavailableException missingManageRoles() {
        return new VerifiedRoleUnavailableException("The bot is missing the Manage Roles permission.");
    }

    public static VerifiedRoleUnavailableException managedRole(String roleName) {
        return new VerifiedRoleUnavailableException(
                "The configured verified role \"" + roleName + "\" is managed by an integration and cannot be assigned.");
    }

    public static VerifiedRoleUnavailableException belowBotRole(String roleName) {
        return new VerifiedRoleUnavailableException(
                "Move the bot role above the configured verified role \"" + roleName + "\".");
    }
}
