package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequest;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequestRepository;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequestStatus;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleService;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Approve/Reject buttons on the embed SubjectRoleSlashCommandListener posts for a 3rd+ subject
 * role request. Admin-only (Discord command-level defaults don't apply to component interactions,
 * so this is checked here explicitly - same reasoning as every other admin action in this bot).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubjectRoleButtonListener extends ListenerAdapter {

    private static final String PREFIX = "subjrole:";

    private final SubjectRoleRequestRepository subjectRoleRequestRepository;
    private final SubjectRoleService subjectRoleService;
    private final AuditLogService auditLogService;
    private final EventLogEmbedSender eventLogEmbedSender;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith(PREFIX)) {
            return;
        }
        Guild guild = event.getGuild();
        Member admin = event.getMember();
        if (guild == null || admin == null) {
            return;
        }
        if (!admin.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("Len administrátori môžu rozhodovať o žiadostiach.").setEphemeral(true).queue();
            return;
        }

        String[] parts = componentId.split(":", 3);
        String action = parts[1];
        long requestId;
        try {
            requestId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        Optional<SubjectRoleRequest> found = subjectRoleRequestRepository.findByIdAndGuildId(requestId, guild.getId());
        if (found.isEmpty() || found.get().getStatus() != SubjectRoleRequestStatus.PENDING) {
            event.reply("Táto žiadosť už bola vybavená.").setEphemeral(true).queue();
            return;
        }
        SubjectRoleRequest request = found.get();
        event.deferEdit().queue();

        if ("reject".equals(action)) {
            subjectRoleService.decide(request, SubjectRoleRequestStatus.REJECTED, admin.getId());
            finalizeMessage(event, request, false, admin);
            logDecision(guild, request, "rejected", admin);
            return;
        }
        if (!"approve".equals(action)) {
            return;
        }

        List<Role> matches = guild.getRolesByName(request.getSubjectCode(), true);
        if (matches.isEmpty()) {
            event.getHook().sendMessage("Rola pre `" + request.getSubjectCode()
                    + "` už neexistuje, over to pred schválením.").setEphemeral(true).queue();
            return;
        }
        Member target = guild.getMemberById(request.getDiscordId());
        if (target == null) {
            subjectRoleService.decide(request, SubjectRoleRequestStatus.REJECTED, admin.getId());
            finalizeMessage(event, request, false, admin);
            event.getHook().sendMessage("Používateľ už nie je na serveri, žiadosť bola automaticky zamietnutá.")
                    .setEphemeral(true).queue();
            return;
        }

        guild.addRoleToMember(target, matches.get(0)).reason("/addpredmet approved by " + admin.getId()).queue();
        subjectRoleService.decide(request, SubjectRoleRequestStatus.APPROVED, admin.getId());
        finalizeMessage(event, request, true, admin);
        logDecision(guild, request, "approved", admin);

        target.getUser().openPrivateChannel().queue(
                dm -> dm.sendMessage("Tvoja žiadosť o rolu predmetu **" + request.getSubjectCode() + "** bola schválená.")
                        .queue(s -> { }, f -> { }),
                f -> { });
    }

    private void finalizeMessage(ButtonInteractionEvent event, SubjectRoleRequest request, boolean approved, Member admin) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(approved ? EventLogEmbedSender.SUCCESS : EventLogEmbedSender.DANGER)
                .setTitle("Subject Role " + (approved ? "Approved" : "Rejected"))
                .addField("User", EventLogEmbedSender.userField(request.getDiscordId(), request.getDiscordId()), true)
                .addField("Subject", request.getSubjectCode(), true)
                .addField("Decided by", "<@" + admin.getId() + ">", true);
        event.getHook().editOriginalEmbeds(embed.build()).setComponents(List.of()).queue();
    }

    private void logDecision(Guild guild, SubjectRoleRequest request, String outcome, Member admin) {
        auditLogService.log(new AuditLogEntry(
                "subjectrole", "/addpredmet " + outcome,
                guild.getId(), guild.getName(),
                null, null, request.getDiscordId(), request.getDiscordId(),
                Map.of("subject", request.getSubjectCode(), "decidedBy", admin.getId(), "outcome", outcome)));

        eventLogEmbedSender.send(guild, LogEventType.SUBJECT_ROLE_DECIDED,
                EventLogEmbedSender.base(new Color(0x6366F1), "Subject Role Decision", null)
                        .addField("User", "<@" + request.getDiscordId() + ">", true)
                        .addField("Subject", request.getSubjectCode(), true)
                        .addField("Decision", outcome + " by <@" + admin.getId() + ">", true));
    }
}
