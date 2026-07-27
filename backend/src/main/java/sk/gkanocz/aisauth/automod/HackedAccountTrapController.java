package sk.gkanocz.aisauth.automod;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

import java.util.Map;

@RestController
@RequestMapping("/api/modules/hacked-account-trap")
@RequiredArgsConstructor
public class HackedAccountTrapController {

    private final HackedAccountTrapService hackedAccountTrapService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final DashboardAuditLogger dashboardAuditLogger;

    @GetMapping
    public HackedAccountTrapSettings getSettings(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return hackedAccountTrapService.get(guildId);
    }

    @PostMapping
    public HackedAccountTrapSettings saveSettings(
            @AuthenticationPrincipal Claims claims,
            @RequestBody HackedAccountTrapService.HackedAccountTrapSaveRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());
        HackedAccountTrapSettings previous = hackedAccountTrapService.get(request.guildId());
        HackedAccountTrapSettings saved = hackedAccountTrapService.save(guild, request);
        dashboardAuditLogger.log(claims, guild, "Updated Hacked Account Trap module", Map.of("before", previous, "after", saved));
        return saved;
    }
}
