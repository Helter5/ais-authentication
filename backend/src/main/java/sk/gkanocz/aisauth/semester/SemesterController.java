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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.ManagerAccess;
import sk.gkanocz.aisauth.auth.PublicToAuthenticated;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import tools.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SemesterController {

    private static final List<Integer> VALID_ROCNIK = List.of(1, 2, 3);

    private final AdminSettingsService adminSettingsService;
    private final LogRoutingService logRoutingService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final DashboardAuditLogger dashboardAuditLogger;
    private final SemesterVisibilityService semesterVisibilityService;
    private final SemesterOperationService semesterOperationService;
    private final SemesterStatusBroadcaster semesterStatusBroadcaster;

    @ManagerAccess
    @GetMapping("/semester/mappings")
    public Map<Integer, Map<Integer, List<CategoryRef>>> getMappings(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Map<Integer, Map<Integer, List<CategoryRef>>> mappings = new HashMap<>();
        for (int r = 1; r <= 3; r++) {
            Map<Integer, List<CategoryRef>> bySemester = new HashMap<>();
            for (int s = 1; s <= 6; s++) {
                bySemester.put(s, categories(guildId, r, s));
            }
            mappings.put(r, bySemester);
        }
        return mappings;
    }

    @ManagerAccess
    @PostMapping("/semester/mappings")
    public Map<String, Boolean> saveMapping(@AuthenticationPrincipal Claims claims, @RequestBody SaveMappingRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        if (!VALID_ROCNIK.contains(request.rocnik()) || request.semester() == null
                || request.semester() < 1 || request.semester() > 6) {
            throw InvalidRequestException.withMessage("Invalid rocnik (1-3) or semester (1-6)");
        }
        if (request.categories() == null) {
            throw InvalidRequestException.withMessage("categories must be an array");
        }
        List<CategoryRef> previous = categories(request.guildId(), request.rocnik(), request.semester());
        adminSettingsService.set(mappingKey(request.guildId(), request.rocnik(), request.semester()), request.categories());
        dashboardAuditLogger.log(claims, request.guildId(), "Updated semester category mapping", Map.of(
                "year", request.rocnik(), "semester", request.semester(), "before", previous, "after", request.categories()));
        return Map.of("success", true);
    }

    @ManagerAccess
    @PostMapping("/semester")
    public Map<String, Object> setVisibility(@AuthenticationPrincipal Claims claims, @RequestBody SetVisibilityRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        if (!VALID_ROCNIK.contains(request.rocnik()) || request.semester() == null
                || request.semester() < 1 || request.semester() > 6) {
            throw InvalidRequestException.withMessage("Invalid rocnik (1-3) or semester (1-6)");
        }
        List<CategoryRef> categoryEntries = categories(request.guildId(), request.rocnik(), request.semester());
        if (categoryEntries.isEmpty()) {
            throw InvalidRequestException.withMessage(
                    "No categories configured for " + request.rocnik() + ". ročník, " + request.semester()
                            + ". semester. Add them in Commands → Patterns.");
        }
        Guild guild = discordBotService.requireGuild(request.guildId());
        List<String> categoryIds = categoryEntries.stream().map(CategoryRef::id).toList();
        boolean visible = Boolean.TRUE.equals(request.visible());
        SemesterVisibilityService.Result result = semesterVisibilityService.apply(guild, categoryIds, visible, false);

        dashboardAuditLogger.log(claims, request.guildId(), (visible ? "Showed" : "Hid") + " semester categories", Map.of(
                "year", request.rocnik(), "semester", request.semester(),
                "categoriesUpdated", result.categoriesUpdated(), "channelsUpdated", result.channelsUpdated(),
                "rolesUpdated", result.rolesUpdated()));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("categoriesUpdated", result.categoriesUpdated());
        response.put("channelsUpdated", result.channelsUpdated());
        response.put("rolesUpdated", result.rolesUpdated());
        response.put("logs", result.logs());
        return response;
    }

    @ManagerAccess
    @GetMapping("/semester/configs")
    public Map<String, Object> getConfigs(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return adminSettingsService.get(configsKey(guildId), new TypeReference<Map<String, Object>>() { }, Map.of());
    }

    @ManagerAccess
    @PostMapping("/semester/configs")
    public Map<String, Boolean> saveConfigs(@AuthenticationPrincipal Claims claims, @RequestBody Map<String, Object> body) {
        String guildId = guildAccessService.requireValidGuildId(body.get("guildId"));
        guildAccessService.assertCanManageGuild(claims, guildId);
        Map<String, Object> settings = new HashMap<>(body);
        settings.remove("guildId");
        Map<String, Object> previous = adminSettingsService.get(configsKey(guildId), new TypeReference<Map<String, Object>>() { }, Map.of());
        adminSettingsService.set(configsKey(guildId), settings);
        dashboardAuditLogger.log(claims, guildId, "Updated switchsemester settings", Map.of("before", previous, "after", settings));
        return Map.of("success", true);
    }

    @PublicToAuthenticated
    @GetMapping("/semester/access")
    public Map<String, Object> getAccess(@AuthenticationPrincipal Claims claims, @RequestParam(required = false) String guildId) {
        if (guildId == null) {
            return Map.of("allowed", false, "reason", "no_guild");
        }
        if (!guildAccessService.canManageGuild(claims, guildId)) {
            return Map.of("allowed", false, "reason", "no_permission");
        }
        if (logRoutingService.channelIdFor(guildId, LogEventType.SEMESTER_RECAP).isEmpty()) {
            return Map.of("allowed", false, "reason", "no_channel");
        }
        return Map.of("allowed", true);
    }

    @ManagerAccess
    @GetMapping("/semester/current")
    public Map<String, Object> getCurrentPlan(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        String planId = semesterOperationService.currentPlan(guildId);
        Map<String, Object> response = new HashMap<>();
        response.put("currentPlanId", planId);
        response.put("currentPlanName", planId == null ? null : planName(guildId, planId));
        response.put("currentSemesterType", planId == null ? null : semesterType(guildId, planId));
        return response;
    }

    /** Manual override escape hatch for a desynced current-plan pointer - see SemesterOperationService.overrideCurrentPlan. */
    @ManagerAccess
    @PostMapping("/semester/current")
    public Map<String, Boolean> setCurrentPlan(
            @AuthenticationPrincipal Claims claims, @RequestBody SetCurrentPlanRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());
        semesterOperationService.overrideCurrentPlan(
                guild, request.planId(), claims.getSubject(), claims.get("username", String.class));
        return Map.of("success", true);
    }

    /** Pushes the current semester type instantly to open tabs (see SemesterOperationService#setCurrentPlan) instead of waiting on a poll. */
    @ManagerAccess
    @GetMapping("/semester/stream")
    public SseEmitter streamSemesterType(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        String planId = semesterOperationService.currentPlan(guildId);
        return semesterStatusBroadcaster.subscribe(guildId, planId == null ? null : semesterType(guildId, planId));
    }

    @ManagerAccess
    @GetMapping("/semester/next")
    public Map<String, Object> getNextPlan(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        SemesterOperationService.NextPlan next = semesterOperationService.nextPlan(guildId);
        Map<String, Object> response = new HashMap<>();
        response.put("currentPlanId", next.currentPlanId());
        response.put("currentPlanName", next.currentPlanId() == null ? null : planName(guildId, next.currentPlanId()));
        response.put("currentSemesterType", next.currentPlanId() == null ? null : semesterType(guildId, next.currentPlanId()));
        response.put("nextPlanId", next.nextPlanId());
        response.put("nextPlanName", next.nextPlanId() == null ? null : planName(guildId, next.nextPlanId()));
        response.put("nextSemesterType", next.nextPlanId() == null ? null : semesterType(guildId, next.nextPlanId()));
        return response;
    }

    private String semesterType(String guildId, String planId) {
        SwitchSemesterSettings settings = adminSettingsService.get(configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
        return settings.resultingSemesterType(planId);
    }

    private String planName(String guildId, String planId) {
        SwitchSemesterSettings settings = adminSettingsService.get(configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
        SwitchSemesterSettings.SwitchPlan plan = settings.findPlan(planId);
        return plan == null ? null : plan.name();
    }

    private List<CategoryRef> categories(String guildId, int rocnik, int semester) {
        return adminSettingsService.get(mappingKey(guildId, rocnik, semester), new TypeReference<List<CategoryRef>>() { }, List.of());
    }

    private String mappingKey(String guildId, int rocnik, int semester) {
        return "semester_cats_" + guildId + "_" + rocnik + "_" + semester;
    }

    static String configsKey(String guildId) {
        return "cmd_settings_" + guildId + "_switchsemester";
    }

    public record SaveMappingRequest(String guildId, Integer rocnik, Integer semester, List<CategoryRef> categories) {
    }

    public record SetVisibilityRequest(String guildId, Integer rocnik, Integer semester, Boolean visible) {
    }

    public record SetCurrentPlanRequest(String guildId, String planId) {
    }
}
