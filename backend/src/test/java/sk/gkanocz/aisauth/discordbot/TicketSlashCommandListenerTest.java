package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.ticket.IncidentTicket;
import sk.gkanocz.aisauth.ticket.IncidentTicketRepository;
import sk.gkanocz.aisauth.ticket.TicketService;

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
class TicketSlashCommandListenerTest {

    @Mock
    private IncidentTicketRepository incidentTicketRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private LogRoutingService logRoutingService;

    @Mock
    private SlashCommandInteractionEvent event;
    @Mock
    private Guild guild;
    @Mock
    private Member member;
    @Mock
    private MessageChannelUnion channelUnion;
    @Mock
    private TextChannel channel;
    @Mock
    private net.dv8tion.jda.api.entities.User actor;

    private TicketSlashCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketSlashCommandListener(incidentTicketRepository, ticketService, logRoutingService);

        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(event.getMember()).thenReturn(member);
        Mockito.lenient().when(event.getUser()).thenReturn(actor);
        Mockito.lenient().when(actor.getId()).thenReturn("actor-1");
        Mockito.lenient().when(actor.getName()).thenReturn("Actor");
        Mockito.lenient().when(event.getChannel()).thenReturn(channelUnion);
        Mockito.lenient().when(channelUnion.getId()).thenReturn("channel-1");
        Mockito.lenient().when(channelUnion.asTextChannel()).thenReturn(channel);
        Mockito.lenient().when(ticketService.isTicketManager("guild-1", member)).thenReturn(true);
    }

    private IncidentTicket openTicket() {
        IncidentTicket ticket = new IncidentTicket("channel-1", "guild-1", "user-1");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.of(ticket));
        return ticket;
    }

    @SuppressWarnings("unchecked")
    private ReplyCallbackAction stubReply() {
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class, Mockito.RETURNS_SELF);
        when(event.reply(anyString())).thenReturn(replyAction);
        return replyAction;
    }

    // ---- dispatch routing ----

    @Test
    void dispatchIgnoresNonTicketCommands() {
        when(event.getName()).thenReturn("info");

        listener.dispatch(event, null);

        verify(incidentTicketRepository, never()).findById(anyString());
    }

    @Test
    void dispatchIgnoresUnknownSubcommand() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("unknown");

        listener.dispatch(event, null);

        verify(incidentTicketRepository, never()).findById(anyString());
    }

    // ---- requireTicket / requireManager guards ----

    @Test
    void repliesWhenChannelIsNotATicket() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("close");
        when(incidentTicketRepository.findById("channel-1")).thenReturn(Optional.empty());
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("This channel is not a ticket.");
    }

    @Test
    void repliesWhenTicketBelongsToADifferentGuild() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("close");
        when(incidentTicketRepository.findById("channel-1"))
                .thenReturn(Optional.of(new IncidentTicket("channel-1", "other-guild", "user-1")));
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("This channel is not a ticket.");
    }

    @Test
    void repliesPermissionDeniedWhenMemberIsNull() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("close");
        openTicket();
        when(event.getMember()).thenReturn(null);
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("You do not have permission to manage this ticket.");
    }

    @Test
    void repliesPermissionDeniedWhenNotTicketManager() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("close");
        openTicket();
        when(ticketService.isTicketManager("guild-1", member)).thenReturn(false);
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("You do not have permission to manage this ticket.");
    }

    // ---- close ----

    @Test
    void closeRepliesWhenAlreadyClosed() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("close");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("This ticket is already closed.");
    }

    @Test
    void closeRepliesWhenNoClosedCategoryConfigured() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("close");
        openTicket();
        when(ticketService.closedCategoryId("guild-1")).thenReturn(null);
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("Set a \"Category on close\" in the Hacked Account Trap module settings before closing tickets.");
    }

    @Test
    void closePromptsConfirmationWhenClosedCategoryConfigured() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("close");
        openTicket();
        when(ticketService.closedCategoryId("guild-1")).thenReturn("closed-cat-1");
        List<net.dv8tion.jda.api.interactions.components.buttons.Button> confirmButtons = List.of();
        when(ticketService.closeConfirmButtons("channel-1")).thenReturn(confirmButtons);
        ReplyCallbackAction replyAction = stubReply();

        listener.dispatch(event, null);

        verify(event).reply("Are you sure you would like to close this ticket?");
        verify(replyAction).addActionRow(confirmButtons);
    }

    // ---- reopen ----

    @Test
    void reopenRepliesWhenAlreadyOpen() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("reopen");
        openTicket();
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("This ticket is already open.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reopenGrantsAccessWhenIncludeUserSettingEnabled() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("reopen");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(ticketService.includeUserSetting("guild-1")).thenReturn(true);
        when(ticketService.openCategoryId("guild-1")).thenReturn(null);

        Member owner = mock(Member.class);
        CacheRestAction<Member> retrieveAction = mock(CacheRestAction.class);
        when(guild.retrieveMemberById("user-1")).thenReturn(retrieveAction);
        when(retrieveAction.complete()).thenReturn(owner);
        PermissionOverrideAction overrideAction = mock(PermissionOverrideAction.class, Mockito.RETURNS_SELF);
        when(channel.upsertPermissionOverride(owner)).thenReturn(overrideAction);
        stubReply();

        listener.dispatch(event, null);

        verify(overrideAction).grant(Permission.VIEW_CHANNEL);
        verify(ticketService).postTicketControls(channel, "guild-1", "user-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reopenDeletesOverrideWhenIncludeUserSettingDisabled() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("reopen");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(ticketService.includeUserSetting("guild-1")).thenReturn(false);
        when(ticketService.openCategoryId("guild-1")).thenReturn(null);

        Member owner = mock(Member.class);
        CacheRestAction<Member> retrieveAction = mock(CacheRestAction.class);
        when(guild.retrieveMemberById("user-1")).thenReturn(retrieveAction);
        when(retrieveAction.complete()).thenReturn(owner);
        PermissionOverride override = mock(PermissionOverride.class);
        when(channel.getPermissionOverride(owner)).thenReturn(override);
        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(override.delete()).thenReturn(deleteAction);
        stubReply();

        listener.dispatch(event, null);

        verify(deleteAction).queue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reopenSkipsPermissionOverrideWhenOwnerLeftServer() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("reopen");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(ticketService.openCategoryId("guild-1")).thenReturn(null);
        CacheRestAction<Member> retrieveAction = mock(CacheRestAction.class);
        when(guild.retrieveMemberById("user-1")).thenReturn(retrieveAction);
        when(retrieveAction.complete()).thenReturn(null);
        stubReply();

        listener.dispatch(event, null);

        verify(channel, never()).upsertPermissionOverride(any());
        verify(event).reply(org.mockito.ArgumentMatchers.contains("Ticket reopened by"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reopenMovesChannelToOpenCategoryWhenConfigured() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("reopen");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(ticketService.openCategoryId("guild-1")).thenReturn("open-cat-1");
        CacheRestAction<Member> retrieveAction = mock(CacheRestAction.class);
        when(guild.retrieveMemberById("user-1")).thenReturn(retrieveAction);
        when(retrieveAction.complete()).thenReturn(null);
        Category openCategory = mock(Category.class);
        when(openCategory.getId()).thenReturn("open-cat-1");
        when(guild.getCategoryById("open-cat-1")).thenReturn(openCategory);
        when(channel.getParentCategory()).thenReturn(null);
        TextChannelManager channelManager = mock(TextChannelManager.class, Mockito.RETURNS_SELF);
        when(channel.getManager()).thenReturn(channelManager);
        stubReply();

        listener.dispatch(event, null);

        verify(channelManager).setParent(openCategory);
    }

    // ---- delete ----

    @Test
    @SuppressWarnings("unchecked")
    void deleteRepliesAndSchedulesChannelDeletion() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("delete");
        openTicket();
        AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);
        when(channel.delete()).thenReturn(deleteAction);
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("Ticket will be deleted in a few seconds.");
        verify(deleteAction).queueAfter(eq(5L), eq(java.util.concurrent.TimeUnit.SECONDS),
                any(java.util.function.Consumer.class), any(java.util.function.Consumer.class));
    }

    // ---- recap ----

    @Test
    void recapRepliesWhenNoTranscriptYet() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("recap");
        openTicket();
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("No transcript yet — close the ticket first.");
    }

    @Test
    void recapRepliesWhenNoLogChannelConfigured() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("recap");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(logRoutingService.channelIdFor("guild-1", LogEventType.TICKET_TRANSCRIPT_SAVED)).thenReturn(Optional.empty());
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("Set a Transcript Log channel in Settings → Log Channels before saving transcripts.");
    }

    @Test
    void recapRepliesWithLinkWhenSaved() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("recap");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(logRoutingService.channelIdFor("guild-1", LogEventType.TICKET_TRANSCRIPT_SAVED)).thenReturn(Optional.of("log-1"));
        when(ticketService.frontendUrl()).thenReturn("https://example.com");
        when(ticketService.saveTranscriptToLogChannel(eq(channel), eq(ticket), anyString())).thenReturn(true);
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("Transcript saved. https://example.com/tickets/channel-1?guildId=guild-1");
    }

    @Test
    void recapRepliesWithFailureWhenSaveFails() {
        when(event.getName()).thenReturn("ticket");
        when(event.getSubcommandName()).thenReturn("recap");
        IncidentTicket ticket = openTicket();
        ticket.close("closer-1", "[]");
        when(logRoutingService.channelIdFor("guild-1", LogEventType.TICKET_TRANSCRIPT_SAVED)).thenReturn(Optional.of("log-1"));
        when(ticketService.frontendUrl()).thenReturn("https://example.com");
        when(ticketService.saveTranscriptToLogChannel(eq(channel), eq(ticket), anyString())).thenReturn(false);
        stubReply();

        listener.dispatch(event, null);

        verify(event).reply("Failed to save the transcript — check that the configured Transcript Log channel still exists and the bot can post there.");
    }
}
