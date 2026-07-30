package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import tools.jackson.core.type.TypeReference;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GuildAccessService {

    private final DiscordBotService discordBotService;
    private final AdminSettingsService adminSettingsService;

    public boolean isSuperAdmin(Claims claims) {
        return Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class));
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
        List<String> allowedGuildIds = adminSettingsService.get(
                "allowed_guild_ids", new TypeReference<List<String>>() { }, List.of());
        if (!allowedGuildIds.contains(guildId)) {
            return false;
        }

        return discordBotService.jda().map(jda -> {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                return false;
            }
            DashboardSettings dashboardSettings = adminSettingsService.get(
                    "dashboard_settings_" + guildId, DashboardSettings.class, DashboardSettings.empty());
            if (dashboardSettings.managerRoleIds().isEmpty()) {
                return false;
            }
            Member member = guild.getMemberById(discordId);
            return member != null && member.getRoles().stream()
                    .anyMatch(role -> dashboardSettings.managerRoleIds().contains(role.getId()));
        }).orElse(false);
    }
}
