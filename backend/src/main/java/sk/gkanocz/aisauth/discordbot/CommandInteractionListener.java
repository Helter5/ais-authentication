package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single entry point for every slash command: gates on allowed guilds, maintenance mode,
 * per-guild command state and permissions, then delegates to the domain handlers and writes
 * one "commands" audit log entry per invocation. Mirrors the old bot's interactionCreate.js.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CommandInteractionListener extends ListenerAdapter {

    private static final Set<String> KNOWN_COMMANDS = Set.of(
            "verify", "code", "find", "manualverify", "warn", "mywarns", "info", "user", "pridatpredmet");
    private static final Set<String> OMIT_OPTIONS = Set.of("code");
    private static final Set<String> REDACT_OPTIONS = Set.of("email");

    private final VerificationSlashCommandListener verificationCommandHandler;
    private final WarnSlashCommandListener warnCommandHandler;
    private final UtilityCommandListener utilityCommandHandler;
    private final SubjectRoleSlashCommandListener subjectRoleCommandHandler;
    private final AdminSettingsService adminSettingsService;
    private final AuditLogService auditLogService;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        long startedAt = System.currentTimeMillis();

        if (event.getGuild() == null) {
            event.reply("This bot only works in server channels, not in DMs.").queue();
            return;
        }
        if (!KNOWN_COMMANDS.contains(event.getName())) {
            return;
        }

        String guildId = event.getGuild().getId();

        if (!adminSettingsService.isGuildAllowed(guildId)) {
            logCommand(event, "blocked", startedAt, "Server is not allowed");
            event.reply("**Bot príkazy nie sú povolené na tomto serveri.**").queue();
            return;
        }

        if (adminSettingsService.isMaintenanceMode()) {
            logCommand(event, "blocked", startedAt, "Maintenance mode");
            event.reply("Bot is currently in maintenance mode. All commands are temporarily disabled.")
                    .setEphemeral(true).queue();
            return;
        }

        Map<String, Boolean> states = adminSettingsService.get(
                "cmd_states_" + guildId, new TypeReference<Map<String, Boolean>>() { }, Map.of());
        if (Boolean.FALSE.equals(states.get("/" + event.getName()))) {
            logCommand(event, "blocked", startedAt, "Command is disabled");
            event.reply("This command is currently disabled.").setEphemeral(true).queue();
            return;
        }

        CommandPermissions permissions = adminSettingsService.get(
                "cmd_perms_" + guildId + "_" + event.getName(), CommandPermissions.class, CommandPermissions.empty());
        Member member = event.getMember();
        List<String> memberRoleIds = member == null ? List.of() : member.getRoles().stream().map(Role::getId).toList();
        boolean isAdministrator = member != null && member.hasPermission(Permission.ADMINISTRATOR);
        String blockReason = permissions.blockReason(event.getChannel().getId(), memberRoleIds, isAdministrator);
        if (blockReason != null) {
            logCommand(event, "blocked", startedAt, blockReason);
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        Map<String, Object> commandSettings = adminSettingsService.get(
                "cmd_settings_" + guildId + "_" + event.getName(), new TypeReference<Map<String, Object>>() { }, Map.of());
        Boolean ephemeralOverride = commandSettings.get("ephemeral") instanceof Boolean bool ? bool : null;

        try {
            verificationCommandHandler.dispatch(event, ephemeralOverride);
            warnCommandHandler.dispatch(event, ephemeralOverride);
            utilityCommandHandler.dispatch(event, ephemeralOverride);
            subjectRoleCommandHandler.dispatch(event, ephemeralOverride);
            logCommand(event, "success", startedAt, null);
        } catch (Exception e) {
            log.error("Command execution error in guild {}", guildId, e);
            logCommand(event, "error", startedAt, null);
        }
    }

    private void logCommand(SlashCommandInteractionEvent event, String status, long startedAt, String blockedReason) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("status", status);
            details.put("durationMs", System.currentTimeMillis() - startedAt);
            details.put("options", serializeOptions(event.getOptions()));
            details.put("blockedReason", blockedReason);

            String commandLabel = event.getSubcommandName() == null
                    ? "/" + event.getName() : "/" + event.getName() + " " + event.getSubcommandName();
            auditLogService.log(new AuditLogEntry(
                    "commands", commandLabel,
                    event.getGuild().getId(), event.getGuild().getName(),
                    event.getChannel().getId(), event.getChannel().getName(),
                    event.getUser().getId(), event.getUser().getName(),
                    details));
        } catch (Exception e) {
            log.error("Failed to write command audit log", e);
        }
    }

    private List<Map<String, Object>> serializeOptions(List<OptionMapping> options) {
        return options.stream()
                .filter(option -> !OMIT_OPTIONS.contains(option.getName()))
                .map(option -> {
                    Map<String, Object> serialized = new LinkedHashMap<>();
                    serialized.put("name", option.getName());
                    serialized.put("type", option.getType().name());
                    serialized.put("value",
                            REDACT_OPTIONS.contains(option.getName()) ? "[redacted]" : option.getAsString());
                    return serialized;
                })
                .toList();
    }
}
