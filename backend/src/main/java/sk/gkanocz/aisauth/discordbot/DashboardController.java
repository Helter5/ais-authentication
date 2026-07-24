package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DiscordBotService discordBotService;
    private final GuildAccessService guildAccessService;

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);

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
