package sk.gkanocz.aisauth.ticket;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.auth.AdminProperties;
import sk.gkanocz.aisauth.automod.HackedAccountTrapService;
import sk.gkanocz.aisauth.automod.HackedAccountTrapSettings;
import sk.gkanocz.aisauth.discordbot.EventLogEmbedSender;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import sk.gkanocz.aisauth.settings.LogEventType;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private IncidentTicketRepository incidentTicketRepository;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private HackedAccountTrapService hackedAccountTrapService;
    @Mock
    private EventLogEmbedSender eventLogEmbedSender;
    @Mock
    private MessageChannel channel;
    @Mock
    private TextChannel textChannel;
    @Mock
    private Guild guild;
    @Mock
    private Member member;

    private TicketService service;

    @BeforeEach
    void setUp() {
        AdminProperties adminProperties = new AdminProperties(List.of("super-admin-1"));
        service = new TicketService(
                incidentTicketRepository, adminSettingsService, adminProperties, hackedAccountTrapService,
                eventLogEmbedSender, new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "frontendUrl", "https://example.com/");
    }

    // ---- isTicketManager ----

    @Test
    void isTicketManagerTrueForSuperAdmin() {
        when(member.getId()).thenReturn("super-admin-1");

        assertThat(service.isTicketManager("guild-1", member)).isTrue();

        verify(adminSettingsService, never()).dashboardSettings(anyString());
    }

    @Test
    void isTicketManagerTrueWhenMemberHasManagerRole() {
        when(member.getId()).thenReturn("user-1");
        when(adminSettingsService.dashboardSettings("guild-1")).thenReturn(new DashboardSettings(List.of("role-1")));
        Role role = mock(Role.class);
        when(role.getId()).thenReturn("role-1");
        when(member.getRoles()).thenReturn(List.of(role));

        assertThat(service.isTicketManager("guild-1", member)).isTrue();
    }

    @Test
    void isTicketManagerFalseOtherwise() {
        when(member.getId()).thenReturn("user-1");
        when(adminSettingsService.dashboardSettings("guild-1")).thenReturn(new DashboardSettings(List.of("role-1")));
        when(member.getRoles()).thenReturn(List.of());

        assertThat(service.isTicketManager("guild-1", member)).isFalse();
    }

    // ---- hasOpenTicket ----

    @Test
    void hasOpenTicketTrueWhenTicketOpen() {
        IncidentTicket ticket = new IncidentTicket("channel-1", "guild-1", "user-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));

        assertThat(service.hasOpenTicket("channel-1")).isTrue();
    }

    @Test
    void hasOpenTicketFalseWhenTicketClosed() {
        IncidentTicket ticket = new IncidentTicket("channel-1", "guild-1", "user-1");
        ticket.close("closer-1", null);
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));

        assertThat(service.hasOpenTicket("channel-1")).isFalse();
    }

    @Test
    void hasOpenTicketFalseWhenNoTicket() {
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.empty());

        assertThat(service.hasOpenTicket("channel-1")).isFalse();
    }

    // ---- postTicketControls / postClosedControls (renderControlsMessage) ----

    @Test
    @SuppressWarnings("unchecked")
    void postTicketControlsSendsFreshMessageWhenNoneExists() {
        when(channel.getId()).thenReturn("channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.empty());
        MessageCreateAction createAction = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        Message sent = mock(Message.class);
        when(sent.getId()).thenReturn("controls-msg-1");
        when(sent.pin()).thenReturn(mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class));
        when(createAction.complete()).thenReturn(sent);
        when(channel.sendMessage("Ticket controls")).thenReturn(createAction);

        service.postTicketControls(channel, "guild-1", "user-1");

        verify(incidentTicketRepository).save(any(IncidentTicket.class));
        verify(channel, never()).editMessageById(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void postTicketControlsReopensAndEditsExistingControlsMessage() {
        IncidentTicket ticket = new IncidentTicket("channel-1", "guild-1", "user-1");
        ticket.close("closer-1", "[]");
        ticket.setControlsMessageId("controls-msg-1");
        when(channel.getId()).thenReturn("channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        MessageEditAction editAction = mock(MessageEditAction.class, Mockito.RETURNS_SELF);
        when(editAction.complete()).thenReturn(mock(Message.class));
        when(channel.editMessageById("controls-msg-1", "Ticket controls")).thenReturn(editAction);

        service.postTicketControls(channel, "guild-1", "user-1");

        assertThat(ticket.isOpen()).isTrue();
        verify(channel, never()).sendMessage(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void renderControlsMessageFallsBackToSendWhenEditFails() {
        IncidentTicket ticket = new IncidentTicket("channel-1", "guild-1", "user-1");
        ticket.setControlsMessageId("stale-msg-1");
        when(channel.getId()).thenReturn("channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        MessageEditAction editAction = mock(MessageEditAction.class, Mockito.RETURNS_SELF);
        when(editAction.complete()).thenThrow(new RuntimeException("unknown message"));
        when(channel.editMessageById("stale-msg-1", "Ticket controls")).thenReturn(editAction);

        MessageCreateAction createAction = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        Message sent = mock(Message.class);
        when(sent.getId()).thenReturn("controls-msg-2");
        when(sent.pin()).thenReturn(mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class));
        when(createAction.complete()).thenReturn(sent);
        when(channel.sendMessage("Ticket controls")).thenReturn(createAction);

        service.postTicketControls(channel, "guild-1", "user-1");

        assertThat(ticket.getControlsMessageId()).isEqualTo("controls-msg-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void postClosedControlsUsesClosedControlsText() {
        IncidentTicket ticket = new IncidentTicket("channel-1", "guild-1", "user-1");
        ticket.close("closer-1", "[]");
        MessageCreateAction createAction = mock(MessageCreateAction.class, Mockito.RETURNS_SELF);
        Message sent = mock(Message.class);
        when(sent.getId()).thenReturn("controls-msg-3");
        when(sent.pin()).thenReturn(mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class));
        when(createAction.complete()).thenReturn(sent);
        when(channel.sendMessage("Support team ticket controls")).thenReturn(createAction);

        service.postClosedControls(channel, ticket);

        verify(channel).sendMessage("Support team ticket controls");
    }

    // ---- Hacked-account-trap-settings delegation ----

    @Test
    void includeUserSettingDelegatesToTrapSettings() {
        when(hackedAccountTrapService.get("guild-1")).thenReturn(HackedAccountTrapSettings.defaults("trap-1", 15));

        assertThat(service.includeUserSetting("guild-1")).isFalse();
    }

    @Test
    void closedCategoryIdDelegatesToTrapSettings() {
        when(hackedAccountTrapService.get("guild-1")).thenReturn(new HackedAccountTrapSettings(
                true, "trap-1", "timeout", 1440, true, true, 15, List.of(), true, false, "dm", "reason",
                false, "open-cat", "closed-cat", "name", false, "msg", false, false, List.of()));

        assertThat(service.closedCategoryId("guild-1")).isEqualTo("closed-cat");
        assertThat(service.openCategoryId("guild-1")).isEqualTo("open-cat");
    }

    // ---- button builders ----

    @Test
    void closeButtonUsesTicketCloseCustomId() {
        assertThat(service.closeButton("channel-1").getId()).isEqualTo("ticket_close:channel-1");
    }

    @Test
    void closeConfirmButtonsHaveConfirmAndCancel() {
        List<net.dv8tion.jda.api.interactions.components.buttons.Button> buttons = service.closeConfirmButtons("channel-1");

        assertThat(buttons).extracting(net.dv8tion.jda.api.interactions.components.buttons.Button::getId)
                .containsExactly("ticket_close_confirm:channel-1", "ticket_close_cancel:channel-1");
    }

    @Test
    void closedButtonsHaveTranscriptOpenAndDelete() {
        List<net.dv8tion.jda.api.interactions.components.buttons.Button> buttons = service.closedButtons("channel-1");

        assertThat(buttons).extracting(net.dv8tion.jda.api.interactions.components.buttons.Button::getId)
                .containsExactly("ticket_transcript:channel-1", "ticket_open:channel-1", "ticket_delete:channel-1");
    }

    // ---- writeTranscript / frontendUrl ----

    @Test
    void writeTranscriptSerializesMessagesToJson() {
        TranscriptMessage message = new TranscriptMessage(
                "user-1", "tag", "avatar", "2026-01-01T00:00:00Z", "hello", List.of(), null);

        String json = service.writeTranscript(List.of(message));

        assertThat(json).contains("\"authorId\":\"user-1\"").contains("hello");
    }

    @Test
    void frontendUrlStripsTrailingSlash() {
        assertThat(service.frontendUrl()).isEqualTo("https://example.com");
    }

    // ---- saveTranscriptToLogChannel ----

    @Test
    void saveTranscriptToLogChannelReportsNoMessagesWhenTranscriptIsNull() {
        IncidentTicket ticket = new IncidentTicket("channel-1", "guild-1", "user-1");
        when(textChannel.getGuild()).thenReturn(guild);
        when(guild.getName()).thenReturn("My Guild");
        when(textChannel.getName()).thenReturn("ticket-channel");
        when(eventLogEmbedSender.send(eq(guild), eq(LogEventType.TICKET_TRANSCRIPT_SAVED), any(), any())).thenReturn(true);

        boolean result = service.saveTranscriptToLogChannel(textChannel, ticket, "https://example.com/tickets/channel-1");

        assertThat(result).isTrue();
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.TICKET_TRANSCRIPT_SAVED), any(), any());
    }

    @Test
    void saveTranscriptToLogChannelCountsMessagesPerAuthor() {
        IncidentTicket ticket = new IncidentTicket("channel-1", "guild-1", "user-1");
        ticket.close("closer-1", service.writeTranscript(List.of(
                new TranscriptMessage("user-1", "Alice", "avatar", "t1", "hi", List.of(), null),
                new TranscriptMessage("user-1", "Alice", "avatar", "t2", "hi again", List.of(), null),
                new TranscriptMessage("user-2", "Bob", "avatar", "t3", "hello", List.of(), null))));
        when(textChannel.getGuild()).thenReturn(guild);
        when(guild.getName()).thenReturn("My Guild");
        when(textChannel.getName()).thenReturn("ticket-channel");
        when(eventLogEmbedSender.send(eq(guild), eq(LogEventType.TICKET_TRANSCRIPT_SAVED), any(), any())).thenReturn(false);

        boolean result = service.saveTranscriptToLogChannel(textChannel, ticket, "https://example.com/tickets/channel-1");

        assertThat(result).isFalse();
    }

    // ---- buildTranscript ----

    private Message mockMessage(String authorId, boolean isBot, String content, OffsetDateTime timestamp) {
        Message message = mock(Message.class);
        User author = mock(User.class);
        when(author.getId()).thenReturn(authorId);
        Mockito.lenient().when(author.getName()).thenReturn("author-" + authorId);
        Mockito.lenient().when(author.getEffectiveAvatarUrl()).thenReturn("avatar-url");
        when(message.getAuthor()).thenReturn(author);
        when(message.getAttachments()).thenReturn(List.<Attachment>of());
        when(message.getContentRaw()).thenReturn(content);
        Mockito.lenient().when(message.getTimeCreated()).thenReturn(timestamp);
        return message;
    }

    @SuppressWarnings("unchecked")
    private void stubHistory(TextChannel channelMock, List<Message> messages) {
        MessagePaginationAction retrieveAction = mock(MessagePaginationAction.class, Mockito.RETURNS_SELF);
        when(retrieveAction.takeAsync(500)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(messages));
        when(channelMock.getIterableHistory()).thenReturn(retrieveAction);
        JDA jda = mock(JDA.class);
        SelfUser selfUser = mock(SelfUser.class);
        when(selfUser.getId()).thenReturn("bot-1");
        when(jda.getSelfUser()).thenReturn(selfUser);
        when(channelMock.getJDA()).thenReturn(jda);
        when(channelMock.getGuild()).thenReturn(guild);
    }

    @Test
    void buildTranscriptMapsRegularMessage() {
        Message message = mockMessage("user-1", false, "hello world", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        stubHistory(textChannel, List.of(message));

        List<TranscriptMessage> result = service.buildTranscript(textChannel);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("hello world");
        assertThat(result.get(0).eventKind()).isNull();
    }

    @Test
    void buildTranscriptResolvesRoleMentions() {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn("Moderators");
        when(guild.getRoleById("123")).thenReturn(role);
        Message message = mockMessage("user-1", false, "hey <@&123>", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        stubHistory(textChannel, List.of(message));

        List<TranscriptMessage> result = service.buildTranscript(textChannel);

        assertThat(result.get(0).content()).isEqualTo("hey @Moderators");
    }

    @Test
    void buildTranscriptResolvesDeletedRoleMention() {
        when(guild.getRoleById("999")).thenReturn(null);
        Message message = mockMessage("user-1", false, "hey <@&999>", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        stubHistory(textChannel, List.of(message));

        List<TranscriptMessage> result = service.buildTranscript(textChannel);

        assertThat(result.get(0).content()).isEqualTo("hey @deleted-role");
    }

    @Test
    void buildTranscriptSkipsIgnoredBotConfirmationMessage() {
        Message message = mockMessage("bot-1", true, "Are you sure you would like to close this ticket?",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        stubHistory(textChannel, List.of(message));

        assertThat(service.buildTranscript(textChannel)).isEmpty();
    }

    @Test
    void buildTranscriptSkipsBlankBotMessageWithNoAttachments() {
        Message message = mockMessage("bot-1", true, "", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        stubHistory(textChannel, List.of(message));

        assertThat(service.buildTranscript(textChannel)).isEmpty();
    }

    @Test
    void buildTranscriptClassifiesKnownBotEventKinds() {
        Message controls = mockMessage("bot-1", true, "Ticket controls", OffsetDateTime.parse("2026-01-01T00:00:01Z"));
        Message closed = mockMessage("bot-1", true, "Ticket closed by Alice (<@user-1>).", OffsetDateTime.parse("2026-01-01T00:00:02Z"));
        Message reopened = mockMessage("bot-1", true, "Ticket reopened by Alice (<@user-1>).", OffsetDateTime.parse("2026-01-01T00:00:03Z"));
        Message cancelled = mockMessage("bot-1", true, "Close cancelled.", OffsetDateTime.parse("2026-01-01T00:00:04Z"));
        Message repeatTrigger = mockMessage("bot-1", true, "User posted in the trap channel again.", OffsetDateTime.parse("2026-01-01T00:00:05Z"));
        Message dmSent = mockMessage("bot-1", true, "DM was successfully sent.", OffsetDateTime.parse("2026-01-01T00:00:06Z"));
        Message dmFailed = mockMessage("bot-1", true, "Failed to send DM.", OffsetDateTime.parse("2026-01-01T00:00:07Z"));
        Message notice = mockMessage("bot-1", true, "Some other bot notice", OffsetDateTime.parse("2026-01-01T00:00:08Z"));
        stubHistory(textChannel, List.of(controls, closed, reopened, cancelled, repeatTrigger, dmSent, dmFailed, notice));

        List<TranscriptMessage> result = service.buildTranscript(textChannel);

        assertThat(result).extracting(TranscriptMessage::eventKind).containsExactly(
                "ticket_controls", "ticket_closed", "ticket_reopened", "close_cancelled",
                "repeat_trigger", "dm_sent", "dm_failed", "bot_notice");
    }

    @Test
    void buildTranscriptSortsMessagesByTimestamp() {
        Message later = mockMessage("user-1", false, "second", OffsetDateTime.parse("2026-01-01T00:00:02Z"));
        Message earlier = mockMessage("user-1", false, "first", OffsetDateTime.parse("2026-01-01T00:00:01Z"));
        stubHistory(textChannel, List.of(later, earlier));

        List<TranscriptMessage> result = service.buildTranscript(textChannel);

        assertThat(result).extracting(TranscriptMessage::content).containsExactly("first", "second");
    }
}
