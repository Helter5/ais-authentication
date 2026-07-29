package sk.gkanocz.aisauth.ticket;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
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
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final IncidentTicketRepository incidentTicketRepository;
    private final AdminSettingsService adminSettingsService;
    private final AdminProperties adminProperties;
    private final LogRoutingService logRoutingService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public boolean isTicketManager(String guildId, Member member) {
        if (adminProperties.superAdminIds().contains(member.getId())) {
            return true;
        }
        DashboardSettings settings = adminSettingsService.get(
                "dashboard_settings_" + guildId, DashboardSettings.class, DashboardSettings.empty());
        return member.getRoles().stream().map(Role::getId).anyMatch(settings.managerRoleIds()::contains);
    }

    @Transactional
    public void postTicketControls(MessageChannel channel, String guildId, String userId) {
        incidentTicketRepository.findById(channel.getId()).ifPresentOrElse(
                IncidentTicket::reopen,
                () -> incidentTicketRepository.save(new IncidentTicket(channel.getId(), guildId, userId)));
        channel.sendMessage("Ticket controls").addActionRow(closeButton(channel.getId())).queue();
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

    public List<TranscriptMessage> buildTranscript(TextChannel channel) {
        List<Message> history = channel.getIterableHistory().cache(false).takeAsync(500).join();
        List<TranscriptMessage> messages = new ArrayList<>(history.size());
        for (Message message : history) {
            List<TranscriptMessage.Attachment> attachments = message.getAttachments().stream()
                    .map(a -> new TranscriptMessage.Attachment(a.getUrl(), a.getFileName()))
                    .toList();
            messages.add(new TranscriptMessage(
                    message.getAuthor().getId(), message.getAuthor().getName(),
                    message.getTimeCreated().toString(), message.getContentRaw(), attachments));
        }
        messages.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return messages;
    }

    public String writeTranscript(List<TranscriptMessage> messages) {
        return objectMapper.writeValueAsString(messages);
    }

    public boolean saveTranscriptToLogChannel(TextChannel sourceChannel, IncidentTicket ticket, String url) {
        String logChannelId = logRoutingService.channelIdFor(ticket.getGuildId(), LogEventType.TICKET_TRANSCRIPT_SAVED)
                .orElse(null);
        if (logChannelId == null) {
            return false;
        }
        TextChannel logChannel = sourceChannel.getGuild().getTextChannelById(logChannelId);
        if (logChannel == null) {
            return false;
        }

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

        try {
            logChannel.sendMessageEmbeds(embed.build())
                    .addActionRow(Button.link(url, "View Transcript"))
                    .queue();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String frontendUrl() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
