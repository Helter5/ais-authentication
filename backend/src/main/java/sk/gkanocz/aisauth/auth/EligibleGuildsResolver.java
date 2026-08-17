package sk.gkanocz.aisauth.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared between the Discord OAuth2 login flow and /api/auth/refresh, which both need to derive
 * "which guilds can this Discord user manage right now" from live bot state - super admins can
 * manage every guild the bot is in, regular managers are restricted to guilds explicitly onboarded
 * via allowed_guild_ids AND where they hold one of that guild's configured manager roles.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EligibleGuildsResolver {

    private final DiscordBotService discordBotService;
    private final AdminSettingsService adminSettingsService;

    public List<String> computeEligibleGuildIds(String discordId, boolean isSuperAdmin) {
        return discordBotService.jda().map(jda -> {
            List<String> allowedGuildIds = adminSettingsService.get(
                    "allowed_guild_ids", new TypeReference<List<String>>() { }, List.of());

            List<String> eligible = new ArrayList<>();
            for (Guild guild : jda.getGuilds()) {
                if (isSuperAdmin) {
                    eligible.add(guild.getId());
                    continue;
                }
                if (!allowedGuildIds.contains(guild.getId())) {
                    continue;
                }
                DashboardSettings dashboardSettings = adminSettingsService.dashboardSettings(guild.getId());
                if (dashboardSettings.managerRoleIds().isEmpty()) {
                    continue;
                }
                Member member = fetchMemberSafely(guild, discordId);
                boolean hasManagerRole = member != null && member.getRoles().stream()
                        .anyMatch(role -> dashboardSettings.managerRoleIds().contains(role.getId()));
                if (hasManagerRole) {
                    eligible.add(guild.getId());
                }
            }
            return eligible;
        }).orElseGet(List::of);
    }

    private Member fetchMemberSafely(Guild guild, String discordId) {
        try {
            return guild.retrieveMemberById(discordId).complete();
        } catch (Exception e) {
            log.warn("Could not fetch member {} in guild {} while computing manager-role eligibility: {}",
                    discordId, guild.getId(), e.getMessage());
            return null;
        }
    }
}
