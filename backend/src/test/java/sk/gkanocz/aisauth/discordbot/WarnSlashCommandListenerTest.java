package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import sk.gkanocz.aisauth.warn.Warn;
import sk.gkanocz.aisauth.warn.WarnService;
import sk.gkanocz.aisauth.warn.WarnThreshold;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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
class WarnSlashCommandListenerTest {

    @Mock
    private WarnService warnService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DiscordModerationService moderationService;
    @Mock
    private EventLogEmbedSender eventLogEmbedSender;
    @Mock
    private LogRoutingService logRoutingService;
    @Mock
    private AdminSettingsService adminSettingsService;

    @Mock
    private SlashCommandInteractionEvent event;
    @Mock
    private Guild guild;
    @Mock
    private User actor;
    @Mock
    private InteractionHook hook;

    private WarnSlashCommandListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        listener = new WarnSlashCommandListener(warnService, auditLogService, moderationService, eventLogEmbedSender, logRoutingService, adminSettingsService);

        Mockito.lenient().when(logRoutingService.channelIdFor(anyString(), any(LogEventType.class))).thenReturn(Optional.of("log-channel-1"));
        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(guild.getName()).thenReturn("My Guild");
        Mockito.lenient().when(event.getUser()).thenReturn(actor);
        Mockito.lenient().when(actor.getId()).thenReturn("mod-1");
        Mockito.lenient().when(event.getHook()).thenReturn(hook);
        Mockito.lenient().when(event.deferReply(org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(mock(ReplyCallbackAction.class));
        Mockito.lenient().when(adminSettingsService.get(eq("cmd_settings_guild-1_warn"), any(TypeReference.class), any()))
                .thenReturn(Map.of());
    }

    @SuppressWarnings("unchecked")
    private WebhookMessageCreateAction<net.dv8tion.jda.api.entities.Message> stubHookSendMessage() {
        WebhookMessageCreateAction<net.dv8tion.jda.api.entities.Message> action =
                mock(WebhookMessageCreateAction.class, Mockito.RETURNS_SELF);
        when(hook.sendMessage(anyString())).thenReturn(action);
        return action;
    }

    private void stubStringOption(String name, String value) {
        OptionMapping option = mock(OptionMapping.class);
        Mockito.lenient().when(option.getAsString()).thenReturn(value);
        when(event.getOption(name)).thenReturn(option);
    }

    // ---- dispatch routing ----

    @Test
    void dispatchIgnoresUnrelatedCommands() {
        when(event.getName()).thenReturn("info");

        listener.dispatch(event, null);

        verify(warnService, never()).addWarn(any(), any(), any(), any());
    }

    @Test
    void dispatchIgnoresUnknownWarnSubcommand() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("unknown");

        listener.dispatch(event, null);

        verify(warnService, never()).addWarn(any(), any(), any(), any());
    }

    @Test
    void dispatchRoutesMywarnsToHandleMyWarns() {
        when(event.getName()).thenReturn("mywarns");
        when(warnService.getWarns("mod-1", "guild-1")).thenReturn(List.of());
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(warnService).getWarns("mod-1", "guild-1");
    }

    // ---- handleWarn (warn add) ----

    @Test
    void warnRepliesWhenTargetIsNotAMember() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("add");
        OptionMapping userOption = mock(OptionMapping.class);
        when(userOption.getAsMember()).thenReturn(null);
        when(event.getOption("user")).thenReturn(userOption);
        stubStringOption("reason", "spam");
        WebhookMessageCreateAction<net.dv8tion.jda.api.entities.Message> action = stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("User not found in this server.");
        verify(warnService, never()).addWarn(any(), any(), any(), any());
    }

    private Member targetMember(String id, String name) {
        Member member = mock(Member.class);
        Mockito.lenient().when(member.getId()).thenReturn(id);
        User user = mock(User.class);
        Mockito.lenient().when(user.getName()).thenReturn(name);
        Mockito.lenient().when(member.getUser()).thenReturn(user);
        Mockito.lenient().when(member.getGuild()).thenReturn(guild);
        return member;
    }

    private void stubWarnCommand(Member target, String reason) {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("add");
        OptionMapping userOption = mock(OptionMapping.class);
        Mockito.lenient().when(userOption.getAsMember()).thenReturn(target);
        when(event.getOption("user")).thenReturn(userOption);
        stubStringOption("reason", reason);
    }

    @Test
    void warnNotRecordedWhenActiveThresholdActionIsBlocked() {
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(2L);
        WarnThreshold threshold = new WarnThreshold("guild-1", 3, "ban");
        when(warnService.activeThreshold("guild-1", 3L)).thenReturn(Optional.of(threshold));
        when(moderationService.missingPermission(target, "ban")).thenReturn("bot is missing the BAN_MEMBERS permission");
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("⚠️ Warn not recorded: the \"ban\" threshold at 3 warns is in effect, "
                + "but bot is missing the BAN_MEMBERS permission.");
        verify(warnService, never()).addWarn(any(), any(), any(), any());
    }

    @Test
    void warnRecordsAndRepliesWithTotalWhenNoThresholdMatches() {
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(0L, 1L);
        when(warnService.activeThreshold("guild-1", 1L)).thenReturn(Optional.empty());
        when(warnService.matchingThreshold("guild-1", 1L)).thenReturn(Optional.empty());
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(warnService).addWarn("guild-1", "user-1", "mod-1", "spam");
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.WARN_ISSUED), any());
        verify(hook).sendMessage("**Alice** warned. Reason: spam\nTotal warns: 1");
    }

    @Test
    void warnAppliesSuccessfulAutoActionWhenThresholdMatches() {
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(2L, 3L);
        when(warnService.activeThreshold("guild-1", 3L)).thenReturn(Optional.empty());
        WarnThreshold threshold = new WarnThreshold("guild-1", 3, "timeout");
        when(warnService.matchingThreshold("guild-1", 3L)).thenReturn(Optional.of(threshold));
        when(moderationService.apply(eq(target), eq("timeout"), anyString(), eq(Duration.ofHours(24))))
                .thenReturn(new DiscordModerationService.Outcome(true, "timed out (24h)"));
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("**Alice** warned. Reason: spam\nTotal warns: 3\nAuto-action: timed out (24h)");
        verify(auditLogService).log(any());
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.WARN_THRESHOLD_ACTION), any());
    }

    @Test
    void warnReportsFailedAutoAction() {
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(2L, 3L);
        when(warnService.activeThreshold("guild-1", 3L)).thenReturn(Optional.empty());
        WarnThreshold threshold = new WarnThreshold("guild-1", 3, "ban");
        when(warnService.matchingThreshold("guild-1", 3L)).thenReturn(Optional.of(threshold));
        when(moderationService.apply(eq(target), eq("ban"), anyString(), any()))
                .thenReturn(new DiscordModerationService.Outcome(false, "target's role is equal to or higher than the bot's role"));
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("**Alice** warned. Reason: spam\nTotal warns: 3"
                + "\n⚠️ Auto-action (ban) failed: target's role is equal to or higher than the bot's role");
    }

    @Test
    void warnRepliesWithDomainExceptionMessageOnFailure() {
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenThrow(InvalidRequestException.withMessage("boom"));
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("boom");
    }

    @Test
    void warnRepliesWithGenericMessageOnUnexpectedFailure() {
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenThrow(new RuntimeException("db down"));
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Nastala neočakávaná chyba, skús to prosím neskôr.");
    }

    @Test
    void warnRefusesWhenLogChannelNotConfigured() {
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WARN_ISSUED)).thenReturn(Optional.empty());
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Log kanál pre tento príkaz nie je nastavený. Kontaktuj administrátora.");
        verify(warnService, never()).addWarn(any(), any(), any(), any());
    }

    // ---- handleWarns (warn list) ----

    @Test
    void warnsListsGuildWarnsWhenNoUserOptionGiven() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("list");
        when(event.getOption("user")).thenReturn(null);
        Warn warn = new Warn("guild-1", "user-1", "mod-1", "spam");
        when(warnService.getGuildWarns("guild-1")).thenReturn(List.of(warn));
        WebhookMessageCreateAction<net.dv8tion.jda.api.entities.Message> action = stubHookSendMessage();

        listener.dispatch(event, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(hook).sendMessage(captor.capture());
        assertThat(captor.getValue()).startsWith("**Server warnings** (1)").contains("<@user-1>").contains("spam");
    }

    @Test
    void warnsListsForASpecificUserWhenGiven() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("list");
        User target = mock(User.class);
        when(target.getId()).thenReturn("user-1");
        when(target.getName()).thenReturn("Alice");
        OptionMapping userOption = mock(OptionMapping.class);
        when(userOption.getAsUser()).thenReturn(target);
        when(event.getOption("user")).thenReturn(userOption);
        when(warnService.getWarns("user-1", "guild-1")).thenReturn(List.of());
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("**Warnings for Alice**\nNo warnings.");
    }

    // ---- handleMyWarns ----

    @Test
    void mywarnsShowsTheCallersOwnWarnings() {
        when(event.getName()).thenReturn("mywarns");
        when(warnService.getWarns("mod-1", "guild-1")).thenReturn(List.of());
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("**Your warnings**\nNo warnings.");
    }

    // ---- handleRemoveWarn ----

    @Test
    void removeWarnSucceeds() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("remove");
        OptionMapping idOption = mock(OptionMapping.class);
        when(idOption.getAsLong()).thenReturn(42L);
        when(event.getOption("id")).thenReturn(idOption);
        Warn removed = new Warn("guild-1", "user-1", "mod-1", "spam");
        when(warnService.removeWarn(42L, "guild-1")).thenReturn(removed);
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Warn #42 removed.");
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.WARN_REMOVED), any());
    }

    @Test
    void removeWarnRepliesWithDomainExceptionMessage() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("remove");
        OptionMapping idOption = mock(OptionMapping.class);
        when(idOption.getAsLong()).thenReturn(42L);
        when(event.getOption("id")).thenReturn(idOption);
        when(warnService.removeWarn(42L, "guild-1")).thenThrow(InvalidRequestException.withMessage("Warn not found"));
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Warn not found");
    }

    @Test
    void removeWarnRefusesWhenLogChannelNotConfigured() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("remove");
        OptionMapping idOption = mock(OptionMapping.class);
        when(idOption.getAsLong()).thenReturn(42L);
        when(event.getOption("id")).thenReturn(idOption);
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WARN_REMOVED)).thenReturn(Optional.empty());
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Log kanál pre tento príkaz nie je nastavený. Kontaktuj administrátora.");
        verify(warnService, never()).removeWarn(any(), any());
    }

    // ---- handleClearWarns ----

    @Test
    void clearWarnsReportsNoWarningsWhenNoneCleared() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("clearall");
        User target = mock(User.class);
        when(target.getId()).thenReturn("user-1");
        when(target.getName()).thenReturn("Alice");
        OptionMapping userOption = mock(OptionMapping.class);
        when(userOption.getAsUser()).thenReturn(target);
        when(event.getOption("user")).thenReturn(userOption);
        when(warnService.clearWarns("user-1", "guild-1")).thenReturn(0L);
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Alice has no warnings.");
        verify(eventLogEmbedSender, never()).send(any(), any(), any());
    }

    @Test
    void clearWarnsReportsClearedCount() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("clearall");
        User target = mock(User.class);
        when(target.getId()).thenReturn("user-1");
        when(target.getName()).thenReturn("Alice");
        OptionMapping userOption = mock(OptionMapping.class);
        when(userOption.getAsUser()).thenReturn(target);
        when(event.getOption("user")).thenReturn(userOption);
        when(warnService.clearWarns("user-1", "guild-1")).thenReturn(3L);
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Cleared 3 warning(s) for Alice.");
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.WARNS_CLEARED), any());
    }

    @Test
    void clearWarnsRefusesWhenLogChannelNotConfigured() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("clearall");
        User target = mock(User.class);
        OptionMapping userOption = mock(OptionMapping.class);
        when(userOption.getAsUser()).thenReturn(target);
        when(event.getOption("user")).thenReturn(userOption);
        when(logRoutingService.channelIdFor("guild-1", LogEventType.WARNS_CLEARED)).thenReturn(Optional.empty());
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(hook).sendMessage("Log kanál pre tento príkaz nie je nastavený. Kontaktuj administrátora.");
        verify(warnService, never()).clearWarns(any(), any());
    }

    // ---- per-subcommand ephemeral table ----

    @Test
    void warnAddDefersNonEphemeralByDefault() {
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(0L, 1L);
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(event).deferReply(false);
    }

    @Test
    void warnListDefersEphemeralByDefault() {
        when(event.getName()).thenReturn("warn");
        when(event.getSubcommandName()).thenReturn("list");
        when(event.getOption("user")).thenReturn(null);
        when(warnService.getGuildWarns("guild-1")).thenReturn(List.of());
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(event).deferReply(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void honorsThePerSubcommandEphemeralTable() {
        when(adminSettingsService.get(eq("cmd_settings_guild-1_warn"), any(TypeReference.class), any()))
                .thenReturn(Map.of("subcommandEphemeral", Map.of("add", true, "clearall", true)));
        Member target = targetMember("user-1", "Alice");
        stubWarnCommand(target, "spam");
        when(warnService.countWarns("user-1", "guild-1")).thenReturn(0L, 1L);
        stubHookSendMessage();

        listener.dispatch(event, null);

        verify(event).deferReply(true);
    }
}
