package sk.gkanocz.aisauth.automod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.discordbot.DiscordModerationService;
import sk.gkanocz.aisauth.discordbot.EventLogEmbedSender;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;

import java.awt.Color;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors handleHackedAccountTrap from the old bot's messageCreate.js: a message posted in the
 * configured trap channel triggers an optional DM, deletion of the trigger message, and a
 * permanent ban (optionally deleting the author's recent message history via Discord's own
 * ban-duration options), all logged to the "automod" audit category and the guild's spam log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HackedAccountTrapListener extends ListenerAdapter {

    private static final Map<Integer, String> DELETE_MESSAGE_HISTORY_LABELS = Map.of(
            3600, "Previous Hour",
            21600, "Previous 6 Hours",
            43200, "Previous 12 Hours",
            86400, "Previous 24 Hours",
            259200, "Previous 3 Days",
            604800, "Previous 7 Days");

    private final HackedAccountTrapService hackedAccountTrapService;
    private final AdminSettingsService adminSettingsService;
    private final AuditLogService auditLogService;
    private final DiscordModerationService moderationService;
    private final EventLogEmbedSender eventLogEmbedSender;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        String guildId = event.getGuild().getId();
        if (adminSettingsService.isMaintenanceMode()) {
            return;
        }

        HackedAccountTrapSettings settings = hackedAccountTrapService.get(guildId);
        if (!settings.enabled() || settings.trapChannelId() == null
                || !event.getChannel().getId().equals(settings.trapChannelId())) {
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            return;
        }
        if (settings.ignoreAdministrators() && member.hasPermission(Permission.ADMINISTRATOR)) {
            return;
        }
        if (member.getRoles().stream().map(Role::getId).anyMatch(settings.exemptRoleIds()::contains)) {
            return;
        }

        try {
            trigger(event, settings, member);
        } catch (Exception e) {
            log.error("HackedAccountTrap: failed to process trigger for {}", event.getAuthor().getId(), e);
        }
    }

    private void trigger(MessageReceivedEvent event, HackedAccountTrapSettings settings, Member member) {
        Guild guild = event.getGuild();
        Message message = event.getMessage();
        String authorId = event.getAuthor().getId();
        String authorTag = event.getAuthor().getName();

        String imageAttachmentUrl = message.getAttachments().stream()
                .filter(Message.Attachment::isImage)
                .map(Message.Attachment::getUrl)
                .findFirst().orElse(null);
        String triggerContent = triggerContent(message);
        String triggerChannelId = event.getChannel().getId();

        boolean dmSent = false;
        if (settings.dmUser() && !settings.dmMessage().isBlank()) {
            dmSent = sendDm(event, guild, settings.dmMessage());
        }

        boolean triggerDeleted = false;
        if (settings.deleteTriggerMessage()) {
            try {
                message.delete().complete();
                triggerDeleted = true;
            } catch (Exception e) {
                log.error("HackedAccountTrap: Failed to delete trigger message: {}", e.getMessage());
            }
        }

        int deleteMessageSeconds = settings.deleteMessageHistory() ? settings.deleteMessageHistorySeconds() : 0;
        DiscordModerationService.Outcome outcome = moderationService.apply(
                member, "ban", settings.reason(), Duration.ZERO, deleteMessageSeconds);
        boolean actionSucceeded = outcome != null && outcome.success();
        String actionError = null;
        String result;
        if (outcome == null) {
            result = "ban failed";
        } else if (outcome.success()) {
            result = outcome.detail() + " applied";
        } else {
            actionError = outcome.detail();
            result = "ban failed";
        }
        if (actionError != null) {
            log.error("HackedAccountTrap: Failed to ban user {}: {}", authorId, actionError);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", actionSucceeded ? "success" : "failed");
        details.put("trigger", triggerContent);
        details.put("result", result);
        details.put("message", triggerContent);
        details.put("moderationAction", "ban");
        details.put("triggerDeleted", triggerDeleted);
        details.put("deleteMessageHistorySeconds", deleteMessageSeconds);
        details.put("dmSent", dmSent);
        details.put("error", actionError);
        auditLogService.log(new AuditLogEntry(
                "automod", "Hacked account trap ban", guild.getId(), guild.getName(),
                triggerChannelId, event.getChannel().getName(), authorId, authorTag, details));

        sendSpamLog(guild, authorId, authorTag, triggerChannelId, triggerContent, result,
                deleteMessageSeconds, imageAttachmentUrl);
    }

    private String triggerContent(Message message) {
        if (message.getContentRaw() != null && !message.getContentRaw().isBlank()) {
            return truncate(message.getContentRaw(), 1024);
        }
        if (!message.getAttachments().isEmpty()) {
            String urls = message.getAttachments().stream().map(Message.Attachment::getUrl)
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            return truncate(urls, 1024);
        }
        return "*[empty message]*";
    }

    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private boolean sendDm(MessageReceivedEvent event, Guild guild, String template) {
        try {
            String text = template.replace("{server}", guild.getName()).replace("{user}", event.getAuthor().getName());
            event.getAuthor().openPrivateChannel().complete().sendMessage(text).complete();
            return true;
        } catch (Exception e) {
            log.error("HackedAccountTrap: Failed to DM user {}: {}", event.getAuthor().getId(), e.getMessage());
            return false;
        }
    }

    private void sendSpamLog(
            Guild guild, String authorId, String authorTag, String triggerChannelId,
            String triggerContent, String result, int deleteMessageSeconds, String imageAttachmentUrl) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0xFF0000))
                .setTitle("Hacked Account Trap Triggered")
                .addField("User", "<@" + authorId + "> (" + authorTag + ")", true)
                .addField("User ID", authorId, true)
                .addField("Trap Channel", "<#" + triggerChannelId + ">", true)
                .addField("Message", triggerContent, false)
                .addField("Action", result, true)
                .addField("Message History Deleted",
                        deleteMessageSeconds == 0 ? "None" : DELETE_MESSAGE_HISTORY_LABELS.get(deleteMessageSeconds), true);
        if (imageAttachmentUrl != null) {
            embed.setImage(imageAttachmentUrl);
        }
        eventLogEmbedSender.send(guild, LogEventType.HACKED_ACCOUNT_TRAP_TRIGGERED, embed);
    }
}
