package sk.gkanocz.aisauth.ticket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.auth.AdminProperties;
import sk.gkanocz.aisauth.automod.HackedAccountTrapService;
import sk.gkanocz.aisauth.discordbot.EventLogEmbedSender;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import sk.gkanocz.aisauth.settings.LogEventType;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final IncidentTicketRepository incidentTicketRepository;
    private final AdminSettingsService adminSettingsService;
    private final AdminProperties adminProperties;
    private final HackedAccountTrapService hackedAccountTrapService;
    private final EventLogEmbedSender eventLogEmbedSender;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public boolean isTicketManager(String guildId, Member member) {
        if (adminProperties.superAdminIds().contains(member.getId())) {
            return true;
        }
        DashboardSettings settings = adminSettingsService.dashboardSettings(guildId);
        return member.getRoles().stream().map(Role::getId).anyMatch(settings.managerRoleIds()::contains);
    }

    public boolean hasOpenTicket(String channelId) {
        return incidentTicketRepository.findById(channelId).map(IncidentTicket::isOpen).orElse(false);
    }

    @Transactional
    public void postTicketControls(MessageChannel channel, String guildId, String userId) {
        IncidentTicket ticket = incidentTicketRepository.findById(channel.getId())
                .map(existing -> { existing.reopen(); return existing; })
                .orElseGet(() -> new IncidentTicket(channel.getId(), guildId, userId));
        renderControlsMessage(channel, ticket, "Ticket controls", List.of(closeButton(channel.getId())));
    }

    @Transactional
    public void postClosedControls(MessageChannel channel, IncidentTicket ticket) {
        renderControlsMessage(channel, ticket, "Support team ticket controls", closedButtons(ticket.getChannelId()));
    }

    /**
     * Edits the ticket's existing controls message in place when possible (so open/close/reopen
     * cycles and repeat automod triggers don't each leave behind a new "Ticket controls" message),
     * only sending and pinning a fresh one the first time or if the old message is gone.
     */
    private void renderControlsMessage(MessageChannel channel, IncidentTicket ticket, String text, List<Button> buttons) {
        String existingMessageId = ticket.getControlsMessageId();
        if (existingMessageId != null) {
            try {
                channel.editMessageById(existingMessageId, text).setActionRow(buttons).complete();
                incidentTicketRepository.save(ticket);
                return;
            } catch (Exception e) {
                log.warn("IncidentTicket: Controls message {} is gone, sending a new one: {}", existingMessageId, e.getMessage());
            }
        }
        Message sent = channel.sendMessage(text).addActionRow(buttons).complete();
        ticket.setControlsMessageId(sent.getId());
        incidentTicketRepository.save(ticket);
        try {
            sent.pin().queue();
        } catch (Exception e) {
            log.warn("IncidentTicket: Failed to pin controls message: {}", e.getMessage());
        }
    }

    public boolean includeUserSetting(String guildId) {
        return hackedAccountTrapService.get(guildId).incidentChannelIncludeUser();
    }

    public String closedCategoryId(String guildId) {
        return hackedAccountTrapService.get(guildId).incidentChannelClosedCategoryId();
    }

    public String openCategoryId(String guildId) {
        return hackedAccountTrapService.get(guildId).incidentChannelCategoryId();
    }

    public Button closeButton(String channelId) {
        return Button.secondary("ticket_close:" + channelId, "Close").withEmoji(Emoji.fromUnicode("🔒"));
    }

    public List<Button> closeConfirmButtons(String channelId) {
        return List.of(
                Button.danger("ticket_close_confirm:" + channelId, "Close"),
                Button.secondary("ticket_close_cancel:" + channelId, "Cancel"));
    }

    public List<Button> closedButtons(String channelId) {
        return List.of(
                Button.secondary("ticket_transcript:" + channelId, "Transcript").withEmoji(Emoji.fromUnicode("📄")),
                Button.secondary("ticket_open:" + channelId, "Open").withEmoji(Emoji.fromUnicode("🔓")),
                Button.danger("ticket_delete:" + channelId, "Delete").withEmoji(Emoji.fromUnicode("⛔")));
    }

    private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&(\\d+)>");

    // Confirmation prompts add nothing to a transcript once resolved (they get edited into
    // "Ticket closed by ..."/"Close cancelled." anyway) or, if abandoned mid-flow, are just noise.
    private static final Set<String> IGNORED_BOT_MESSAGES = Set.of("Are you sure you would like to close this ticket?");

    public List<TranscriptMessage> buildTranscript(TextChannel channel) {
        List<Message> history = channel.getIterableHistory().cache(false).takeAsync(500).join();
        Guild guild = channel.getGuild();
        String botId = channel.getJDA().getSelfUser().getId();
        List<TranscriptMessage> messages = new ArrayList<>(history.size());
        for (Message message : history) {
            List<TranscriptMessage.Attachment> attachments = message.getAttachments().stream()
                    .map(a -> new TranscriptMessage.Attachment(a.getUrl(), a.getFileName()))
                    .toList();
            String content = resolveRoleMentions(guild, message.getContentRaw());
            boolean isBot = message.getAuthor().getId().equals(botId);
            if (isBot && (IGNORED_BOT_MESSAGES.contains(content) || (content.isBlank() && attachments.isEmpty()))) {
                continue;
            }
            messages.add(new TranscriptMessage(
                    message.getAuthor().getId(), message.getAuthor().getName(), message.getAuthor().getEffectiveAvatarUrl(),
                    message.getTimeCreated().toString(), content, attachments,
                    isBot ? classifyEventKind(content) : null));
        }
        messages.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return messages;
    }

    /**
     * Every bot-authored message is a lifecycle event, never a "chat message", so the transcript
     * viewer can always render it as a compact line rather than a full bubble. The bot's ticket
     * control/status messages are fixed, known texts and get a specific, named kind; anything else
     * bot-authored (e.g. the admin-configurable Hacked Account Trap intro message) falls back to
     * the generic "bot_notice" kind instead of going unclassified.
     */
    private String classifyEventKind(String content) {
        if (content.equals("Ticket controls") || content.equals("Support team ticket controls")) {
            return "ticket_controls";
        }
        if (content.startsWith("Ticket closed by ")) {
            return "ticket_closed";
        }
        if (content.startsWith("Ticket reopened by ")) {
            return "ticket_reopened";
        }
        if (content.equals("Close cancelled.")) {
            return "close_cancelled";
        }
        if (content.endsWith("posted in the trap channel again.")) {
            return "repeat_trigger";
        }
        if (content.equals("DM was successfully sent.")) {
            return "dm_sent";
        }
        if (content.equals("Failed to send DM.")) {
            return "dm_failed";
        }
        return "bot_notice";
    }

    private String resolveRoleMentions(Guild guild, String content) {
        Matcher matcher = ROLE_MENTION_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Role role = guild.getRoleById(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement("@" + (role == null ? "deleted-role" : role.getName())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String writeTranscript(List<TranscriptMessage> messages) {
        return objectMapper.writeValueAsString(messages);
    }

    public boolean saveTranscriptToLogChannel(TextChannel sourceChannel, IncidentTicket ticket, String url) {
        List<TranscriptMessage> messages = ticket.getTranscript() == null
                ? List.of()
                : objectMapper.readValue(ticket.getTranscript(), TranscriptMessage.LIST_TYPE);

        Map<String, Integer> authorCounts = new LinkedHashMap<>();
        int attachmentsSaved = 0;
        for (TranscriptMessage message : messages) {
            String authorKey = message.authorTag() + " (" + message.authorId() + ")";
            authorCounts.merge(authorKey, 1, Integer::sum);
            attachmentsSaved += message.attachments().size();
        }
        String userInfo = authorCounts.isEmpty() ? "No messages" : authorCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> e.getValue() + " - " + e.getKey())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("No messages");

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x2f3136))
                .setTitle("Transcript Saved")
                .addField("Server-Info", "Server: " + sourceChannel.getGuild().getName() + " (" + ticket.getGuildId() + ")"
                        + "\nChannel: " + sourceChannel.getName() + " (" + ticket.getChannelId() + ")"
                        + "\nMessages: " + messages.size() + "\nAttachments Saved: " + attachmentsSaved, false)
                .addField("User-Info", userInfo, false);

        return eventLogEmbedSender.send(
                sourceChannel.getGuild(), LogEventType.TICKET_TRANSCRIPT_SAVED, embed, Button.link(url, "View Transcript"));
    }

    public String frontendUrl() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
