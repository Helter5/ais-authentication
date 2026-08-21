package sk.gkanocz.aisauth.semester;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.ManagerAccess;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SemesterOperationController {

    private final SemesterOperationService semesterOperationService;
    private final SemesterPlanService semesterPlanService;
    private final SemesterRollbackService semesterRollbackService;
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
    @GetMapping("/switchplan/progress")
    public SemesterOperationState getPlanProgress(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return semesterOperationService.status(guildId, "plan");
    }

    @ManagerAccess
    @PostMapping("/switchplan/run")
    public Map<String, Boolean> runPlan(@AuthenticationPrincipal Claims claims, @RequestBody RunPlanRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());
        semesterPlanService.startPlan(
                guild, request.planId(), Boolean.TRUE.equals(request.resume()),
                claims.getSubject(), claims.get("username", String.class));
        return Map.of("started", true);
    }

    @ManagerAccess
    @GetMapping("/switchsemester/history")
    public List<SemesterRollbackService.HistoryView> getHistory(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return semesterRollbackService.history(guildId);
    }

    @ManagerAccess
    @GetMapping("/switchsemester/migration/{migrationId}/detail")
    public SemesterRollbackService.MigrationDetail getMigrationDetail(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId, @PathVariable String migrationId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return semesterRollbackService.migrationDetail(guildId, migrationId);
    }

    @ManagerAccess
    @GetMapping("/switchsemester/rollback/progress")
    public SemesterOperationState getRollbackProgress(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return semesterRollbackService.status(guildId);
    }

    @ManagerAccess
    @PostMapping("/switchsemester/rollback")
    public Map<String, Boolean> runRollback(@AuthenticationPrincipal Claims claims, @RequestBody RunRollbackRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());
        semesterRollbackService.startRollback(
                guild, request.migrationId(), request.roleGroupKeys(), request.visibilityIds(),
                Boolean.TRUE.equals(request.revertPlanPosition()), claims.getSubject(), claims.get("username", String.class));
        return Map.of("started", true);
    }

    public record RunSetupRequest(String guildId, String semesterName, Boolean visible, Boolean clearRoles, Boolean resume) {
    }

    public record RunPlanRequest(String guildId, String planId, Boolean resume) {
    }

    public record RunRollbackRequest(
            String guildId, String migrationId, List<String> roleGroupKeys, List<Long> visibilityIds, Boolean revertPlanPosition) {
    }
}
