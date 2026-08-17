package sk.gkanocz.aisauth.wipe;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.ManagerAccess;
import sk.gkanocz.aisauth.auth.PublicToAuthenticated;
import sk.gkanocz.aisauth.auth.SuperAdminAccess;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wipe")
@RequiredArgsConstructor
public class WipeController {

    private final WipeService wipeService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final LogRoutingService logRoutingService;

    @ManagerAccess
    @GetMapping("/settings")
    public WipeSettings getSettings(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return wipeService.getSettings(guildId);
    }

    @PublicToAuthenticated
    @GetMapping("/access")
    public Map<String, Object> getAccess(@AuthenticationPrincipal Claims claims, @RequestParam(required = false) String guildId) {
        if (guildId == null) {
            return Map.of("allowed", false, "reason", "no_guild");
        }
        if (!guildAccessService.isSuperAdmin(claims)) {
            return Map.of("allowed", false, "reason", "no_permission");
        }
        if (logRoutingService.channelIdFor(guildId, LogEventType.WIPE_RECAP).isEmpty()) {
            return Map.of("allowed", false, "reason", "no_channel");
        }
        return Map.of("allowed", true);
    }

    @ManagerAccess
    @GetMapping("/status")
    public WipeService.WipeStatusResponse getStatus(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return wipeService.status(guildId);
    }

    @SuperAdminAccess
    @PostMapping
    public Map<String, Object> startWipe(@AuthenticationPrincipal Claims claims, @RequestBody StartWipeRequest request) {
        // Old app stacked requireSuperAdmin + requireGuildManager on this route - since super
        // admins already auto-pass canManageGuild too, that combo was effectively super-admin-only
        // for this destructive action. assertCanManageGuild alone would let any per-guild manager
        // trigger a wipe, which is a real permission widening on a mass-removal action.
        guildAccessService.assertSuperAdmin(claims);
        Guild guild = discordBotService.requireGuild(request.guildId());
        List<String> keepRoleIds = request.keepRoleIds() == null ? List.of() : request.keepRoleIds();
        int total = wipeService.start(
                guild, Boolean.TRUE.equals(request.removeAllRoles()), keepRoleIds,
                claims.getSubject(), claims.get("username", String.class));
        return Map.of("started", true, "total", total);
    }

    public record StartWipeRequest(String guildId, Boolean removeAllRoles, List<String> keepRoleIds) {
    }
}
