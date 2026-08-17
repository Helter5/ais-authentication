package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import tools.jackson.core.type.TypeReference;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class GuildAccessAdminController {

    private final GuildAccessService guildAccessService;
    private final AdminSettingsService adminSettingsService;
    private final DiscordBotService discordBotService;

    @SuperAdminAccess
    @GetMapping("/manager-roles")
    public ManagerRolesResponse getManagerRoles(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertSuperAdmin(claims);
        Guild guild = discordBotService.requireGuild(guildId);

        List<RoleResponse> roles = guild.getRoles().stream()
                .filter(role -> !role.getId().equals(guild.getId()))
                .sorted(Comparator.comparingInt(Role::getPosition).reversed())
                .map(role -> new RoleResponse(role.getId(), role.getName(), hexColor(role), role.getPosition()))
                .toList();

        DashboardSettings settings = adminSettingsService.dashboardSettings(guildId);
        return new ManagerRolesResponse(roles, settings.managerRoleIds());
    }

    @SuperAdminAccess
    @PostMapping("/manager-roles")
    public Map<String, Object> setManagerRoles(
            @AuthenticationPrincipal Claims claims, @RequestBody UpdateManagerRolesRequest request) {
        guildAccessService.assertSuperAdmin(claims);
        Guild guild = discordBotService.requireGuild(request.guildId());

        List<String> validRoleIds = guild.getRoles().stream().map(Role::getId).toList();
        boolean allValid = request.managerRoleIds().stream()
                .allMatch(roleId -> validRoleIds.contains(roleId) && !roleId.equals(guild.getId()));
        if (!allValid) {
            throw InvalidRequestException.withMessage("One or more manager roles are invalid");
        }

        List<String> unique = List.copyOf(new LinkedHashSet<>(request.managerRoleIds()));
        adminSettingsService.set(dashboardSettingsKey(request.guildId()), new DashboardSettings(unique));
        return Map.of("success", true, "managerRoleIds", unique);
    }

    @SuperAdminAccess
    @GetMapping("/allowed-guilds")
    public Map<String, Object> getAllowedGuilds(@AuthenticationPrincipal Claims claims) {
        guildAccessService.assertSuperAdmin(claims);
        return Map.of("guildIds", allowedGuildIds());
    }

    /**
     * Narrower than /allowed-guilds (which exposes the whole list and is super-admin only) - any
     * manager needs to know whether the guild they're currently looking at is actually allowed,
     * since that's what silently gates every command and module event for it.
     */
    @ManagerAccess
    @GetMapping("/guild-allowed")
    public Map<String, Boolean> isGuildAllowed(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return Map.of("allowed", adminSettingsService.isGuildAllowed(guildId));
    }

    @SuperAdminAccess
    @PostMapping("/allowed-guilds")
    public Map<String, Object> setAllowedGuilds(
            @AuthenticationPrincipal Claims claims, @RequestBody UpdateAllowedGuildsRequest request) {
        guildAccessService.assertSuperAdmin(claims);
        if (request.guildIds() == null || request.guildIds().isEmpty()
                || request.guildIds().stream().anyMatch(id -> !id.matches("\\d{17,20}"))) {
            throw InvalidRequestException.withMessage("At least one valid Discord guild ID is required");
        }

        List<String> unique = List.copyOf(new LinkedHashSet<>(request.guildIds()));
        adminSettingsService.set("allowed_guild_ids", unique);
        return Map.of("success", true, "guildIds", unique);
    }

    private String dashboardSettingsKey(String guildId) {
        return "dashboard_settings_" + guildId;
    }

    private List<String> allowedGuildIds() {
        return adminSettingsService.get("allowed_guild_ids", new TypeReference<List<String>>() { }, List.of());
    }

    private String hexColor(Role role) {
        return String.format("#%06X", role.getColorRaw() & 0xFFFFFF);
    }

    public record RoleResponse(String id, String name, String color, int position) {
    }

    public record ManagerRolesResponse(List<RoleResponse> roles, List<String> managerRoleIds) {
    }

    public record UpdateManagerRolesRequest(String guildId, List<String> managerRoleIds) {
    }

    public record UpdateAllowedGuildsRequest(List<String> guildIds) {
    }
}
