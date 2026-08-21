package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.PublicToAuthenticated;

import java.util.List;

@RestController
@RequestMapping("/api/discord")
@RequiredArgsConstructor
public class DiscordGuildsController {

    private final DiscordBotService discordBotService;
    private final GuildAccessService guildAccessService;
    private final DiscordStatusService discordStatusService;

    /**
     * Filters live via canManageGuild (JDA member-cache state), not the guildIds JWT claim, so a
     * server added to/removed from the allowlist or a manager role revoked in Discord shows up (or
     * disappears from) the server picker on the very next request - same reasoning as
     * GuildAccessService's javadoc.
     */
    @PublicToAuthenticated
    @GetMapping("/guilds")
    public List<GuildResponse> getGuilds(@AuthenticationPrincipal Claims claims) {
        return discordBotService.jda()
                .map(jda -> jda.getGuilds().stream()
                        .filter(guild -> guildAccessService.canManageGuild(claims, guild.getId()))
                        .map(GuildResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    /** Not guild-scoped - the same discordstatus.com summary for every dashboard user. See DiscordStatusService. */
    @PublicToAuthenticated
    @GetMapping("/status")
    public DiscordStatusService.Result getDiscordApiStatus() {
        return discordStatusService.status();
    }

    public record GuildResponse(String id, String name, String icon) {
        static GuildResponse from(Guild guild) {
            return new GuildResponse(guild.getId(), guild.getName(), guild.getIconUrl());
        }
    }
}
