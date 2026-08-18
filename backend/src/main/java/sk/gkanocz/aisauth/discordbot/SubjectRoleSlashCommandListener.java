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
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequest;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * /addpredmet - self-service role grant for students repeating or double-enrolled in a subject
 * (e.g. 2nd-year student who also has a 3rd-year subject). First AUTO_GRANT_LIMIT subject roles
 * per guild+user+semester grant instantly; anything past that is held for admin approval via
 * SubjectRoleButtonListener, so the command can't be abused to self-grant an unbounded number of
 * subject roles. Deliberately a single command with a free-text option, not per-subject reaction
 * roles - the subject catalogue changes every semester and isn't enumerable as Discord command
 * choices or a fixed role-menu list.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SubjectRoleSlashCommandListener {

    private static final int MAX_SUBJECTS_PER_CALL = 10;
    private static final Pattern SPLIT = Pattern.compile("[,\\s]+");
    private static final Pattern VALID_CODE = Pattern.compile("[A-Za-z0-9]{1,32}");

    private final SubjectRoleService subjectRoleService;
    private final EventLogEmbedSender eventLogEmbedSender;

    void dispatch(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        if (!"addpredmet".equals(event.getName())) {
            return;
        }
        handle(event, ephemeralOverride);
    }

    private void handle(SlashCommandInteractionEvent event, Boolean ephemeralOverride) {
        event.deferReply(ephemeralOverride == null ? true : ephemeralOverride).queue();

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (member == null) {
            event.getHook().sendMessage("Nepodarilo sa nájsť tvoje členstvo na tomto serveri.").queue();
            return;
        }

        List<String> codes = parseCodes(event.getOption("predmety").getAsString());
        if (codes.isEmpty()) {
            event.getHook().sendMessage("Zadaj aspoň jeden platný kód predmetu, napr. `mat1 zfi`.").queue();
            return;
        }
        if (codes.size() > MAX_SUBJECTS_PER_CALL) {
            event.getHook().sendMessage("Naraz môžeš zadať max " + MAX_SUBJECTS_PER_CALL + " predmetov.").queue();
            return;
        }

        List<String> missingBotPerms = BotPermissionChecker.missingPermissions(guild, Permission.MANAGE_ROLES);
        if (!missingBotPerms.isEmpty()) {
            event.getHook().sendMessage("Bot nemá potrebné oprávnenia (" + String.join(", ", missingBotPerms)
                    + "), kontaktuj admina.").queue();
            return;
        }

        LocalDateTime resetAt = subjectRoleService.semesterResetAt(guild.getId());
        long activeCount = subjectRoleService.activeCount(guild.getId(), member.getId(), resetAt);

        List<String> granted = new ArrayList<>();
        List<String> alreadyHave = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();

        for (String code : codes) {
            List<Role> matches = guild.getRolesByName(code, true);
            if (matches.isEmpty()) {
                unavailable.add(code + " (rola neexistuje)");
                subjectRoleService.recordMissing(guild.getId(), member.getId(), code);
                postMissingRoleAlert(guild, member, code, "Rola pre tento predmet zatiaľ neexistuje.");
                continue;
            }
            Role role = matches.get(0);
            if (member.getRoles().contains(role)) {
                alreadyHave.add(code);
                continue;
            }
            if (!BotPermissionChecker.rolesAboveBot(guild, role).isEmpty()) {
                unavailable.add(code + " (bot na túto rolu nedosiahne)");
                postMissingRoleAlert(guild, member, code, "Rola existuje, ale sedí vyššie ako najvyššia rola bota - presuň ju nižšie.");
                continue;
            }

            if (activeCount < SubjectRoleService.AUTO_GRANT_LIMIT) {
                guild.addRoleToMember(member, role).reason("/addpredmet").queue();
                subjectRoleService.recordGranted(guild.getId(), member.getId(), code);
                granted.add(code);
                activeCount++;
            } else {
                SubjectRoleRequest request = subjectRoleService.recordPending(guild.getId(), member.getId(), code);
                pending.add(code);
                activeCount++;
                postApprovalRequest(guild, member, request);
            }
        }

        event.getHook().sendMessage(formatSummary(granted, alreadyHave, pending, unavailable)).queue();
    }

    private List<String> parseCodes(String raw) {
        Set<String> seen = new LinkedHashSet<>();
        for (String token : SPLIT.split(raw.trim())) {
            String code = token.toUpperCase();
            if (VALID_CODE.matcher(code).matches()) {
                seen.add(code);
            }
        }
        return List.copyOf(seen);
    }

    private String formatSummary(List<String> granted, List<String> alreadyHave, List<String> pending, List<String> unavailable) {
        StringBuilder sb = new StringBuilder();
        if (!granted.isEmpty()) {
            sb.append("✅ Pridelené role: ").append(String.join(", ", granted)).append('\n');
        }
        if (!alreadyHave.isEmpty()) {
            sb.append("ℹ️ Už si mal: ").append(String.join(", ", alreadyHave)).append('\n');
        }
        if (!pending.isEmpty()) {
            sb.append("⏳ Čaká na schválenie adminom (už máš ").append(SubjectRoleService.AUTO_GRANT_LIMIT)
                    .append("+ predmetových rolí tento semester): ").append(String.join(", ", pending)).append('\n');
        }
        if (!unavailable.isEmpty()) {
            sb.append("⚠️ Nedalo sa spracovať, admin bol upozornený: ").append(String.join(", ", unavailable)).append('\n');
        }
        return sb.isEmpty() ? "Nič sa nespracovalo." : sb.toString().stripTrailing();
    }

    private void postApprovalRequest(Guild guild, Member member, SubjectRoleRequest request) {
        TextChannel channel = eventLogEmbedSender.resolveChannel(guild, LogEventType.SUBJECT_ROLE_PENDING_APPROVAL);
        if (channel == null) {
            log.warn("Guild {}: no log channel configured for SUBJECT_ROLE_PENDING_APPROVAL, request {} needs manual review",
                    guild.getId(), request.getId());
            return;
        }
        EmbedBuilder embed = EventLogEmbedSender.base(EventLogEmbedSender.WARNING, "Subject Role Needs Approval", null)
                .addField("User", EventLogEmbedSender.userField(member.getId(), member.getUser().getName()), true)
                .addField("Subject", request.getSubjectCode(), true)
                .addField("Request ID", "#" + request.getId(), true);
        channel.sendMessageEmbeds(embed.build())
                .setActionRow(
                        Button.success("subjrole:approve:" + request.getId(), "Approve"),
                        Button.danger("subjrole:reject:" + request.getId(), "Reject"))
                .queue();
    }

    private void postMissingRoleAlert(Guild guild, Member member, String code, String reason) {
        TextChannel channel = eventLogEmbedSender.resolveChannel(guild, LogEventType.SUBJECT_ROLE_MISSING_ROLE);
        if (channel == null) {
            log.warn("Guild {}: no log channel configured for SUBJECT_ROLE_MISSING_ROLE ({}: {})", guild.getId(), code, reason);
            return;
        }
        EmbedBuilder embed = EventLogEmbedSender.base(EventLogEmbedSender.DANGER, "Subject Role Unavailable", reason)
                .addField("User", EventLogEmbedSender.userField(member.getId(), member.getUser().getName()), true)
                .addField("Subject", code, true);
        channel.sendMessageEmbeds(embed.build()).queue();
    }
}
