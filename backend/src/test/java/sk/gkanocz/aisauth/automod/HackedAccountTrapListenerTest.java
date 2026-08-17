package sk.gkanocz.aisauth.automod;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.discordbot.DiscordModerationService;
import sk.gkanocz.aisauth.discordbot.EventLogEmbedSender;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.ticket.TicketService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers only the guard clauses in onMessageReceived (does the trap correctly decide whether to
 * fire at all) - the actual trigger() pipeline (incident channel creation, message history sweep,
 * moderation action) needs a disproportionate amount of JDA action-chain mocking for its value and
 * is left uncovered here.
 */
@ExtendWith(MockitoExtension.class)
class HackedAccountTrapListenerTest {

    @Mock
    private HackedAccountTrapService hackedAccountTrapService;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private TicketService ticketService;
    @Mock
    private DiscordModerationService moderationService;
    @Mock
    private EventLogEmbedSender eventLogEmbedSender;

    @Mock
    private MessageReceivedEvent event;
    @Mock
    private Guild guild;
    @Mock
    private User author;
    @Mock
    private MessageChannelUnion channel;

    private HackedAccountTrapListener listener;

    @BeforeEach
    void setUp() {
        listener = new HackedAccountTrapListener(
                hackedAccountTrapService, adminSettingsService, auditLogService, ticketService,
                moderationService, eventLogEmbedSender);

        Mockito.lenient().when(event.isFromGuild()).thenReturn(true);
        Mockito.lenient().when(event.getAuthor()).thenReturn(author);
        Mockito.lenient().when(author.isBot()).thenReturn(false);
        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(event.getChannel()).thenReturn(channel);
        Mockito.lenient().when(channel.getId()).thenReturn("trap-channel-1");
        Mockito.lenient().when(adminSettingsService.isMaintenanceMode()).thenReturn(false);
    }

    private HackedAccountTrapSettings enabledSettings() {
        return HackedAccountTrapSettings.defaults("trap-channel-1", 15);
    }

    private HackedAccountTrapSettings withEnabled(HackedAccountTrapSettings base, boolean enabled) {
        return new HackedAccountTrapSettings(
                enabled, base.trapChannelId(), base.action(), base.timeoutMinutes(),
                base.deleteTriggerMessage(), base.deleteRecentMessages(), base.cleanupMinutes(),
                base.exemptRoleIds(), base.ignoreAdministrators(), base.dmUser(), base.dmMessage(), base.reason(),
                base.incidentChannelEnabled(), base.incidentChannelCategoryId(), base.incidentChannelClosedCategoryId(),
                base.incidentChannelNameTemplate(), base.incidentChannelIncludeUser(), base.incidentChannelMessage(),
                base.incidentChannelPostDmStatus(), base.incidentChannelTagRoles(), base.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapSettings withTrapChannelId(HackedAccountTrapSettings base, String trapChannelId) {
        return new HackedAccountTrapSettings(
                base.enabled(), trapChannelId, base.action(), base.timeoutMinutes(),
                base.deleteTriggerMessage(), base.deleteRecentMessages(), base.cleanupMinutes(),
                base.exemptRoleIds(), base.ignoreAdministrators(), base.dmUser(), base.dmMessage(), base.reason(),
                base.incidentChannelEnabled(), base.incidentChannelCategoryId(), base.incidentChannelClosedCategoryId(),
                base.incidentChannelNameTemplate(), base.incidentChannelIncludeUser(), base.incidentChannelMessage(),
                base.incidentChannelPostDmStatus(), base.incidentChannelTagRoles(), base.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapSettings withExemptRoleIds(HackedAccountTrapSettings base, List<String> exemptRoleIds) {
        return new HackedAccountTrapSettings(
                base.enabled(), base.trapChannelId(), base.action(), base.timeoutMinutes(),
                base.deleteTriggerMessage(), base.deleteRecentMessages(), base.cleanupMinutes(),
                exemptRoleIds, base.ignoreAdministrators(), base.dmUser(), base.dmMessage(), base.reason(),
                base.incidentChannelEnabled(), base.incidentChannelCategoryId(), base.incidentChannelClosedCategoryId(),
                base.incidentChannelNameTemplate(), base.incidentChannelIncludeUser(), base.incidentChannelMessage(),
                base.incidentChannelPostDmStatus(), base.incidentChannelTagRoles(), base.incidentChannelTagRoleIds());
    }

    private void assertNoTriggerSideEffects() {
        verify(auditLogService, never()).log(any());
        verify(moderationService, never()).apply(any(), any(), any(), any());
        verify(ticketService, never()).postTicketControls(any(), any(), any());
    }

    @Test
    void ignoresMessagesNotFromAGuild() {
        when(event.isFromGuild()).thenReturn(false);

        listener.onMessageReceived(event);

        verify(hackedAccountTrapService, never()).get(any());
        assertNoTriggerSideEffects();
    }

    @Test
    void ignoresMessagesFromBots() {
        when(author.isBot()).thenReturn(true);

        listener.onMessageReceived(event);

        verify(hackedAccountTrapService, never()).get(any());
        assertNoTriggerSideEffects();
    }

    @Test
    void doesNothingDuringMaintenanceMode() {
        when(adminSettingsService.isMaintenanceMode()).thenReturn(true);

        listener.onMessageReceived(event);

        verify(hackedAccountTrapService, never()).get(any());
        assertNoTriggerSideEffects();
    }

    @Test
    void doesNothingWhenTrapIsDisabled() {
        HackedAccountTrapSettings disabled = withEnabled(enabledSettings(), false);
        when(hackedAccountTrapService.get("guild-1")).thenReturn(disabled);

        listener.onMessageReceived(event);

        assertNoTriggerSideEffects();
    }

    @Test
    void doesNothingWhenNoTrapChannelConfigured() {
        HackedAccountTrapSettings noChannel = withTrapChannelId(enabledSettings(), null);
        when(hackedAccountTrapService.get("guild-1")).thenReturn(noChannel);

        listener.onMessageReceived(event);

        assertNoTriggerSideEffects();
    }

    @Test
    void doesNothingWhenMessageIsInADifferentChannel() {
        when(channel.getId()).thenReturn("some-other-channel");
        when(hackedAccountTrapService.get("guild-1")).thenReturn(enabledSettings());

        listener.onMessageReceived(event);

        assertNoTriggerSideEffects();
    }

    @Test
    void doesNothingWhenAuthorIsNotAGuildMember() {
        when(hackedAccountTrapService.get("guild-1")).thenReturn(enabledSettings());
        when(event.getMember()).thenReturn(null);

        listener.onMessageReceived(event);

        assertNoTriggerSideEffects();
    }

    @Test
    void ignoresAdministratorsWhenConfiguredTo() {
        when(hackedAccountTrapService.get("guild-1")).thenReturn(enabledSettings());
        Member member = mock(Member.class);
        when(member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)).thenReturn(true);
        when(event.getMember()).thenReturn(member);

        listener.onMessageReceived(event);

        assertNoTriggerSideEffects();
    }

    @Test
    void ignoresMembersWithAnExemptRole() {
        HackedAccountTrapSettings withExemptRole = withExemptRoleIds(enabledSettings(), List.of("exempt-role-1"));
        when(hackedAccountTrapService.get("guild-1")).thenReturn(withExemptRole);
        Member member = mock(Member.class);
        when(member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)).thenReturn(false);
        Role exemptRole = mock(Role.class);
        when(exemptRole.getId()).thenReturn("exempt-role-1");
        when(member.getRoles()).thenReturn(List.of(exemptRole));
        when(event.getMember()).thenReturn(member);

        listener.onMessageReceived(event);

        assertNoTriggerSideEffects();
    }
}
