package sk.gkanocz.aisauth.automod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.discordbot.DiscordModerationService;
import sk.gkanocz.aisauth.discordbot.EventLogEmbedSender;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.ticket.TicketService;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors handleHackedAccountTrap from the old bot's messageCreate.js: a message posted in the
 * configured trap channel triggers an optional DM, an optional incident channel with ticket
 * controls, deletion of the trigger message and the author's recent messages, and a moderation
 * action (timeout/kick/ban), all logged to the "automod" audit category and the guild's spam log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HackedAccountTrapListener extends ListenerAdapter {

    private final HackedAccountTrapService hackedAccountTrapService;
    private final AdminSettingsService adminSettingsService;
    private final AuditLogService auditLogService;
    private final TicketService ticketService;
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

        if (settings.incidentChannelEnabled()) {
            TextChannel existingChannel = findExistingIncidentChannel(guild, authorId);
            if (existingChannel != null && ticketService.hasOpenTicket(existingChannel.getId())) {
                handleRepeatTrigger(event, existingChannel, settings, authorId, authorTag);
                return;
            }
        }

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

        TextChannel incidentChannel = settings.incidentChannelEnabled()
                ? createOrReuseIncidentChannel(guild, settings, member) : null;
        if (incidentChannel != null) {
            postIncidentIntro(incidentChannel, guild, settings, member, dmSent);
            ticketService.postTicketControls(incidentChannel, guild.getId(), authorId);
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

        int totalDeleted = settings.deleteRecentMessages() ? sweepRecentMessages(guild, authorId, settings.cleanupMinutes()) : 0;

        // Anything other than "ban"/"kick" has always meant "timeout" here (the settings UI only
        // ever offers these three), so normalize before handing off to the shared moderation service.
        String action = "ban".equals(settings.action()) || "kick".equals(settings.action()) ? settings.action() : "timeout";
        DiscordModerationService.Outcome outcome = moderationService.apply(
                member, action, settings.reason(), Duration.ofMinutes(settings.timeoutMinutes()));
        boolean actionSucceeded = outcome != null && outcome.success();
        String actionError = null;
        String result;
        if (outcome == null) {
            result = action + " failed";
        } else if (outcome.success()) {
            result = outcome.detail() + " applied";
        } else {
            actionError = outcome.detail();
            result = action + " failed";
        }
        if (actionError != null) {
            log.error("HackedAccountTrap: Failed to {} user {}: {}", action, authorId, actionError);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", actionSucceeded ? "success" : "failed");
        details.put("trigger", triggerContent);
        details.put("result", result);
        details.put("message", triggerContent);
        details.put("moderationAction", action);
        details.put("timeoutMinutes", "timeout".equals(action) ? settings.timeoutMinutes() : null);
        details.put("triggerDeleted", triggerDeleted);
        details.put("messagesDeleted", totalDeleted);
        details.put("deleteIntervalMinutes", settings.cleanupMinutes());
        details.put("dmSent", dmSent);
        details.put("incidentChannelId", incidentChannel == null ? null : incidentChannel.getId());
        details.put("error", actionError);
        auditLogService.log(new AuditLogEntry(
                "automod", "Hacked account trap " + action, guild.getId(), guild.getName(),
                triggerChannelId, event.getChannel().getName(), authorId, authorTag, details));

        sendSpamLog(guild, authorId, authorTag, triggerChannelId, triggerContent, result,
                totalDeleted, incidentChannel, imageAttachmentUrl, settings.cleanupMinutes());
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

    private TextChannel findExistingIncidentChannel(Guild guild, String authorId) {
        String existingChannelId = adminSettingsService.get(incidentChannelKey(guild.getId(), authorId), String.class, null);
        return existingChannelId == null ? null : guild.getTextChannelById(existingChannelId);
    }

    private String incidentChannelKey(String guildId, String authorId) {
        return "hacked_trap_incident_channel_" + guildId + "_" + authorId;
    }

    private TextChannel createOrReuseIncidentChannel(Guild guild, HackedAccountTrapSettings settings, Member author) {
        String authorId = author.getId();
        try {
            TextChannel channel = findExistingIncidentChannel(guild, authorId);

            List<String> managerRoleIds = managerRoleIds(guild.getId());
            if (channel != null) {
                applyIncidentOverwrites(channel, guild, managerRoleIds, settings, author);
                return channel;
            }

            String name = sanitizeChannelName(settings.incidentChannelNameTemplate(), author);
            Category category = settings.incidentChannelCategoryId() == null
                    ? null : guild.getCategoryById(settings.incidentChannelCategoryId());

            TextChannel created = (category == null ? guild.createTextChannel(name) : guild.createTextChannel(name, category))
                    .complete();
            applyIncidentOverwrites(created, guild, managerRoleIds, settings, author);
            adminSettingsService.set(incidentChannelKey(guild.getId(), authorId), created.getId());
            return created;
        } catch (Exception e) {
            log.error("HackedAccountTrap: Failed to create/reuse incident channel: {}", e.getMessage());
            return null;
        }
    }

    /**
     * The user is already being handled in an open incident channel (e.g. still timed out, or
     * staff hasn't closed the ticket yet) — re-running the DM/moderation-action/intro pipeline on
     * every repeat post would just spam the same channel, so only tag them there instead.
     */
    private void handleRepeatTrigger(
            MessageReceivedEvent event, TextChannel incidentChannel, HackedAccountTrapSettings settings,
            String authorId, String authorTag) {
        try {
            incidentChannel.sendMessage("<@" + authorId + "> posted in the trap channel again.").complete();
        } catch (Exception e) {
            log.error("HackedAccountTrap: Failed to post repeat-trigger notice: {}", e.getMessage());
        }
        if (settings.deleteTriggerMessage()) {
            try {
                event.getMessage().delete().complete();
            } catch (Exception e) {
                log.error("HackedAccountTrap: Failed to delete trigger message: {}", e.getMessage());
            }
        }
        auditLogService.log(new AuditLogEntry(
                "automod", "Hacked account trap repeat trigger", event.getGuild().getId(), event.getGuild().getName(),
                event.getChannel().getId(), event.getChannel().getName(), authorId, authorTag,
                Map.of("incidentChannelId", incidentChannel.getId())));
    }

    private void applyIncidentOverwrites(
            TextChannel channel, Guild guild, List<String> managerRoleIds,
            HackedAccountTrapSettings settings, Member author) {
        channel.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.VIEW_CHANNEL).complete();
        for (String roleId : managerRoleIds) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                channel.upsertPermissionOverride(role).grant(Permission.VIEW_CHANNEL).complete();
            }
        }
        if (settings.incidentChannelIncludeUser()) {
            channel.upsertPermissionOverride(author).grant(Permission.VIEW_CHANNEL).complete();
        }
    }

    private String sanitizeChannelName(String template, Member author) {
        String name = template.replace("{user}", author.getUser().getName()).replace("{id}", author.getId())
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-");
        if (name.length() > 90) {
            name = name.substring(0, 90);
        }
        return name.isBlank() ? "hacked-" + author.getId() : name;
    }

    private void postIncidentIntro(
            TextChannel channel, Guild guild, HackedAccountTrapSettings settings, Member author, boolean dmSent) {
        String mentionText = settings.incidentChannelTagRoles() && !settings.incidentChannelTagRoleIds().isEmpty()
                ? settings.incidentChannelTagRoleIds().stream().map(id -> "<@&" + id + ">")
                        .reduce((a, b) -> a + " " + b).orElse("")
                : "";
        // Keep both the plain username and a real mention so the affected user actually gets pinged.
        String userReference = author.getUser().getName() + " (<@" + author.getId() + ">)";
        String customMessage = settings.incidentChannelMessage().replace("{server}", guild.getName())
                .replace("{user}", userReference).trim();
        String intro = (mentionText + "\n" + customMessage).trim();
        if (!intro.isBlank()) {
            try {
                channel.sendMessage(intro).complete();
            } catch (Exception e) {
                log.error("HackedAccountTrap: Failed to send incident channel message: {}", e.getMessage());
            }
        }
        if (settings.dmUser() && settings.incidentChannelPostDmStatus()) {
            try {
                channel.sendMessage(dmSent ? "DM was successfully sent." : "Failed to send DM.").complete();
            } catch (Exception e) {
                log.error("HackedAccountTrap: Failed to post DM status in incident channel: {}", e.getMessage());
            }
        }
    }

    private int sweepRecentMessages(Guild guild, String authorId, int cleanupMinutes) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(cleanupMinutes));
        int totalDeleted = 0;
        for (TextChannel channel : guild.getTextChannels()) {
            if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_HISTORY, Permission.MESSAGE_MANAGE)) {
                continue;
            }
            try {
                List<Message> recent = channel.getHistory().retrievePast(100).complete();
                List<Message> toDelete = recent.stream()
                        .filter(m -> m.getAuthor().getId().equals(authorId) && !m.getTimeCreated().toInstant().isBefore(cutoff))
                        .toList();
                if (toDelete.isEmpty()) {
                    continue;
                }
                channel.purgeMessages(toDelete);
                totalDeleted += toDelete.size();
            } catch (Exception e) {
                log.error("SpamTrap: Failed to clear messages in channel {}: {}", channel.getId(), e.getMessage());
            }
        }
        return totalDeleted;
    }

    private void sendSpamLog(
            Guild guild, String authorId, String authorTag, String triggerChannelId,
            String triggerContent, String result, int totalDeleted, TextChannel incidentChannel,
            String imageAttachmentUrl, int cleanupMinutes) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0xFF0000))
                .setTitle("Hacked Account Trap Triggered")
                .addField("User", "<@" + authorId + "> (" + authorTag + ")", true)
                .addField("User ID", authorId, true)
                .addField("Trap Channel", "<#" + triggerChannelId + ">", true)
                .addField("Message", triggerContent, false)
                .addField("Action", result, true)
                .addField("Messages Deleted", totalDeleted + " (last " + cleanupMinutes + " min)", true);
        if (incidentChannel != null) {
            embed.addField("Incident Channel", "<#" + incidentChannel.getId() + ">", true);
        }
        if (imageAttachmentUrl != null) {
            embed.setImage(imageAttachmentUrl);
        }
        eventLogEmbedSender.send(guild, LogEventType.HACKED_ACCOUNT_TRAP_TRIGGERED, embed);
    }

    private List<String> managerRoleIds(String guildId) {
        return adminSettingsService.dashboardSettings(guildId).managerRoleIds();
    }
}
