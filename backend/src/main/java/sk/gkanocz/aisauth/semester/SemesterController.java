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
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
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
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final AuditLogService auditLogService;
    private final SemesterVisibilityService semesterVisibilityService;

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
        logDashboardChange(claims, request.guildId(), "Updated semester category mapping", Map.of(
                "year", request.rocnik(), "semester", request.semester(), "before", previous, "after", request.categories()));
        return Map.of("success", true);
    }

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

        logDashboardChange(claims, request.guildId(), (visible ? "Showed" : "Hid") + " semester categories", Map.of(
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

    @GetMapping("/semester/configs")
    public Map<String, Object> getConfigs(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return adminSettingsService.get(configsKey(guildId), new TypeReference<Map<String, Object>>() { }, Map.of());
    }

    @PostMapping("/semester/configs")
    public Map<String, Boolean> saveConfigs(@AuthenticationPrincipal Claims claims, @RequestBody Map<String, Object> body) {
        String guildId = (String) body.get("guildId");
        guildAccessService.assertCanManageGuild(claims, guildId);
        Map<String, Object> settings = new HashMap<>(body);
        settings.remove("guildId");
        Map<String, Object> previous = adminSettingsService.get(configsKey(guildId), new TypeReference<Map<String, Object>>() { }, Map.of());
        adminSettingsService.set(configsKey(guildId), settings);
        logDashboardChange(claims, guildId, "Updated switchsemester settings", Map.of("before", previous, "after", settings));
        return Map.of("success", true);
    }

    @GetMapping("/semester/access")
    public Map<String, Object> getAccess(@AuthenticationPrincipal Claims claims, @RequestParam(required = false) String guildId) {
        if (guildId == null) {
            return Map.of("allowed", false, "reason", "no_guild");
        }
        if (!guildAccessService.canManageGuild(claims, guildId)) {
            return Map.of("allowed", false, "reason", "no_permission");
        }
        String channelId = adminSettingsService.get("recap_channel_semester_" + guildId, String.class, null);
        if (channelId == null) {
            return Map.of("allowed", false, "reason", "no_channel");
        }
        return Map.of("allowed", true);
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

    private void logDashboardChange(Claims claims, String guildId, String action, Map<String, Object> details) {
        try {
            Guild guild = discordBotService.jda().map(jda -> jda.getGuildById(guildId)).orElse(null);
            auditLogService.log(new AuditLogEntry(
                    "dashboard", action, guildId, guild == null ? null : guild.getName(),
                    null, null, claims.getSubject(), claims.get("username", String.class), details));
        } catch (Exception e) {
            // best-effort audit trail; never block the underlying change on a logging failure
        }
    }

    public record SaveMappingRequest(String guildId, Integer rocnik, Integer semester, List<CategoryRef> categories) {
    }

    public record SetVisibilityRequest(String guildId, Integer rocnik, Integer semester, Boolean visible) {
    }
}
