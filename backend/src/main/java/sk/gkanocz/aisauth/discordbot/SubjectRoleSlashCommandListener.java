package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.semester.SemesterOperationService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.shared.DbErrors;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequest;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleService;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /pridatpredmet - self-service role grant for students repeating or double-enrolled in a subject
 * (e.g. 2nd-year student who also has a 3rd-year subject). Options are STRING+autocomplete
 * (SubjectRoleAutoCompleteListener), not Discord's native ROLE type, because ROLE pickers always
 * show every guild role with no way to filter them - autocomplete is the only way to make the
 * picker only ever suggest the admin-approved subject roles. Autocomplete is just a UI hint though,
 * so every submitted value is re-validated against the same allowlist here regardless of how it
 * was entered. The command refuses to run at all until an admin has configured both the allowed
 * subject roles AND who may approve/reject overflow requests (SubjectRoleSettings) - there's no
 * point letting people queue up requests nobody is set up to act on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SubjectRoleSlashCommandListener {

    private static final String GENERIC_ERROR_MESSAGE = "Nastala neočakávaná chyba, skús to prosím neskôr.";
    private static final String DEFAULT_BLOCKED_MESSAGE = "🚫 Nie je v zozname povolených predmetov: {predmety}";
    private static final List<String> SUBJECT_OPTIONS = List.of("predmet1", "predmet2", "predmet3", "predmet4", "predmet5");
    /** {channel=123456789012345678} - same convention as /code's configurable message. */
    private static final Pattern CHANNEL_TOKEN = Pattern.compile("\\{channel=(\\d+)}");

    private final SubjectRoleService subjectRoleService;
    private final AdminSettingsService adminSettingsService;
    private final EventLogEmbedSender eventLogEmbedSender;
    /** @Lazy breaks a startup cycle: DiscordBotService -> ... -> this -> SemesterOperationService ->
     *  DashboardAuditLogger -> DiscordBotService. Resolved on first actual use, well after both sides exist. */
    @Lazy
    private final SemesterOperationService semesterOperationService;

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        if (!SubjectRoleSettings.COMMAND_NAME.equals(event.getName())) {
            return;
        }
        handle(event, ephemeralOverride);
    }

    private void handle(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();
        try {
            process(event);
        } catch (Exception e) {
            DbErrors.report(log, "/pridatpredmet failed", e);
            event.getHook().sendMessage(GENERIC_ERROR_MESSAGE).queue();
        }
    }

    private void process(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (member == null) {
            event.getHook().sendMessage("Nepodarilo sa nájsť tvoje členstvo na tomto serveri.").queue();
            return;
        }

        String semesterType = semesterOperationService.currentSemesterType(guild.getId());
        Set<String> allowedRoleIds = SubjectRoleSettings.allowedRoleIds(adminSettingsService, guild.getId(), semesterType);
        Set<String> approverRoleIds = SubjectRoleSettings.approverRoleIds(adminSettingsService, guild.getId());
        boolean logChannelSet = eventLogEmbedSender.resolveChannel(guild, LogEventType.SUBJECT_ROLE_PENDING_APPROVAL) != null;
        if (!SubjectRoleSettings.anyAllowedRoleIdsConfigured(adminSettingsService, guild.getId())
                || approverRoleIds.isEmpty() || !logChannelSet) {
            event.getHook().sendMessage("Tento príkaz ešte nie je nastavený administrátorom "
                    + "(chýbajú povolené predmetové role, schvaľovatelia, alebo Log Channel v Settings), skús to neskôr.").queue();
            return;
        }

        List<String> missingBotPerms = BotPermissionChecker.missingPermissions(guild, Permission.MANAGE_ROLES);
        if (!missingBotPerms.isEmpty()) {
            event.getHook().sendMessage("Bot nemá potrebné oprávnenia (" + String.join(", ", missingBotPerms)
                    + "), kontaktuj admina.").queue();
            return;
        }

        List<RoleSelection> selections = selectedRoles(event, guild);
        LocalDateTime resetAt = subjectRoleService.semesterResetAt(guild.getId());
        long activeCount = subjectRoleService.activeCount(guild.getId(), member.getId(), resetAt);

        Set<String> bypassLimitRoleIds = SubjectRoleSettings.bypassLimitRoleIds(adminSettingsService, guild.getId());
        boolean bypassLimit = member.getRoles().stream().map(Role::getId).anyMatch(bypassLimitRoleIds::contains);
        int autoGrantLimit = SubjectRoleSettings.autoGrantLimit(adminSettingsService, guild.getId());
        long effectiveLimit = bypassLimit ? Long.MAX_VALUE : autoGrantLimit;

        List<String> granted = new ArrayList<>();
        List<String> alreadyHave = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();

        for (RoleSelection selection : selections) {
            if (selection.role() == null) {
                blocked.add(selection.rawValue());
                continue;
            }
            Role role = selection.role();
            if (!allowedRoleIds.contains(role.getId())) {
                blocked.add(role.getName());
                continue;
            }
            if (member.getRoles().contains(role)) {
                alreadyHave.add(role.getName());
                continue;
            }
            if (!BotPermissionChecker.rolesAboveBot(guild, role).isEmpty()) {
                unavailable.add(role.getName());
                postUnreachableRoleAlert(guild, member, role);
                continue;
            }

            if (activeCount < effectiveLimit) {
                guild.addRoleToMember(member, role).reason("/pridatpredmet").queue();
                subjectRoleService.recordGranted(guild.getId(), member.getId(), role.getId());
                granted.add(role.getName());
                activeCount++;
            } else {
                SubjectRoleRequest request = subjectRoleService.recordPending(guild.getId(), member.getId(), role.getId());
                pending.add(role.getName());
                activeCount++;
                postApprovalRequest(guild, member, request, role);
            }
        }

        String blockedLine = blocked.isEmpty() ? null : blockedMessage(event, guild.getId(), blocked);
        event.getHook().sendMessage(
                formatSummary(granted, alreadyHave, pending, blockedLine, unavailable, autoGrantLimit)).queue();
    }

    /**
     * "cmd_settings_<guild>_pridatpredmet" -> "message" - configurable via the card's Settings
     * modal, same {channel=<id>} + placeholder-token convention as /code's success message.
     */
    private String blockedMessage(SlashCommandInteractionEvent event, String guildId, List<String> blocked) {
        Map<String, Object> settings = adminSettingsService.get(
                "cmd_settings_" + guildId + "_pridatpredmet", new TypeReference<Map<String, Object>>() { }, Map.of());
        Object configured = settings.get("message");
        String template = (configured instanceof String s && !s.isBlank()) ? s : DEFAULT_BLOCKED_MESSAGE;

        Matcher matcher = CHANNEL_TOKEN.matcher(template);
        String withChannels = matcher.replaceAll(result -> "<#" + result.group(1) + ">");

        return withChannels
                .replace("{user}", event.getUser().getName())
                .replace("{server}", event.getGuild().getName())
                .replace("{predmety}", String.join(", ", blocked));
    }

    private record RoleSelection(String rawValue, Role role) {
    }

    /** Distinct roles across the (up to 5) predmetN options - autocomplete doesn't stop the same value being picked twice. */
    private List<RoleSelection> selectedRoles(SlashCommandInteractionEvent event, Guild guild) {
        Set<String> seenIds = new LinkedHashSet<>();
        List<RoleSelection> selections = new ArrayList<>();
        for (String optionName : SUBJECT_OPTIONS) {
            OptionMapping option = event.getOption(optionName);
            if (option == null) {
                continue;
            }
            String value = option.getAsString().trim();
            if (value.isEmpty()) {
                continue;
            }
            Role role = resolveRole(guild, value);
            String dedupeKey = role != null ? role.getId() : value;
            if (seenIds.add(dedupeKey)) {
                selections.add(new RoleSelection(value, role));
            }
        }
        return selections;
    }

    /**
     * guild.getRoleById(String) throws NumberFormatException for anything that isn't a snowflake -
     * expected here, since autocomplete is only a suggestion, and a user can still type free text
     * (e.g. "admin") and hit Enter without picking a suggestion. Treated the same as "no such role".
     */
    private Role resolveRole(Guild guild, String value) {
        try {
            return guild.getRoleById(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatSummary(List<String> granted, List<String> alreadyHave, List<String> pending,
                                  String blockedLine, List<String> unavailable, int autoGrantLimit) {
        StringBuilder sb = new StringBuilder();
        if (!granted.isEmpty()) {
            sb.append("✅ Pridelené role: ").append(String.join(", ", granted)).append('\n');
        }
        if (!alreadyHave.isEmpty()) {
            sb.append("ℹ️ Už si mal: ").append(String.join(", ", alreadyHave)).append('\n');
        }
        if (!pending.isEmpty()) {
            sb.append("⏳ Čaká na schválenie adminom (už máš ").append(autoGrantLimit)
                    .append("+ predmetových rolí tento semester): ").append(String.join(", ", pending)).append('\n');
        }
        if (blockedLine != null) {
            sb.append(blockedLine).append('\n');
        }
        if (!unavailable.isEmpty()) {
            sb.append("⚠️ Nedalo sa spracovať, admin bol upozornený: ").append(String.join(", ", unavailable)).append('\n');
        }
        return sb.isEmpty() ? "Nič sa nespracovalo." : sb.toString().stripTrailing();
    }

    private void postApprovalRequest(Guild guild, Member member, SubjectRoleRequest request, Role role) {
        TextChannel channel = eventLogEmbedSender.resolveChannel(guild, LogEventType.SUBJECT_ROLE_PENDING_APPROVAL);
        if (channel == null) {
            log.warn("Guild {}: no log channel configured for SUBJECT_ROLE_PENDING_APPROVAL, request {} needs manual review",
                    guild.getId(), request.getId());
            return;
        }
        EmbedBuilder embed = EventLogEmbedSender.base(EventLogEmbedSender.WARNING, "Subject Role Needs Approval", null)
                .addField("User", EventLogEmbedSender.userField(member.getId(), member.getUser().getName()), true)
                .addField("Subject", role.getAsMention(), true)
                .addField("Request ID", "#" + request.getId(), true);
        channel.sendMessageEmbeds(embed.build())
                .setActionRow(
                        Button.success("subjrole:approve:" + request.getId(), "Approve"),
                        Button.danger("subjrole:reject:" + request.getId(), "Reject"))
                .queue();
    }

    private void postUnreachableRoleAlert(Guild guild, Member member, Role role) {
        TextChannel channel = eventLogEmbedSender.resolveChannel(guild, LogEventType.SUBJECT_ROLE_MISSING_ROLE);
        if (channel == null) {
            log.warn("Guild {}: no log channel configured for SUBJECT_ROLE_MISSING_ROLE (role {} above bot)", guild.getId(), role.getId());
            return;
        }
        EmbedBuilder embed = EventLogEmbedSender.base(EventLogEmbedSender.DANGER, "Subject Role Unavailable",
                        "Rola sedí vyššie ako najvyššia rola bota - presuň ju nižšie v zozname rolí.")
                .addField("User", EventLogEmbedSender.userField(member.getId(), member.getUser().getName()), true)
                .addField("Subject", role.getAsMention(), true);
        channel.sendMessageEmbeds(embed.build()).queue();
    }
}
