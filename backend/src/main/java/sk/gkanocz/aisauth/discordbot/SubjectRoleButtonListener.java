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
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequest;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequestRepository;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequestStatus;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Approve/Reject buttons on the embed SubjectRoleSlashCommandListener posts for a 3rd+ subject
 * role request. Allowed for server Administrators (safety net, same as every other admin action in
 * this bot) plus whoever holds one of the guild's configured approverRoleIds
 * (SubjectRoleSettings) - Discord command-level defaults don't apply to component interactions, so
 * this is checked here explicitly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubjectRoleButtonListener extends ListenerAdapter {

    private static final String PREFIX = "subjrole:";

    private final SubjectRoleRequestRepository subjectRoleRequestRepository;
    private final SubjectRoleService subjectRoleService;
    private final AdminSettingsService adminSettingsService;
    private final AuditLogService auditLogService;

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
        if (!canDecide(admin, guild.getId())) {
            event.reply("Nemáš oprávnenie rozhodovať o týchto žiadostiach.").setEphemeral(true).queue();
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
            dmBestEffort(guild, request.getDiscordId(), "Tvoja žiadosť o rolu predmetu **" + roleDisplayName(guild, request.getSubjectCode())
                    + "** bola zamietnutá administrátorom <@" + admin.getId() + ">.");
            return;
        }
        if (!"approve".equals(action)) {
            return;
        }

        Role role = guild.getRoleById(request.getSubjectCode());
        if (role == null) {
            event.getHook().sendMessage("Rola už neexistuje, over to pred schválením.").setEphemeral(true).queue();
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

        guild.addRoleToMember(target, role).reason("/pridatpredmet approved by " + admin.getId()).queue();
        subjectRoleService.decide(request, SubjectRoleRequestStatus.APPROVED, admin.getId());
        finalizeMessage(event, request, true, admin);
        logDecision(guild, request, "approved", admin);
        dmBestEffort(guild, request.getDiscordId(), "Tvoja žiadosť o rolu predmetu **" + role.getName()
                + "** bola schválená administrátorom <@" + admin.getId() + ">.");
    }

    private boolean canDecide(Member admin, String guildId) {
        if (admin.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        Set<String> approverRoleIds = SubjectRoleSettings.approverRoleIds(adminSettingsService, guildId);
        return admin.getRoles().stream().map(Role::getId).anyMatch(approverRoleIds::contains);
    }

    /** Role mentions don't resolve in DMs (Discord shows "@unknown-role" - no guild context to resolve against), so DM text uses the plain name instead. */
    private String roleDisplayName(Guild guild, String roleId) {
        Role role = guild.getRoleById(roleId);
        return role != null ? role.getName() : "neznáma rola";
    }

    private void dmBestEffort(Guild guild, String discordId, String content) {
        Member target = guild.getMemberById(discordId);
        if (target == null) {
            return;
        }
        target.getUser().openPrivateChannel().queue(
                dm -> dm.sendMessage(content).queue(s -> { }, f -> { }),
                f -> { });
    }

    private void finalizeMessage(ButtonInteractionEvent event, SubjectRoleRequest request, boolean approved, Member admin) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(approved ? EventLogEmbedSender.SUCCESS : EventLogEmbedSender.DANGER)
                .setTitle("Subject Role " + (approved ? "Approved" : "Rejected"))
                .addField("User", EventLogEmbedSender.userField(request.getDiscordId(), request.getDiscordId()), true)
                .addField("Subject", "<@&" + request.getSubjectCode() + ">", true)
                .addField("Decided by", "<@" + admin.getId() + ">", true);
        event.getHook().editOriginalEmbeds(embed.build()).setComponents(List.of()).queue();
    }

    /**
     * Internal audit trail only (dashboard's Logs page) - not posted to a Discord channel, since
     * finalizeMessage() already edits the original Decision-channel embed in place to show the
     * outcome, and a separate "Subject Role Decision" post duplicated that with nothing new in it.
     */
    private void logDecision(Guild guild, SubjectRoleRequest request, String outcome, Member admin) {
        auditLogService.log(new AuditLogEntry(
                "subjectrole", "/pridatpredmet " + outcome,
                guild.getId(), guild.getName(),
                null, null, request.getDiscordId(), request.getDiscordId(),
                Map.of("subject", request.getSubjectCode(), "decidedBy", admin.getId(), "outcome", outcome)));
    }
}
