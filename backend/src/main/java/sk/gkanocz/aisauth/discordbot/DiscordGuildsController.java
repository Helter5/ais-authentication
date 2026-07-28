package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;

import java.util.List;

@RestController
@RequestMapping("/api/discord")
@RequiredArgsConstructor
public class DiscordGuildsController {

    private final DiscordBotService discordBotService;
    private final GuildAccessService guildAccessService;

    @GetMapping("/guilds")
    public List<GuildResponse> getGuilds(@AuthenticationPrincipal Claims claims) {
        boolean isSuperAdmin = guildAccessService.isSuperAdmin(claims);
        List<String> eligibleGuildIds = guildAccessService.guildIds(claims);
        return discordBotService.jda()
                .map(jda -> jda.getGuilds().stream()
                        .filter(guild -> isSuperAdmin || eligibleGuildIds.contains(guild.getId()))
                        .map(GuildResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    public record GuildResponse(String id, String name, String icon) {
        static GuildResponse from(Guild guild) {
            return new GuildResponse(guild.getId(), guild.getName(), guild.getIconUrl());
        }
    }
}
