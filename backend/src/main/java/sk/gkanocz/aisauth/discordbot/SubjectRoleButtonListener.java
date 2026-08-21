package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequest;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequestRepository;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleRequestStatus;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleService;

import java.util.LinkedHashMap;
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
    private static final String REJECT_MODAL_PREFIX = "subjrole:rejectreason:";
    private static final String REASON_INPUT_ID = "reason";

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

        if ("reject".equals(action)) {
            // Doesn't decide yet - onModalInteraction does the actual reject once the admin submits
            // the reason they typed. event.getMessageId() (the original request embed) rides along
            // in the modal's custom id since a modal submission is a separate interaction with no
            // link back to the button that opened it.
            TextInput reason = TextInput.create(REASON_INPUT_ID, "Dôvod zamietnutia", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("napr. duplicitná žiadosť, nespĺňa podmienky, rola už neexistuje...")
                    .setRequired(false)
                    .setMaxLength(300)
                    .build();
            Modal modal = Modal.create(REJECT_MODAL_PREFIX + requestId + ":" + event.getMessageId(), "Zamietnuť žiadosť")
                    .addActionRow(reason)
                    .build();
            event.replyModal(modal).queue();
            return;
        }
        if (!"approve".equals(action)) {
            return;
        }
        event.deferEdit().queue();

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

        guild.addRoleToMember(target, role).reason("/pridatpredmet approved by " + admin.getId()).queue(
                success -> {
                    subjectRoleService.decide(request, SubjectRoleRequestStatus.APPROVED, admin.getId());
                    finalizeMessage(event, request, true, admin);
                    logDecision(guild, request, "approved", admin);
                    dmBestEffort(guild, request.getDiscordId(), "Tvoja žiadosť o rolu predmetu **" + role.getName()
                            + "** bola schválená administrátorom <@" + admin.getId() + ">.");
                },
                failure -> {
                    // Request stays PENDING (not decided) so the buttons remain clickable for a retry -
                    // marking it APPROVED here would tell the DB and the student the role was granted
                    // when the Discord call actually failed (role moved above the bot, rate limit, ...).
                    log.warn("Guild {}: failed to grant role {} to {} for request {}",
                            guild.getId(), role.getId(), target.getId(), request.getId(), failure);
                    event.getHook().sendMessage(
                                    "Nepodarilo sa prideliť rolu (skontroluj, či je rola nižšie ako najvyššia rola bota), skús to znova.")
                            .setEphemeral(true).queue();
                });
    }

    /** Reject flow's second step - fired once the admin submits the reason they typed into the
     *  modal the button handler opened. The original request embed lives in a different (non-
     *  ephemeral) message than this modal submission, so it's looked up by id in the same channel
     *  and edited directly, rather than through this interaction's own hook (which can only ever
     *  touch a reply to this submission, not the message the button was originally on). */
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        if (!modalId.startsWith(REJECT_MODAL_PREFIX)) {
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

        String[] parts = modalId.substring(REJECT_MODAL_PREFIX.length()).split(":", 2);
        long requestId;
        try {
            requestId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return;
        }
        String originalMessageId = parts.length > 1 ? parts[1] : null;

        Optional<SubjectRoleRequest> found = subjectRoleRequestRepository.findByIdAndGuildId(requestId, guild.getId());
        if (found.isEmpty() || found.get().getStatus() != SubjectRoleRequestStatus.PENDING) {
            event.reply("Táto žiadosť už bola vybavená.").setEphemeral(true).queue();
            return;
        }
        SubjectRoleRequest request = found.get();
        String typedReason = event.getValue(REASON_INPUT_ID) == null ? "" : event.getValue(REASON_INPUT_ID).getAsString().trim();
        String reason = typedReason.isEmpty() ? null : typedReason;

        subjectRoleService.decide(request, SubjectRoleRequestStatus.REJECTED, admin.getId());
        event.reply(reason != null ? "Zamietnuté (" + reason + ")." : "Zamietnuté.").setEphemeral(true).queue();
        if (originalMessageId != null) {
            event.getChannel().retrieveMessageById(originalMessageId).queue(
                    msg -> msg.editMessageEmbeds(buildDecisionEmbed(request, false, admin, reason)).setComponents(List.of()).queue(
                            s -> { }, f -> { }),
                    f -> log.warn("Guild {}: couldn't find original request embed {} to update", guild.getId(), originalMessageId));
        }
        logDecision(guild, request, "rejected", admin, reason);
        dmBestEffort(guild, request.getDiscordId(), "Tvoja žiadosť o rolu predmetu **" + roleDisplayName(guild, request.getSubjectCode())
                + "** bola zamietnutá administrátorom <@" + admin.getId() + ">."
                + (reason != null ? "\nDôvod: " + reason : ""));
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
        event.getHook().editOriginalEmbeds(buildDecisionEmbed(request, approved, admin, null)).setComponents(List.of()).queue();
    }

    private MessageEmbed buildDecisionEmbed(
            SubjectRoleRequest request, boolean approved, Member admin, String reason) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(approved ? EventLogEmbedSender.SUCCESS : EventLogEmbedSender.DANGER)
                .setTitle("Subject Role " + (approved ? "Approved" : "Rejected"))
                .addField("User", EventLogEmbedSender.userField(request.getDiscordId(), request.getDiscordId()), true)
                .addField("Subject", "<@&" + request.getSubjectCode() + ">", true)
                .addField("Decided by", "<@" + admin.getId() + ">", true);
        if (reason != null) {
            embed.addField("Reason", reason, false);
        }
        return embed.build();
    }

    /**
     * Internal audit trail only (dashboard's Logs page) - not posted to a Discord channel, since
     * finalizeMessage() already edits the original Decision-channel embed in place to show the
     * outcome, and a separate "Subject Role Decision" post duplicated that with nothing new in it.
     */
    private void logDecision(Guild guild, SubjectRoleRequest request, String outcome, Member admin) {
        logDecision(guild, request, outcome, admin, null);
    }

    private void logDecision(Guild guild, SubjectRoleRequest request, String outcome, Member admin, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("subject", request.getSubjectCode());
        details.put("decidedBy", admin.getId());
        details.put("outcome", outcome);
        if (reason != null) {
            details.put("reason", reason);
        }
        auditLogService.log(new AuditLogEntry(
                "subjectrole", "/pridatpredmet " + outcome,
                guild.getId(), guild.getName(),
                null, null, request.getDiscordId(), request.getDiscordId(),
                details));
    }
}
