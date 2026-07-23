package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DiscordBotService discordBotService;

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(@RequestParam String guildId) {
        Guild guild = discordBotService.jda()
                .orElseThrow(GuildNotAvailableException::botNotConnected)
                .getGuildById(guildId);

        if (guild == null) {
            throw GuildNotAvailableException.guildNotFound(guildId);
        }

        DashboardResponse.ServerInfo server = new DashboardResponse.ServerInfo(
                guild.getId(),
                guild.getName(),
                guild.getIconUrl(),
                guild.getMemberCount(),
                guild.getCategories().size(),
                guild.getTextChannels().size(),
                guild.getVoiceChannels().size(),
                guild.getRoles().size());

        String nickname = guild.getSelfMember().getNickname() != null
                ? guild.getSelfMember().getNickname()
                : guild.getJDA().getSelfUser().getName();

        DashboardResponse.Settings settings = new DashboardResponse.Settings(nickname, "UTC");
        DashboardResponse.Synchronization synchronization = new DashboardResponse.Synchronization(0, null, null);

        return new DashboardResponse(server, settings, synchronization);
    }
}
