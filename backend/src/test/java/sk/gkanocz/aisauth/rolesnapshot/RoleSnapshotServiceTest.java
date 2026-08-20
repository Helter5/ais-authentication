package sk.gkanocz.aisauth.rolesnapshot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleSnapshotServiceTest {

    @Mock
    private RoleSnapshotRepository roleSnapshotRepository;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private Guild guild;
    @Mock
    private Member member;
    @Mock
    private Role normalRole;
    @Mock
    private Role adminRole;

    private RoleSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new RoleSnapshotService(roleSnapshotRepository, adminSettingsService, new JsonMapper());
        lenient().when(guild.getId()).thenReturn("guild-1");
        lenient().when(member.getId()).thenReturn("member-1");
        lenient().when(normalRole.getId()).thenReturn("role-normal");
        lenient().when(adminRole.getId()).thenReturn("role-admin");
        // No dashboard exclude list configured by default - individual tests override via stubExclude().
        lenient().when(adminSettingsService.get(eq("cmd_settings_guild-1_refresh"), any(TypeReference.class), any()))
                .thenReturn(Map.of());
    }

    private void stubExclude(List<String> excludeRoleIds) {
        when(adminSettingsService.get(eq("cmd_settings_guild-1_refresh"), any(TypeReference.class), any()))
                .thenReturn(Map.of("excludeRoleIds", excludeRoleIds));
    }

    // ---- snapshot() ----

    @Test
    void snapshotDoesNothingWhenMemberIsNull() {
        service.snapshot(guild, null);

        verifyNoInteractions(roleSnapshotRepository);
    }

    @Test
    void snapshotSkipsElevatedRoles() {
        // lenient: see the comment on refreshDropsRolesThatBecameElevatedSinceTheSnapshot.
        lenient().when(adminRole.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(member.getRoles()).thenReturn(List.of(normalRole, adminRole));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.empty());

        service.snapshot(guild, member);

        ArgumentCaptor<RoleSnapshot> captor = ArgumentCaptor.forClass(RoleSnapshot.class);
        verify(roleSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getRoleIds()).contains("role-normal").doesNotContain("role-admin");
    }

    @Test
    void snapshotSkipsDashboardExcludedRoles() {
        stubExclude(List.of("role-normal"));
        when(member.getRoles()).thenReturn(List.of(normalRole));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.empty());

        service.snapshot(guild, member);

        verify(roleSnapshotRepository, never()).save(any());
        verify(roleSnapshotRepository, never()).delete(any());
    }

    @Test
    void snapshotWritesNothingNewWhenEveryRoleIsFilteredAndNoPriorRowExists() {
        // lenient: see the comment on refreshDropsRolesThatBecameElevatedSinceTheSnapshot.
        lenient().when(adminRole.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(member.getRoles()).thenReturn(List.of(adminRole));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.empty());

        service.snapshot(guild, member);

        verify(roleSnapshotRepository, never()).save(any());
        verify(roleSnapshotRepository, never()).delete(any());
    }

    @Test
    void snapshotDeletesAStalePriorRowWhenTheLatestDepartureHasNothingTrackable() {
        // Regression: leave (roles saved) -> rejoin, never /refresh -> pick up different roles ->
        // leave again with nothing trackable this time must drop the now-stale prior snapshot,
        // not silently leave it in place for /refresh to hand back later.
        lenient().when(adminRole.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(member.getRoles()).thenReturn(List.of(adminRole));
        RoleSnapshot existing = new RoleSnapshot("guild-1", "member-1", "[\"role-old\"]",
                LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(25));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.of(existing));

        service.snapshot(guild, member);

        verify(roleSnapshotRepository).delete(existing);
        verify(roleSnapshotRepository, never()).save(any());
    }

    @Test
    void snapshotOverwritesAnExistingRowInsteadOfCreatingASecondOne() {
        when(member.getRoles()).thenReturn(List.of(normalRole));
        RoleSnapshot existing = new RoleSnapshot("guild-1", "member-1", "[\"role-old\"]",
                LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(25));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.of(existing));

        service.snapshot(guild, member);

        assertThat(existing.getRoleIds()).contains("role-normal").doesNotContain("role-old");
        verify(roleSnapshotRepository, never()).save(any());
    }

    // ---- refresh() ----

    @Test
    void refreshReportsNoSnapshotWhenNoneExists() {
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.empty());

        RoleSnapshotService.RefreshOutcome outcome = service.refresh(guild, member);

        assertThat(outcome.hadSnapshot()).isFalse();
        assertThat(outcome.rolesToApply()).isEmpty();
    }

    @Test
    void refreshDeletesAndReportsNoSnapshotWhenExpired() {
        RoleSnapshot expired = new RoleSnapshot("guild-1", "member-1", "[\"role-normal\"]",
                LocalDateTime.now().minusDays(40), LocalDateTime.now().minusDays(10));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.of(expired));

        RoleSnapshotService.RefreshOutcome outcome = service.refresh(guild, member);

        assertThat(outcome.hadSnapshot()).isFalse();
        verify(roleSnapshotRepository).delete(expired);
    }

    @Test
    void refreshDropsRolesThatNoLongerExistOnTheGuild() {
        RoleSnapshot snapshot = new RoleSnapshot("guild-1", "member-1", "[\"role-normal\",\"role-deleted\"]",
                LocalDateTime.now(), LocalDateTime.now().plusDays(29));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.of(snapshot));
        when(guild.getRoleById("role-normal")).thenReturn(normalRole);
        when(guild.getRoleById("role-deleted")).thenReturn(null);

        RoleSnapshotService.RefreshOutcome outcome = service.refresh(guild, member);

        assertThat(outcome.hadSnapshot()).isTrue();
        assertThat(outcome.rolesToApply()).containsExactly(normalRole);
    }

    @Test
    void refreshDropsRolesThatBecameElevatedSinceTheSnapshot() {
        RoleSnapshot snapshot = new RoleSnapshot("guild-1", "member-1", "[\"role-admin\"]",
                LocalDateTime.now(), LocalDateTime.now().plusDays(29));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.of(snapshot));
        when(guild.getRoleById("role-admin")).thenReturn(adminRole);
        // lenient: isElevated() probes every ELEVATED_PERMISSIONS entry via anyMatch (Set order is
        // unspecified), so strict stubbing would flag the other, unstubbed hasPermission(...) calls
        // on this same mock as an argument mismatch even though only this one needs to return true.
        lenient().when(adminRole.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);

        RoleSnapshotService.RefreshOutcome outcome = service.refresh(guild, member);

        assertThat(outcome.hadSnapshot()).isTrue();
        assertThat(outcome.rolesToApply()).isEmpty();
    }

    @Test
    void refreshDropsRolesNewlyExcludedOnTheDashboard() {
        stubExclude(List.of("role-normal"));
        RoleSnapshot snapshot = new RoleSnapshot("guild-1", "member-1", "[\"role-normal\"]",
                LocalDateTime.now(), LocalDateTime.now().plusDays(29));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.of(snapshot));
        when(guild.getRoleById("role-normal")).thenReturn(normalRole);

        RoleSnapshotService.RefreshOutcome outcome = service.refresh(guild, member);

        assertThat(outcome.rolesToApply()).isEmpty();
    }

    @Test
    void refreshDoesNotConsumeTheSnapshot() {
        RoleSnapshot snapshot = new RoleSnapshot("guild-1", "member-1", "[\"role-normal\"]",
                LocalDateTime.now(), LocalDateTime.now().plusDays(29));
        when(roleSnapshotRepository.findByGuildIdAndDiscordId("guild-1", "member-1")).thenReturn(Optional.of(snapshot));
        when(guild.getRoleById("role-normal")).thenReturn(normalRole);

        service.refresh(guild, member);

        verify(roleSnapshotRepository, never()).delete(any());
        verify(roleSnapshotRepository, never()).deleteByGuildIdAndDiscordId(any(), any());
    }

    // ---- consumeSnapshot() ----

    @Test
    void consumeSnapshotDeletesByGuildAndDiscordId() {
        service.consumeSnapshot("guild-1", "member-1");

        verify(roleSnapshotRepository).deleteByGuildIdAndDiscordId("guild-1", "member-1");
    }
}
