package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandInteractionListenerTest {

    @Mock
    private VerificationSlashCommandListener verificationCommandHandler;
    @Mock
    private WarnSlashCommandListener warnCommandHandler;
    @Mock
    private UtilityCommandListener utilityCommandHandler;
    @Mock
    private SubjectRoleSlashCommandListener subjectRoleCommandHandler;
    @Mock
    private ThesisCounterSlashCommandListener thesisCounterCommandHandler;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SlashCommandInteractionEvent event;
    @Mock
    private Guild guild;
    @Mock
    private User user;
    @Mock
    private MessageChannelUnion channel;
    @Mock
    private ReplyCallbackAction replyCallbackAction;

    private CommandInteractionListener listener;

    @BeforeEach
    void setUp() {
        listener = new CommandInteractionListener(
                verificationCommandHandler, warnCommandHandler, utilityCommandHandler, subjectRoleCommandHandler,
                thesisCounterCommandHandler, adminSettingsService, auditLogService);

        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(event.getName()).thenReturn("verify");
        Mockito.lenient().when(event.getUser()).thenReturn(user);
        Mockito.lenient().when(event.getChannel()).thenReturn(channel);
        Mockito.lenient().when(event.getOptions()).thenReturn(List.of());
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(guild.getName()).thenReturn("Guild One");
        Mockito.lenient().when(user.getId()).thenReturn("discord-1");
        Mockito.lenient().when(user.getName()).thenReturn("someuser");
        Mockito.lenient().when(channel.getId()).thenReturn("channel-1");
        Mockito.lenient().when(channel.getName()).thenReturn("general");
        Mockito.lenient().when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(true);
        Mockito.lenient().when(adminSettingsService.isMaintenanceMode()).thenReturn(false);
    }

    @SuppressWarnings("unchecked")
    private void stubReply() {
        Mockito.lenient().when(event.reply(anyString())).thenReturn(replyCallbackAction);
        Mockito.lenient().when(replyCallbackAction.setEphemeral(org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(replyCallbackAction);
    }

    @Test
    void repliesAndStopsWhenInvokedOutsideAGuild() {
        when(event.getGuild()).thenReturn(null);
        stubReply();

        listener.onSlashCommandInteraction(event);

        verify(event).reply("This bot only works in server channels, not in DMs.");
        verify(adminSettingsService, never()).isGuildAllowed(any());
    }

    @Test
    void ignoresUnknownCommandNamesEntirely() {
        when(event.getName()).thenReturn("notarealcommand");

        listener.onSlashCommandInteraction(event);

        verify(adminSettingsService, never()).isGuildAllowed(any());
        verify(verificationCommandHandler, never()).dispatch(any(), any());
    }

    @Test
    void blocksAndAuditsWhenGuildIsNotAllowed() {
        when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(false);
        stubReply();

        listener.onSlashCommandInteraction(event);

        verify(event).reply("**Bot príkazy nie sú povolené na tomto serveri.**");
        verify(verificationCommandHandler, never()).dispatch(any(), any());
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        assertThat(captor.getValue().details().get("status")).isEqualTo("blocked");
        assertThat(captor.getValue().details().get("blockedReason")).isEqualTo("Server is not allowed");
    }

    @Test
    void blocksWhenMaintenanceModeIsOn() {
        when(adminSettingsService.isMaintenanceMode()).thenReturn(true);
        stubReply();

        listener.onSlashCommandInteraction(event);

        verify(event).reply("Bot is currently in maintenance mode. All commands are temporarily disabled.");
        verify(verificationCommandHandler, never()).dispatch(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void blocksWhenCommandIsDisabledForTheGuild() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any()))
                .thenReturn(Map.of("/verify", false));
        stubReply();

        listener.onSlashCommandInteraction(event);

        verify(event).reply("This command is currently disabled.");
        verify(verificationCommandHandler, never()).dispatch(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowsWhenCommandStateIsNotExplicitlyDisabled() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any())).thenReturn(Map.of());
        when(adminSettingsService.get(eq("cmd_perms_guild-1_verify"), eq(CommandPermissions.class), any()))
                .thenReturn(CommandPermissions.empty());
        when(adminSettingsService.get(eq("cmd_settings_guild-1_verify"), any(TypeReference.class), any()))
                .thenReturn(Map.of());

        listener.onSlashCommandInteraction(event);

        verify(verificationCommandHandler).dispatch(event, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void blocksWhenChannelIsNotOnTheAllowedList() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any())).thenReturn(Map.of());
        CommandPermissions restricted =
                new CommandPermissions(List.of("some-other-channel"), List.of(), List.of(), List.of(), false);
        when(adminSettingsService.get(eq("cmd_perms_guild-1_verify"), eq(CommandPermissions.class), any())).thenReturn(restricted);
        stubReply();

        listener.onSlashCommandInteraction(event);

        verify(event).reply("You do not have permission to use this command.");
        verify(verificationCommandHandler, never()).dispatch(any(), any());
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        assertThat(captor.getValue().details().get("blockedReason")).isEqualTo("Channel is not allowed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void blocksAdminOnlyCommandForNonAdministratorMember() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any())).thenReturn(Map.of());
        CommandPermissions adminOnly = new CommandPermissions(List.of(), List.of(), List.of(), List.of(), true);
        when(adminSettingsService.get(eq("cmd_perms_guild-1_verify"), eq(CommandPermissions.class), any())).thenReturn(adminOnly);
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of());
        when(member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)).thenReturn(false);
        when(event.getMember()).thenReturn(member);
        stubReply();

        listener.onSlashCommandInteraction(event);

        verify(event).reply("You do not have permission to use this command.");
        verify(verificationCommandHandler, never()).dispatch(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminOnlyCommandAllowedForAdministratorMember() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any())).thenReturn(Map.of());
        CommandPermissions adminOnly = new CommandPermissions(List.of(), List.of(), List.of(), List.of(), true);
        when(adminSettingsService.get(eq("cmd_perms_guild-1_verify"), eq(CommandPermissions.class), any())).thenReturn(adminOnly);
        when(adminSettingsService.get(eq("cmd_settings_guild-1_verify"), any(TypeReference.class), any())).thenReturn(Map.of());
        Member member = mock(Member.class);
        when(member.getRoles()).thenReturn(List.of());
        when(member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)).thenReturn(true);
        when(event.getMember()).thenReturn(member);

        listener.onSlashCommandInteraction(event);

        verify(verificationCommandHandler).dispatch(event, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void passesEphemeralOverrideFromCommandSettingsToHandlers() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any())).thenReturn(Map.of());
        when(adminSettingsService.get(eq("cmd_perms_guild-1_verify"), eq(CommandPermissions.class), any()))
                .thenReturn(CommandPermissions.empty());
        when(adminSettingsService.get(eq("cmd_settings_guild-1_verify"), any(TypeReference.class), any()))
                .thenReturn(Map.of("ephemeral", true));

        listener.onSlashCommandInteraction(event);

        verify(verificationCommandHandler).dispatch(event, true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchesToAllHandlersAndLogsSuccess() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any())).thenReturn(Map.of());
        when(adminSettingsService.get(eq("cmd_perms_guild-1_verify"), eq(CommandPermissions.class), any()))
                .thenReturn(CommandPermissions.empty());
        when(adminSettingsService.get(eq("cmd_settings_guild-1_verify"), any(TypeReference.class), any()))
                .thenReturn(Map.of());

        listener.onSlashCommandInteraction(event);

        verify(verificationCommandHandler).dispatch(event, null);
        verify(warnCommandHandler).dispatch(event, null);
        verify(utilityCommandHandler).dispatch(event, null);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        assertThat(captor.getValue().details().get("status")).isEqualTo("success");
        assertThat(captor.getValue().action()).isEqualTo("/verify");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlerExceptionIsCaughtAndLoggedAsErrorNotPropagated() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any())).thenReturn(Map.of());
        when(adminSettingsService.get(eq("cmd_perms_guild-1_verify"), eq(CommandPermissions.class), any()))
                .thenReturn(CommandPermissions.empty());
        when(adminSettingsService.get(eq("cmd_settings_guild-1_verify"), any(TypeReference.class), any()))
                .thenReturn(Map.of());
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(verificationCommandHandler).dispatch(any(), any());

        listener.onSlashCommandInteraction(event);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        assertThat(captor.getValue().details().get("status")).isEqualTo("error");
    }

    @Test
    @SuppressWarnings("unchecked")
    void codeOptionValueIsOmittedFromAuditLogDetails() {
        when(adminSettingsService.get(eq("cmd_states_guild-1"), any(TypeReference.class), any())).thenReturn(Map.of());
        when(adminSettingsService.get(eq("cmd_perms_guild-1_verify"), eq(CommandPermissions.class), any()))
                .thenReturn(CommandPermissions.empty());
        when(adminSettingsService.get(eq("cmd_settings_guild-1_verify"), any(TypeReference.class), any()))
                .thenReturn(Map.of());
        net.dv8tion.jda.api.interactions.commands.OptionMapping codeOption =
                mock(net.dv8tion.jda.api.interactions.commands.OptionMapping.class);
        when(codeOption.getName()).thenReturn("code");
        when(event.getOptions()).thenReturn(List.of(codeOption));

        listener.onSlashCommandInteraction(event);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogService).log(captor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) captor.getValue().details().get("options");
        assertThat(options).isEmpty();
    }
}
