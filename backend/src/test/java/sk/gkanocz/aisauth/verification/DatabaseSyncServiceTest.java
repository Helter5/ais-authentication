package sk.gkanocz.aisauth.verification;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.wipe.WipeService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The real sync work runs inside an internal single-thread ExecutorService kicked off by
 * syncAllGuildsAsync, so every assertion here goes through Mockito.timeout(...) to await
 * that background task rather than calling private syncGuild logic directly.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSyncServiceTest {

    @Mock
    private VerifiedUserRepository verifiedUserRepository;
    @Mock
    private GuildSettingsService guildSettingsService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ObjectProvider<WipeService> wipeServiceProvider;
    @Mock
    private WipeService wipeService;

    @Mock
    private Guild guild;

    private DatabaseSyncService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseSyncService(verifiedUserRepository, guildSettingsService, auditLogService,
                adminSettingsService, transactionManager, wipeServiceProvider);

        Mockito.lenient().when(wipeServiceProvider.getObject()).thenReturn(wipeService);
        Mockito.lenient().when(guild.getId()).thenReturn("guild-1");
        Mockito.lenient().when(guild.getName()).thenReturn("My Guild");
    }

    @Test
    void skipsGuildWhenWipeIsInProgress() {
        when(wipeService.isRunning("guild-1")).thenReturn(true);

        service.syncAllGuildsAsync(List.of(guild));

        verify(wipeService, timeout(2000)).isRunning("guild-1");
        Mockito.verifyNoInteractions(guildSettingsService);
    }

    @Test
    void skipsGuildWhenNoVerifiedRoleConfigured() {
        when(wipeService.isRunning("guild-1")).thenReturn(false);
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(new GuildSettings("guild-1"));

        service.syncAllGuildsAsync(List.of(guild));

        verify(guildSettingsService, timeout(2000)).getOrCreate("guild-1");
        Mockito.verifyNoInteractions(verifiedUserRepository);
    }

    @Test
    void skipsGuildWhenVerifiedRoleNotFoundInGuild() {
        when(wipeService.isRunning("guild-1")).thenReturn(false);
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId("role-verified");
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settings);
        when(guild.getRoleById("role-verified")).thenReturn(null);

        service.syncAllGuildsAsync(List.of(guild));

        verify(guild, timeout(2000)).getRoleById("role-verified");
        Mockito.verifyNoInteractions(verifiedUserRepository);
    }

    @SuppressWarnings("unchecked")
    private Task<List<Member>> stubLoadMembers() {
        Task<List<Member>> task = mock(Task.class, Mockito.RETURNS_SELF);
        when(guild.loadMembers()).thenReturn(task);
        return task;
    }

    private void stubGuildWithVerifiedRole(Role role) {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setVerifiedRoleId("role-verified");
        when(wipeService.isRunning("guild-1")).thenReturn(false);
        when(guildSettingsService.getOrCreate("guild-1")).thenReturn(settings);
        when(guild.getRoleById("role-verified")).thenReturn(role);
    }

    @Test
    void skipsGuildWhenLoadMembersFails() {
        Role role = mock(Role.class);
        stubGuildWithVerifiedRole(role);
        Task<List<Member>> task = stubLoadMembers();
        when(task.get()).thenThrow(new RuntimeException("timed out"));

        service.syncAllGuildsAsync(List.of(guild));

        verify(guild, timeout(2000)).loadMembers();
        Mockito.verifyNoInteractions(verifiedUserRepository);
    }

    @Test
    void recordsSyncWithNoRemovalsWhenRoleCountMatchesDbCount() {
        Role role = mock(Role.class);
        stubGuildWithVerifiedRole(role);
        stubLoadMembers();

        VerifiedUser dbUser = new VerifiedUser("ais-1", "user-1", "guild-1", "user1@stuba.sk");
        when(verifiedUserRepository.findByGuildId("guild-1")).thenReturn(List.of(dbUser));
        when(guild.getMembersWithRoles(role)).thenReturn(List.of(mock(Member.class)));

        service.syncAllGuildsAsync(List.of(guild));

        verify(guildSettingsService, timeout(2000)).recordDatabaseSync("guild-1", 1, 0);
        verify(adminSettingsService).set("database_sync_removed_guild-1", List.of());
        Mockito.verifyNoInteractions(auditLogService);
    }

    @Test
    void removesStaleEntryWhenMemberNoLongerHoldsRole() {
        Role role = mock(Role.class);
        stubGuildWithVerifiedRole(role);
        stubLoadMembers();

        VerifiedUser dbUser = new VerifiedUser("ais-1", "user-1", "guild-1", "user1@stuba.sk");
        when(verifiedUserRepository.findByGuildId("guild-1")).thenReturn(List.of(dbUser));
        // roleMemberCount (0) != dbUsers.size() (1) triggers the removal scan
        when(guild.getMembersWithRoles(role)).thenReturn(List.of());
        when(guild.getMemberById("user-1")).thenReturn(null);

        service.syncAllGuildsAsync(List.of(guild));

        verify(verifiedUserRepository, timeout(2000)).deleteByDiscordIdAndGuildId("user-1", "guild-1");
        // auditLogService.log runs last in syncGuild()'s single-threaded execution, so waiting on it
        // here also guarantees the two plain verifies below (which happen strictly before it in that
        // same thread) have already completed - without this, they raced the async task on a loaded
        // CI runner and intermittently failed with zero interactions.
        verify(auditLogService, timeout(2000)).log(any());
        verify(guildSettingsService).recordDatabaseSync("guild-1", 1, 1);
        verify(adminSettingsService).set(eq("database_sync_removed_guild-1"),
                eq(List.of(new RemovedVerificationEntry("user-1", null, "ais-1", "user1@stuba.sk"))));
    }

    @Test
    void keepsEntryWhenMemberStillHoldsRoleDespiteCountMismatch() {
        Role role = mock(Role.class);
        stubGuildWithVerifiedRole(role);
        stubLoadMembers();

        VerifiedUser dbUser = new VerifiedUser("ais-1", "user-1", "guild-1", "user1@stuba.sk");
        when(verifiedUserRepository.findByGuildId("guild-1")).thenReturn(List.of(dbUser));
        when(guild.getMembersWithRoles(role)).thenReturn(List.of());

        Member member = mock(Member.class);
        Mockito.lenient().when(member.getRoles()).thenReturn(List.of(role));
        when(guild.getMemberById("user-1")).thenReturn(member);

        service.syncAllGuildsAsync(List.of(guild));

        verify(guildSettingsService, timeout(2000)).recordDatabaseSync("guild-1", 1, 0);
        Mockito.verify(verifiedUserRepository, never()).deleteByDiscordIdAndGuildId(anyString(), anyString());
        Mockito.verifyNoInteractions(auditLogService);
    }

    @Test
    void swallowsAuditLogFailureAfterRemoval() {
        Role role = mock(Role.class);
        stubGuildWithVerifiedRole(role);
        stubLoadMembers();

        VerifiedUser dbUser = new VerifiedUser("ais-1", "user-1", "guild-1", "user1@stuba.sk");
        when(verifiedUserRepository.findByGuildId("guild-1")).thenReturn(List.of(dbUser));
        when(guild.getMembersWithRoles(role)).thenReturn(List.of());
        when(guild.getMemberById("user-1")).thenReturn(null);
        Mockito.doThrow(new RuntimeException("audit db down")).when(auditLogService).log(any());

        service.syncAllGuildsAsync(List.of(guild));

        verify(guildSettingsService, timeout(2000)).recordDatabaseSync("guild-1", 1, 1);
        verify(auditLogService, timeout(2000)).log(any());
    }

    @Test
    void continuesWithRemainingGuildsWhenOneGuildSyncThrows() {
        Guild failingGuild = mock(Guild.class);
        Mockito.lenient().when(failingGuild.getId()).thenReturn("guild-bad");
        when(wipeService.isRunning("guild-bad")).thenThrow(new RuntimeException("boom"));

        when(wipeService.isRunning("guild-1")).thenReturn(true);

        service.syncAllGuildsAsync(List.of(failingGuild, guild));

        verify(wipeService, timeout(2000)).isRunning("guild-1");
    }
}
