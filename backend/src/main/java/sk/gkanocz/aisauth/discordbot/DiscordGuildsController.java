package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/discord")
@RequiredArgsConstructor
public class DiscordGuildsController {

    private final DiscordBotService discordBotService;

    @GetMapping("/guilds")
    public List<GuildResponse> getGuilds() {
        return discordBotService.jda()
                .map(jda -> jda.getGuilds().stream().map(GuildResponse::from).toList())
                .orElseGet(List::of);
    }

    public record GuildResponse(String id, String name, String icon) {
        static GuildResponse from(Guild guild) {
            return new GuildResponse(guild.getId(), guild.getName(), guild.getIconUrl());
        }
    }
}
