package sk.gkanocz.aisauth.ticket;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketButtonListenerTest {

    @Mock
    private IncidentTicketRepository incidentTicketRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private LogRoutingService logRoutingService;

    @Mock
    private ButtonInteractionEvent event;
    @Mock
    private Guild guild;
    @Mock
    private Member member;
    @Mock
    private User user;
    @Mock
    private MessageChannelUnion channelUnion;
    @Mock
    private TextChannel channel;
    @Mock
    private InteractionHook hook;

    private TicketButtonListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketButtonListener(incidentTicketRepository, ticketService, logRoutingService);

        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(event.getMember()).thenReturn(member);
        Mockito.lenient().when(event.getUser()).thenReturn(user);
        Mockito.lenient().when(user.getId()).thenReturn("actor-1");
        Mockito.lenient().when(user.getName()).thenReturn("Actor");
        Mockito.lenient().when(event.getChannel()).thenReturn(channelUnion);
        Mockito.lenient().when(channelUnion.asTextChannel()).thenReturn(channel);
        Mockito.lenient().when(ticketService.isTicketManager("guild-1", member)).thenReturn(true);
        Mockito.lenient().when(event.getHook()).thenReturn(hook);
    }

    private IncidentTicket openTicket() {
        return new IncidentTicket("channel-1", "guild-1", "user-1");
    }

    @SuppressWarnings("unchecked")
    private ReplyCallbackAction stubReply(String componentId) {
        when(event.getComponentId()).thenReturn(componentId);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);
        return replyAction;
    }

    @SuppressWarnings("unchecked")
    private MessageEditCallbackAction stubEditMessage() {
        MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.editMessage(anyString())).thenReturn(editAction);
        return editAction;
    }

    // ---- routing guards ----

    @Test
    void ignoresComponentIdsThatAreNotTicketPrefixed() {
        when(event.getComponentId()).thenReturn("something_else:channel-1");

        listener.onButtonInteraction(event);

        verify(incidentTicketRepository, never()).findById(anyString());
    }

    @Test
    void ignoresInteractionsOutsideAGuild() {
        when(event.getComponentId()).thenReturn("ticket_close:channel-1");
        when(event.getGuild()).thenReturn(null);

        listener.onButtonInteraction(event);

        verify(incidentTicketRepository, never()).findById(anyString());
    }

    @Test
    void repliesTicketNoLongerExistsWhenNotFound() {
        ReplyCallbackAction replyAction = stubReply("ticket_close:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.empty());

        listener.onButtonInteraction(event);

        verify(event).reply("This ticket no longer exists.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void repliesTicketNoLongerExistsWhenGuildMismatch() {
        stubReply("ticket_close:channel-1");
        IncidentTicket ticket = new IncidentTicket("channel-1", "other-guild", "user-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));

        listener.onButtonInteraction(event);

        verify(event).reply("This ticket no longer exists.");
    }

    @Test
    void repliesPermissionDeniedWhenMemberIsNull() {
        stubReply("ticket_close:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        when(event.getMember()).thenReturn(null);

        listener.onButtonInteraction(event);

        verify(event).reply("You do not have permission to manage this ticket.");
    }

    @Test
    void repliesPermissionDeniedWhenNotTicketManager() {
        stubReply("ticket_close:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        when(ticketService.isTicketManager("guild-1", member)).thenReturn(false);

        listener.onButtonInteraction(event);

        verify(event).reply("You do not have permission to manage this ticket.");
    }

    // ---- ticket_close ----

    @Test
    void closeWarnsWhenNoClosedCategoryConfigured() {
        stubReply("ticket_close:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        when(ticketService.closedCategoryId("guild-1")).thenReturn(null);

        listener.onButtonInteraction(event);

        verify(event).reply("Set a \"Category on close\" in the Hacked Account Trap module settings before closing tickets.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void closePromptsConfirmationWhenClosedCategoryConfigured() {
        ReplyCallbackAction replyAction = stubReply("ticket_close:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        when(ticketService.closedCategoryId("guild-1")).thenReturn("closed-cat-1");
        List<net.dv8tion.jda.api.interactions.components.buttons.Button> confirmButtons = List.of();
        when(ticketService.closeConfirmButtons("channel-1")).thenReturn(confirmButtons);

        listener.onButtonInteraction(event);

        verify(event).reply("Are you sure you would like to close this ticket?");
        verify(replyAction).addActionRow(confirmButtons);
    }

    // ---- ticket_close_cancel ----

    @Test
    void closeCancelEditsMessageAndClearsComponents() {
        when(event.getComponentId()).thenReturn("ticket_close_cancel:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        MessageEditCallbackAction editAction = stubEditMessage();

        listener.onButtonInteraction(event);

        verify(event).editMessage("Close cancelled.");
        verify(editAction).setComponents(List.of());
    }

    // ---- ticket_close_confirm ----

    @Test
    void closeConfirmEditsWarningWhenNoClosedCategoryConfigured() {
        when(event.getComponentId()).thenReturn("ticket_close_confirm:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        when(ticketService.closedCategoryId("guild-1")).thenReturn(null);
        MessageEditCallbackAction editAction = stubEditMessage();

        listener.onButtonInteraction(event);

        verify(event).editMessage("Set a \"Category on close\" in the Hacked Account Trap module settings before closing tickets.");
        verify(editAction).setComponents(List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void closeConfirmClosesTicketAndPostsClosedControls() {
        when(event.getComponentId()).thenReturn("ticket_close_confirm:channel-1");
        IncidentTicket ticket = openTicket();
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        when(ticketService.closedCategoryId("guild-1")).thenReturn("closed-cat-1");
        when(event.deferEdit()).thenReturn(mock(MessageEditCallbackAction.class));
        when(ticketService.buildTranscript(channel)).thenReturn(List.of());
        when(ticketService.writeTranscript(List.of())).thenReturn("[]");

        Member ticketOwner = mock(Member.class);
        when(guild.retrieveMemberById("user-1")).thenReturn(mock(net.dv8tion.jda.api.requests.restaction.CacheRestAction.class));
        when(guild.retrieveMemberById("user-1").complete()).thenReturn(ticketOwner);
        PermissionOverrideAction overrideAction = mock(PermissionOverrideAction.class, Mockito.RETURNS_SELF);
        when(channel.upsertPermissionOverride(ticketOwner)).thenReturn(overrideAction);

        Category closedCategory = mock(Category.class);
        when(closedCategory.getId()).thenReturn("closed-cat-1");
        when(guild.getCategoryById("closed-cat-1")).thenReturn(closedCategory);
        when(channel.getParentCategory()).thenReturn(null);
        TextChannelManager channelManager = mock(TextChannelManager.class, Mockito.RETURNS_SELF);
        when(channel.getManager()).thenReturn(channelManager);

        WebhookMessageEditAction<Message> hookEdit = mock(WebhookMessageEditAction.class, Mockito.RETURNS_SELF);
        when(hook.editOriginal(anyString())).thenReturn(hookEdit);

        listener.onButtonInteraction(event);

        verify(overrideAction).deny(net.dv8tion.jda.api.Permission.VIEW_CHANNEL);
        verify(channelManager).setParent(closedCategory);
        verify(incidentTicketRepository).save(ticket);
        verify(ticketService).postClosedControls(channel, ticket);
    }

    // ---- ticket_open ----

    @Test
    @SuppressWarnings("unchecked")
    void openGrantsAccessWhenIncludeUserSettingEnabled() {
        when(event.getComponentId()).thenReturn("ticket_open:channel-1");
        IncidentTicket ticket = openTicket();
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        when(ticketService.includeUserSetting("guild-1")).thenReturn(true);
        when(ticketService.openCategoryId("guild-1")).thenReturn(null);

        Member ticketOwner = mock(Member.class);
        when(guild.retrieveMemberById("user-1")).thenReturn(mock(net.dv8tion.jda.api.requests.restaction.CacheRestAction.class));
        when(guild.retrieveMemberById("user-1").complete()).thenReturn(ticketOwner);
        PermissionOverrideAction overrideAction = mock(PermissionOverrideAction.class, Mockito.RETURNS_SELF);
        when(channel.upsertPermissionOverride(ticketOwner)).thenReturn(overrideAction);

        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(overrideAction).grant(net.dv8tion.jda.api.Permission.VIEW_CHANNEL);
        verify(ticketService).postTicketControls(channel, "guild-1", "user-1");
        verify(event).reply("Ticket reopened by Actor (<@actor-1>).");
    }

    @Test
    @SuppressWarnings("unchecked")
    void openDeletesOverrideWhenIncludeUserSettingDisabledAndOverrideExists() {
        when(event.getComponentId()).thenReturn("ticket_open:channel-1");
        IncidentTicket ticket = openTicket();
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        when(ticketService.includeUserSetting("guild-1")).thenReturn(false);
        when(ticketService.openCategoryId("guild-1")).thenReturn(null);

        Member ticketOwner = mock(Member.class);
        when(guild.retrieveMemberById("user-1")).thenReturn(mock(net.dv8tion.jda.api.requests.restaction.CacheRestAction.class));
        when(guild.retrieveMemberById("user-1").complete()).thenReturn(ticketOwner);
        PermissionOverride override = mock(PermissionOverride.class);
        when(channel.getPermissionOverride(ticketOwner)).thenReturn(override);
        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(override.delete()).thenReturn(deleteAction);

        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);

        listener.onButtonInteraction(event);

        verify(deleteAction).queue();
    }

    // ---- ticket_delete ----

    @Test
    void deleteRepliesAndSchedulesChannelDeletion() {
        when(event.getComponentId()).thenReturn("ticket_delete:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.replyEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(replyAction);
        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(channel.delete()).thenReturn(deleteAction);

        listener.onButtonInteraction(event);

        verify(event).replyEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class));
        verify(deleteAction).queueAfter(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.SECONDS),
                any(java.util.function.Consumer.class), any(java.util.function.Consumer.class));
    }

    // ---- ticket_transcript ----

    @Test
    void transcriptRepliesWhenTicketHasNoTranscriptYet() {
        ReplyCallbackAction replyAction = stubReply("ticket_transcript:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));

        listener.onButtonInteraction(event);

        verify(event).reply("No transcript yet — close the ticket first.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void transcriptRepliesWhenNoLogChannelConfigured() {
        stubReply("ticket_transcript:channel-1");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        when(logRoutingService.channelIdFor("guild-1", LogEventType.TICKET_TRANSCRIPT_SAVED)).thenReturn(Optional.empty());

        listener.onButtonInteraction(event);

        verify(event).reply("Set a Transcript Log channel in Settings → Log Channels before saving transcripts.");
    }

    @Test
    void transcriptRepliesWithLinkWhenSaved() {
        stubReply("ticket_transcript:channel-1");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        when(logRoutingService.channelIdFor("guild-1", LogEventType.TICKET_TRANSCRIPT_SAVED)).thenReturn(Optional.of("log-1"));
        when(ticketService.frontendUrl()).thenReturn("https://example.com");
        when(ticketService.saveTranscriptToLogChannel(eq(channel), eq(ticket), anyString())).thenReturn(true);

        listener.onButtonInteraction(event);

        verify(event).reply("Transcript saved. https://example.com/tickets/channel-1?guildId=guild-1");
    }

    @Test
    void transcriptRepliesWithFailureWhenSaveFails() {
        stubReply("ticket_transcript:channel-1");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        when(logRoutingService.channelIdFor("guild-1", LogEventType.TICKET_TRANSCRIPT_SAVED)).thenReturn(Optional.of("log-1"));
        when(ticketService.frontendUrl()).thenReturn("https://example.com");
        when(ticketService.saveTranscriptToLogChannel(eq(channel), eq(ticket), anyString())).thenReturn(false);

        listener.onButtonInteraction(event);

        verify(event).reply("Failed to save the transcript — check that the configured Transcript Log channel still exists and the bot can post there.");
    }

    // ---- error handling ----

    @Test
    void repliesGenericErrorWhenActionThrowsAndNotAcknowledged() {
        stubReply("ticket_close:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        when(ticketService.closedCategoryId("guild-1")).thenThrow(new RuntimeException("boom"));
        when(event.isAcknowledged()).thenReturn(false);

        listener.onButtonInteraction(event);

        verify(event).reply("Something went wrong handling this ticket action.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsFollowUpErrorWhenActionThrowsAfterAcknowledged() {
        when(event.getComponentId()).thenReturn("ticket_close:channel-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(openTicket()));
        when(ticketService.closedCategoryId("guild-1")).thenThrow(new RuntimeException("boom"));
        when(event.isAcknowledged()).thenReturn(true);
        net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction<Message> followUp =
                mock(net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction.class, Mockito.RETURNS_SELF);
        when(hook.sendMessage(anyString())).thenReturn(followUp);

        listener.onButtonInteraction(event);

        verify(hook).sendMessage("Something went wrong handling this ticket action.");
        verify(followUp).setEphemeral(true);
    }
}
