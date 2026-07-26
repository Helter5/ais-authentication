package sk.gkanocz.aisauth.discordbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.settings.AdminSetting;
import sk.gkanocz.aisauth.settings.AdminSettingRepository;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommandManagementController {

    private static final List<String> LEGACY_SETTING_KEYS = List.of("responseMessage", "dmMessage", "logChannelId");

    private final AdminSettingsService adminSettingsService;
    private final AdminSettingRepository adminSettingRepository;
    private final GuildAccessService guildAccessService;
    private final AuditLogService auditLogService;
    private final DiscordBotService discordBotService;
    private final ObjectMapper objectMapper;

    @GetMapping("/command-states")
    public Map<String, Boolean> getCommandStates(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return adminSettingsService.get("cmd_states_" + guildId, new TypeReference<Map<String, Boolean>>() { }, Map.of());
    }

    @PatchMapping("/command-states")
    public Map<String, Boolean> setCommandState(@AuthenticationPrincipal Claims claims, @RequestBody CommandStateRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        String key = "cmd_states_" + request.guildId();
        Map<String, Boolean> states = new HashMap<>(
                adminSettingsService.get(key, new TypeReference<Map<String, Boolean>>() { }, Map.of()));
        Boolean previous = states.getOrDefault(request.command(), true);
        states.put(request.command(), request.enabled());
        adminSettingsService.set(key, states);
        logDashboardChange(claims, (request.enabled() ? "Enabled " : "Disabled ") + request.command() + " command", request.guildId(),
                Map.of("command", request.command(), "before", previous, "after", request.enabled()));
        return Map.of("success", true);
    }

    /**
     * Toggling many commands at once (the dashboard's "enable/disable all") must not go through
     * N parallel PATCH /command-states calls: each does a read-modify-write of the same
     * cmd_states_<guildId> blob, so concurrent requests race and only the last writer's single
     * command survives. This applies every change in one read-modify-write instead.
     */
    @PatchMapping("/command-states/bulk")
    public Map<String, Boolean> setCommandStates(@AuthenticationPrincipal Claims claims, @RequestBody BulkCommandStateRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        String key = "cmd_states_" + request.guildId();
        Map<String, Boolean> states = new HashMap<>(
                adminSettingsService.get(key, new TypeReference<Map<String, Boolean>>() { }, Map.of()));
        states.putAll(request.commands());
        adminSettingsService.set(key, states);
        logDashboardChange(claims, "Bulk updated command states", request.guildId(), Map.of("commands", request.commands()));
        return Map.of("success", true);
    }

    @GetMapping("/command-permissions/summary")
    public List<CommandPermissionsSummary> getPermissionsSummary(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        String prefix = "cmd_perms_" + guildId + "_";
        return adminSettingRepository.findByKeyStartingWith(prefix).stream()
                .map(setting -> toSummary(setting, prefix))
                .filter(summary -> summary != null)
                .toList();
    }

    private CommandPermissionsSummary toSummary(AdminSetting setting, String prefix) {
        CommandPermissions permissions;
        try {
            permissions = objectMapper.readValue(setting.getValue(), CommandPermissions.class);
        } catch (Exception e) {
            return null;
        }
        if (!permissions.isConfigured()) {
            return null;
        }
        String command = setting.getKey().substring(prefix.length());
        return new CommandPermissionsSummary(
                command, permissions.allowedRoles(), permissions.ignoredRoles(),
                permissions.allowedChannels(), permissions.ignoredChannels(), permissions.adminOnly());
    }

    @GetMapping("/command-permissions")
    public CommandPermissions getPermissions(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId, @RequestParam String command) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return adminSettingsService.get("cmd_perms_" + guildId + "_" + command, CommandPermissions.class, CommandPermissions.empty());
    }

    @PostMapping("/command-permissions")
    public Map<String, Boolean> savePermissions(@AuthenticationPrincipal Claims claims, @RequestBody SavePermissionsRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        String key = "cmd_perms_" + request.guildId() + "_" + request.command();
        CommandPermissions previous = adminSettingsService.get(key, CommandPermissions.class, CommandPermissions.empty());
        CommandPermissions next = new CommandPermissions(
                request.allowedChannels() == null ? List.of() : request.allowedChannels(),
                request.ignoredChannels() == null ? List.of() : request.ignoredChannels(),
                request.allowedRoles() == null ? List.of() : request.allowedRoles(),
                request.ignoredRoles() == null ? List.of() : request.ignoredRoles(),
                Boolean.TRUE.equals(request.adminOnly()));
        adminSettingsService.set(key, next);
        logDashboardChange(claims, "Updated /" + request.command() + " permissions", request.guildId(),
                Map.of("command", request.command(), "before", previous, "after", next));
        return Map.of("success", true);
    }

    @GetMapping("/command-settings")
    public Map<String, Object> getCommandSettings(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId, @RequestParam String command) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return adminSettingsService.get(
                "cmd_settings_" + guildId + "_" + command, new TypeReference<Map<String, Object>>() { }, Map.of());
    }

    @PostMapping("/command-settings")
    public Map<String, Boolean> saveCommandSettings(@AuthenticationPrincipal Claims claims, @RequestBody Map<String, Object> body) {
        String guildId = (String) body.get("guildId");
        String command = (String) body.get("command");
        guildAccessService.assertCanManageGuild(claims, guildId);
        String key = "cmd_settings_" + guildId + "_" + command;
        Map<String, Object> previous = adminSettingsService.get(key, new TypeReference<Map<String, Object>>() { }, Map.of());

        Map<String, Object> settings = new HashMap<>(body);
        settings.remove("guildId");
        settings.remove("command");
        LEGACY_SETTING_KEYS.forEach(settings::remove);

        adminSettingsService.set(key, settings);
        logDashboardChange(claims, "Updated /" + command + " settings", guildId,
                Map.of("command", command, "before", previous, "after", settings));
        return Map.of("success", true);
    }

    @PostMapping("/deploy-commands")
    public Map<String, Object> deployCommands(@AuthenticationPrincipal Claims claims, @RequestBody GuildIdRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());
        JDA jda = guild.getJDA();

        List<Command> globalCommands = jda.retrieveCommands().complete();
        Map<String, Boolean> states = adminSettingsService.get(
                "cmd_states_" + request.guildId(), new TypeReference<Map<String, Boolean>>() { }, Map.of());

        List<CommandData> guildCommands = globalCommands.stream()
                .map(command -> toGuildCommandData(command, request.guildId(), states))
                .toList();

        guild.updateCommands().addCommands(guildCommands).complete();
        return Map.of("success", true, "count", guildCommands.size());
    }

    private CommandData toGuildCommandData(Command command, String guildId, Map<String, Boolean> states) {
        CommandPermissions permissions = adminSettingsService.get(
                "cmd_perms_" + guildId + "_" + command.getName(), CommandPermissions.class, CommandPermissions.empty());
        boolean disabled = Boolean.FALSE.equals(states.get("/" + command.getName()));

        SlashCommandData data = Commands.slash(command.getName(), command.getDescription());
        command.getOptions().forEach(option -> data.addOptions(
                new OptionData(option.getType(), option.getName(), option.getDescription(), option.isRequired())));

        if (disabled || permissions.adminOnly()) {
            data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
        }
        return data;
    }

    private void logDashboardChange(Claims claims, String action, String guildId, Map<String, Object> details) {
        try {
            Guild guild = discordBotService.jda().map(jda -> jda.getGuildById(guildId)).orElse(null);
            auditLogService.log(new AuditLogEntry(
                    "dashboard", action, guildId, guild == null ? null : guild.getName(),
                    null, null, claims.getSubject(), claims.get("username", String.class), details));
        } catch (Exception e) {
            // best-effort audit trail; never block the underlying change on a logging failure
        }
    }

    public record CommandStateRequest(String guildId, String command, boolean enabled) {
    }

    public record BulkCommandStateRequest(String guildId, Map<String, Boolean> commands) {
    }

    public record SavePermissionsRequest(
            String guildId, String command,
            List<String> allowedChannels, List<String> ignoredChannels,
            List<String> allowedRoles, List<String> ignoredRoles, Boolean adminOnly) {
    }

    public record GuildIdRequest(String guildId) {
    }

    public record CommandPermissionsSummary(
            String command,
            @JsonProperty("allowedRoles") List<String> allowedRoles,
            @JsonProperty("ignoredRoles") List<String> ignoredRoles,
            @JsonProperty("allowedChannels") List<String> allowedChannels,
            @JsonProperty("ignoredChannels") List<String> ignoredChannels,
            boolean adminOnly) {
    }
}
