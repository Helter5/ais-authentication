package sk.gkanocz.aisauth.semester;

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
import sk.gkanocz.aisauth.auth.ManagerAccess;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SemesterOperationController {

    private final SemesterOperationService semesterOperationService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;

    @ManagerAccess
    @GetMapping("/setupsemester/progress")
    public SemesterOperationState getSetupProgress(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return semesterOperationService.status(guildId, "setup");
    }

    @ManagerAccess
    @PostMapping("/setupsemester/run")
    public Map<String, Boolean> runSetup(@AuthenticationPrincipal Claims claims, @RequestBody RunSetupRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());
        semesterOperationService.startSetup(
                guild, request.semesterName(), Boolean.TRUE.equals(request.visible()),
                Boolean.TRUE.equals(request.clearRoles()), Boolean.TRUE.equals(request.resume()),
                claims.getSubject(), claims.get("username", String.class));
        return Map.of("started", true);
    }

    @ManagerAccess
    @GetMapping("/switchsemester/progress")
    public SemesterOperationState getSwitchProgress(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return semesterOperationService.status(guildId, "switch");
    }

    @ManagerAccess
    @PostMapping("/switchsemester/run")
    public Map<String, Boolean> runSwitch(@AuthenticationPrincipal Claims claims, @RequestBody RunSwitchRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());
        semesterOperationService.startSwitch(
                guild, request.oldName(), request.newName(), Boolean.TRUE.equals(request.resume()),
                claims.getSubject(), claims.get("username", String.class));
        return Map.of("started", true);
    }

    public record RunSetupRequest(String guildId, String semesterName, Boolean visible, Boolean clearRoles, Boolean resume) {
    }

    public record RunSwitchRequest(String guildId, String oldName, String newName, Boolean resume) {
    }
}
