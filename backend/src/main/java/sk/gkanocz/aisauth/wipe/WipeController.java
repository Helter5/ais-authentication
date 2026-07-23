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
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wipe")
@RequiredArgsConstructor
public class WipeController {

    private final WipeService wipeService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final AdminSettingsService adminSettingsService;

    @GetMapping("/settings")
    public WipeSettings getSettings(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return wipeService.getSettings(guildId);
    }

    @GetMapping("/access")
    public Map<String, Object> getAccess(@AuthenticationPrincipal Claims claims, @RequestParam(required = false) String guildId) {
        if (guildId == null) {
            return Map.of("allowed", false, "reason", "no_guild");
        }
        if (!guildAccessService.canManageGuild(claims, guildId)) {
            return Map.of("allowed", false, "reason", "no_permission");
        }
        String channelId = adminSettingsService.get("recap_channel_wipe_" + guildId, String.class, null);
        if (channelId == null) {
            return Map.of("allowed", false, "reason", "no_channel");
        }
        return Map.of("allowed", true);
    }

    @GetMapping("/status")
    public WipeService.WipeStatusResponse getStatus(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return wipeService.status(guildId);
    }

    @PostMapping
    public Map<String, Object> startWipe(@AuthenticationPrincipal Claims claims, @RequestBody StartWipeRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
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
