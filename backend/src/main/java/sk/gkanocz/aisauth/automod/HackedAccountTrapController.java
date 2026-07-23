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
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

import java.util.Map;

@RestController
@RequestMapping("/api/modules/hacked-account-trap")
@RequiredArgsConstructor
public class HackedAccountTrapController {

    private final HackedAccountTrapService hackedAccountTrapService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final AuditLogService auditLogService;

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
        logDashboardChange(claims, guild, previous, saved);
        return saved;
    }

    private void logDashboardChange(
            Claims claims, Guild guild, HackedAccountTrapSettings before, HackedAccountTrapSettings after) {
        try {
            auditLogService.log(new AuditLogEntry(
                    "dashboard", "Updated Hacked Account Trap module", guild.getId(), guild.getName(),
                    null, null, claims.getSubject(), claims.get("username", String.class),
                    Map.of("before", before, "after", after)));
        } catch (Exception e) {
            // best-effort audit trail; never block the underlying change on a logging failure
        }
    }
}
