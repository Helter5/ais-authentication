package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GuildAccessService {

    private static final Pattern GUILD_ID_PATTERN = Pattern.compile("\\d{17,20}");

    private final DiscordBotService discordBotService;
    private final AdminSettingsService adminSettingsService;
    private final AdminProperties adminProperties;

    /**
     * Endpoints that pull guildId out of a raw request Map (instead of a typed DTO) should route it
     * through here first, so a null/malformed/non-string value fails as a clean 400 instead of an
     * unhandled ClassCastException or falling through to the access check with a bogus ID.
     */
    public String requireValidGuildId(Object rawGuildId) {
        if (!(rawGuildId instanceof String guildId) || !GUILD_ID_PATTERN.matcher(guildId).matches()) {
            throw InvalidRequestException.withMessage("A valid Discord guild ID is required");
        }
        return guildId;
    }

    /**
     * Re-checks the claim against the current SUPER_ADMIN_IDS config on every call instead of just
     * trusting the JWT - same reasoning as hasLiveManagerRole below: an ID removed from config and
     * redeployed must lose super-admin access immediately, not only once its current access token
     * (up to app.jwt.access-token-ttl-seconds) happens to expire or gets refreshed.
     */
    public boolean isSuperAdmin(Claims claims) {
        return Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class))
                && adminProperties.superAdminIds().contains(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> guildIds(Claims claims) {
        List<String> guildIds = claims.get("guildIds", List.class);
        return guildIds == null ? List.of() : guildIds;
    }

    public boolean canManageGuild(Claims claims, String guildId) {
        return isSuperAdmin(claims) || hasLiveManagerRole(claims.getSubject(), guildId);
    }

    public void assertSuperAdmin(Claims claims) {
        if (!isSuperAdmin(claims)) {
            throw GuildAccessDeniedException.superAdminRequired();
        }
    }

    public void assertCanManageGuild(Claims claims, String guildId) {
        if (!canManageGuild(claims, guildId)) {
            throw GuildAccessDeniedException.managerAccessRequired();
        }
    }

    /**
     * Checked live against JDA's gateway-updated member cache on every request (GUILD_MEMBERS
     * intent + MemberCachePolicy.ALL keep it current - no REST call needed) instead of trusting the
     * guildIds JWT claim. A manager role revoked in Discord takes effect on this exact next request
     * rather than only at the next token refresh.
     */
    private boolean hasLiveManagerRole(String discordId, String guildId) {
        if (!adminSettingsService.isGuildAllowed(guildId)) {
            return false;
        }

        return discordBotService.jda().map(jda -> {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                return false;
            }
            DashboardSettings dashboardSettings = adminSettingsService.dashboardSettings(guildId);
            if (dashboardSettings.managerRoleIds().isEmpty()) {
                return false;
            }
            Member member = guild.getMemberById(discordId);
            return member != null && member.getRoles().stream()
                    .anyMatch(role -> dashboardSettings.managerRoleIds().contains(role.getId()));
        }).orElse(false);
    }
}
