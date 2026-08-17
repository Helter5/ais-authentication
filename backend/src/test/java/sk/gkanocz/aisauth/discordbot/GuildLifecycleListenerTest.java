package sk.gkanocz.aisauth.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.requests.restaction.pagination.AuditLogPaginationAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.verification.VerificationService;
import sk.gkanocz.aisauth.verification.VerifiedUser;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuildLifecycleListenerTest {

    @Mock
    private VerificationService verificationService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private GuildSettingsService guildSettingsService;
    @Mock
    private VerifiedUserRepository verifiedUserRepository;
    @Mock
    private EventLogEmbedSender eventLogEmbedSender;

    @Mock
    private Guild guild;
    @Mock
    private User user;

    private GuildLifecycleListener listener;

    @BeforeEach
    void setUp() {
        listener = new GuildLifecycleListener(
                verificationService, auditLogService, guildSettingsService, verifiedUserRepository, eventLogEmbedSender);

        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(guild.getName()).thenReturn("My Guild");
        Mockito.lenient().when(user.getId()).thenReturn("user-1");
        Mockito.lenient().when(user.getName()).thenReturn("Alice");
    }

    private GuildSettings settingsWithVerifiedRole(String roleId) {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId(roleId);
        return settings;
    }

    private VerifiedUser verifiedUser() {
        return new VerifiedUser("ais-1", "user-1", "guild-1", "user1@stuba.sk");
    }

    // ---- onGuildMemberRemove ----

    @Test
    void memberRemoveAlwaysCleansUpAndAuditLogsRegardlessOfVerificationStatus() {
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());
        GuildMemberRemoveEvent event = mock(GuildMemberRemoveEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);

        listener.onGuildMemberRemove(event);

        verify(verificationService).cleanupDepartedMember("user-1", "guild-1");
        verify(auditLogService).log(any());
        verify(eventLogEmbedSender, never()).send(any(), any(), any());
    }

    @Test
    void memberRemoveSendsAnEventLogWhenTheMemberWasVerified() {
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.of(verifiedUser()));
        GuildMemberRemoveEvent event = mock(GuildMemberRemoveEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);

        listener.onGuildMemberRemove(event);

        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.VERIFIED_USER_REMOVED), any());
    }

    @Test
    void memberRemoveSwallowsCleanupFailureAndSkipsTheAuditLog() {
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("db down")).when(verificationService).cleanupDepartedMember("user-1", "guild-1");
        GuildMemberRemoveEvent event = mock(GuildMemberRemoveEvent.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);

        listener.onGuildMemberRemove(event);

        verify(auditLogService, never()).log(any());
    }

    // ---- onGuildMemberRoleAdd ----

    private GuildMemberRoleAddEvent roleAddEvent(List<Role> roles) {
        GuildMemberRoleAddEvent event = mock(GuildMemberRoleAddEvent.class);
        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(event.getUser()).thenReturn(user);
        Mockito.lenient().when(event.getRoles()).thenReturn(roles);
        return event;
    }

    private Role role(String id) {
        Role role = mock(Role.class);
        Mockito.lenient().when(role.getId()).thenReturn(id);
        return role;
    }

    @Test
    void roleAddDoesNothingWhenNoVerifiedRoleConfigured() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole(null));

        listener.onGuildMemberRoleAdd(roleAddEvent(List.of(role("some-role"))));

        verify(verifiedUserRepository, never()).existsByDiscordIdAndGuildId(any(), any());
    }

    @Test
    void roleAddDoesNothingWhenTheAddedRoleIsNotTheVerifiedRole() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));

        listener.onGuildMemberRoleAdd(roleAddEvent(List.of(role("other-role"))));

        verify(verifiedUserRepository, never()).existsByDiscordIdAndGuildId(any(), any());
    }

    @Test
    void roleAddDoesNothingWhenAMatchingVerifiedUsersRowAlreadyExists() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        when(verifiedUserRepository.existsByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(true);

        listener.onGuildMemberRoleAdd(roleAddEvent(List.of(role("verified-role"))));

        verify(eventLogEmbedSender, never()).send(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void roleAddFlagsAssignmentOutsideVerifyAndResolvesTheActorFromAuditLog() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        when(verifiedUserRepository.existsByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(false);

        AuditLogPaginationAction auditAction = mock(AuditLogPaginationAction.class, Mockito.RETURNS_SELF);
        when(guild.retrieveAuditLogs()).thenReturn(auditAction);
        User actorUser = mock(User.class);
        when(actorUser.getId()).thenReturn("actor-1");
        net.dv8tion.jda.api.audit.AuditLogEntry logEntry = mock(net.dv8tion.jda.api.audit.AuditLogEntry.class);
        when(logEntry.getTargetId()).thenReturn("user-1");
        when(logEntry.getUser()).thenReturn(actorUser);
        Mockito.doAnswer(invocation -> {
            Consumer<List<net.dv8tion.jda.api.audit.AuditLogEntry>> success = invocation.getArgument(0);
            success.accept(List.of(logEntry));
            return null;
        }).when(auditAction).queue(any(), any());

        listener.onGuildMemberRoleAdd(roleAddEvent(List.of(role("verified-role"))));

        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.VERIFIED_ROLE_ADDED_WITHOUT_VERIFY), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void roleAddFallsBackToUnknownActorWhenAuditLogLookupFails() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        when(verifiedUserRepository.existsByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(false);

        AuditLogPaginationAction auditAction = mock(AuditLogPaginationAction.class, Mockito.RETURNS_SELF);
        when(guild.retrieveAuditLogs()).thenReturn(auditAction);
        Mockito.doAnswer(invocation -> {
            Consumer<Throwable> failure = invocation.getArgument(1);
            failure.accept(new RuntimeException("missing audit log permission"));
            return null;
        }).when(auditAction).queue(any(), any());

        listener.onGuildMemberRoleAdd(roleAddEvent(List.of(role("verified-role"))));

        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.VERIFIED_ROLE_ADDED_WITHOUT_VERIFY), any());
    }

    // ---- onGuildMemberRoleRemove ----

    private GuildMemberRoleRemoveEvent roleRemoveEvent(List<Role> roles) {
        GuildMemberRoleRemoveEvent event = mock(GuildMemberRoleRemoveEvent.class);
        Mockito.lenient().when(event.getGuild()).thenReturn(guild);
        Mockito.lenient().when(event.getUser()).thenReturn(user);
        Mockito.lenient().when(event.getRoles()).thenReturn(roles);
        return event;
    }

    @Test
    void roleRemoveDoesNothingWhenNoVerifiedRoleConfigured() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole(null));

        listener.onGuildMemberRoleRemove(roleRemoveEvent(List.of(role("some-role"))));

        verify(verifiedUserRepository, never()).findByDiscordIdAndGuildId(any(), any());
    }

    @Test
    void roleRemoveDoesNothingWhenTheRemovedRoleIsNotTheVerifiedRole() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));

        listener.onGuildMemberRoleRemove(roleRemoveEvent(List.of(role("other-role"))));

        verify(verifiedUserRepository, never()).findByDiscordIdAndGuildId(any(), any());
    }

    @Test
    void roleRemoveDoesNothingWhenNoMatchingVerifiedUsersRowExists() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.empty());

        listener.onGuildMemberRoleRemove(roleRemoveEvent(List.of(role("verified-role"))));

        verify(verificationService, never()).cleanupDepartedMember(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void roleRemoveCleansUpAndSendsEventLogWhenVerifiedRoleIsRemoved() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.of(verifiedUser()));
        AuditLogPaginationAction auditAction = mock(AuditLogPaginationAction.class, Mockito.RETURNS_SELF);
        when(guild.retrieveAuditLogs()).thenReturn(auditAction);
        Mockito.doAnswer(invocation -> {
            Consumer<Throwable> failure = invocation.getArgument(1);
            failure.accept(new RuntimeException("no perms"));
            return null;
        }).when(auditAction).queue(any(), any());

        listener.onGuildMemberRoleRemove(roleRemoveEvent(List.of(role("verified-role"))));

        verify(verificationService).cleanupDepartedMember("user-1", "guild-1");
        verify(auditLogService).log(any());
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.VERIFIED_USER_REMOVED), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void roleRemoveStillSendsEventLogWhenCleanupThrows() {
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settingsWithVerifiedRole("verified-role"));
        when(verifiedUserRepository.findByDiscordIdAndGuildId("user-1", "guild-1")).thenReturn(Optional.of(verifiedUser()));
        doThrow(new RuntimeException("db down")).when(verificationService).cleanupDepartedMember("user-1", "guild-1");
        AuditLogPaginationAction auditAction = mock(AuditLogPaginationAction.class, Mockito.RETURNS_SELF);
        when(guild.retrieveAuditLogs()).thenReturn(auditAction);
        Mockito.doAnswer(invocation -> {
            Consumer<Throwable> failure = invocation.getArgument(1);
            failure.accept(new RuntimeException("no perms"));
            return null;
        }).when(auditAction).queue(any(), any());

        listener.onGuildMemberRoleRemove(roleRemoveEvent(List.of(role("verified-role"))));

        verify(auditLogService, never()).log(any());
        verify(eventLogEmbedSender).send(eq(guild), eq(LogEventType.VERIFIED_USER_REMOVED), any());
    }
}
