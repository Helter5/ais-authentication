package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.rolesnapshot.RoleSnapshotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /refresh} - self-service role restore for a returning member. If they left, were kicked,
 * or were banned within the retention window (see RoleSnapshotService), this hands back whichever
 * of their previous roles are still trackable - roles carrying an elevated Discord permission, or
 * on the guild's dashboard exclude-list, are never among them (RoleSnapshotService's job, not
 * this listener's - this only drives the JDA call and the reply/audit/log around it).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RefreshSlashCommandListener {

    static final String DEFAULT_SUCCESS_MESSAGE = "Welcome back! Restored roles: {roles}";

    private final RoleSnapshotService roleSnapshotService;
    private final AuditLogService auditLogService;
    private final EventLogEmbedSender eventLogEmbedSender;
    private final AdminSettingsService adminSettingsService;

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        if (!"refresh".equals(event.getName())) {
            return;
        }
        boolean ephemeral = ephemeralOverride == null ? true : ephemeralOverride;
        event.deferReply(ephemeral).queue();

        Member member = event.getMember();
        if (member == null) {
            event.getHook().sendMessage("Could not resolve you as a server member.").queue();
            return;
        }

        RoleSnapshotService.RefreshOutcome outcome = roleSnapshotService.refresh(event.getGuild(), member);
        if (!outcome.hadSnapshot()) {
            event.getHook().sendMessage("Nothing to restore - no recent departure on record for you on this server.").queue();
            return;
        }
        if (outcome.rolesToApply().isEmpty()) {
            event.getHook().sendMessage(
                    "A departure snapshot was found, but none of those roles can be restored anymore "
                            + "(removed since, or no longer trackable).").queue();
            return;
        }

        try {
            event.getGuild().modifyMemberRoles(member, outcome.rolesToApply(), List.of()).complete();
        } catch (InsufficientPermissionException | HierarchyException e) {
            log.error("Failed to restore roles for {}: {}", member.getId(), e.getMessage());
            event.getHook().sendMessage(
                    "Found your previous roles, but the bot couldn't re-assign them (missing permission "
                            + "or role hierarchy). Contact an administrator - your snapshot is still saved, "
                            + "so /refresh can be retried once that's fixed.").queue();
            return;
        }
        // Only consumed now that the roles actually landed - a JDA failure above leaves the
        // snapshot in place so the member isn't punished for a bot-side permission problem.
        roleSnapshotService.consumeSnapshot(event.getGuild().getId(), member.getId());

        String roleMentions = outcome.rolesToApply().stream()
                .map(Role::getAsMention).reduce((a, b) -> a + " " + b).orElse("");
        event.getHook().sendMessage(successMessage(event, roleMentions)).queue();

        // "verification", not "commands" - the generic per-invocation "/refresh" row (status,
        // options, duration) is already written by CommandInteractionListener.logCommand for every
        // slash command; a second "commands"-category entry here collided with that table's shape
        // (no status/options of its own) and rendered as a stray "unknown"-status row next to it.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "success");
        details.put("result", "Restored: " + outcome.rolesToApply().stream().map(Role::getName).collect(Collectors.joining(", ")));
        auditLogService.log(new AuditLogEntry(
                "verification", "/refresh restored roles",
                event.getGuild().getId(), event.getGuild().getName(),
                event.getChannel().getId(), event.getChannel().getName(),
                member.getId(), member.getUser().getName(), details));

        eventLogEmbedSender.send(event.getGuild(), LogEventType.ROLE_REFRESH_RESTORED,
                EventLogEmbedSender.base(EventLogEmbedSender.SUCCESS, "Roles Restored via /refresh", null)
                        .addField("User", EventLogEmbedSender.userField(member.getId(), member.getUser().getName()), true)
                        .addField("Roles", roleMentions, false));
    }

    /**
     * "cmd_settings_<guild>_refresh" -> "message" - configurable via the /refresh card's Settings
     * modal, falls back to {@link #DEFAULT_SUCCESS_MESSAGE} when unset/blank. Same
     * {user}/{server} tokens as /code, plus {roles} for the restored role mentions.
     */
    private String successMessage(SlashCommandInteractionEvent event, String roleMentions) {
        Map<String, Object> settings = adminSettingsService.get(
                "cmd_settings_" + event.getGuild().getId() + "_refresh", new TypeReference<Map<String, Object>>() { }, Map.of());
        Object configuredMessage = settings.get("message");
        String template = (configuredMessage instanceof String s && !s.isBlank()) ? s : DEFAULT_SUCCESS_MESSAGE;

        return template
                .replace("{user}", event.getUser().getName())
                .replace("{server}", event.getGuild().getName())
                .replace("{roles}", roleMentions);
    }
}
