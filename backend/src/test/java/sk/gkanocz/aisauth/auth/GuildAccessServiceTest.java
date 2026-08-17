package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * This is the authorization gate on nearly every admin/dashboard controller: a bug here means
 * either legitimate managers get locked out, or someone gets access to a guild they shouldn't.
 *
 * canManageGuild checks live JDA member-cache state on every call (not the guildIds JWT claim) so
 * a manager role revoked in Discord takes effect immediately instead of only at the next token
 * refresh - see GuildAccessService's javadoc for why.
 */
@ExtendWith(MockitoExtension.class)
class GuildAccessServiceTest {

    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private JDA jda;
    @Mock
    private Guild guild;
    @Mock
    private Member member;
    @Mock
    private Role managerRole;
    @Mock
    private AdminProperties adminProperties;

    private GuildAccessService guildAccessService;

    @BeforeEach
    void setUp() {
        lenient().when(adminProperties.superAdminIds()).thenReturn(List.of("discord-1"));
        guildAccessService = new GuildAccessService(discordBotService, adminSettingsService, adminProperties);
    }

    private Claims claimsOf(boolean superAdmin, String discordId) {
        return Jwts.claims().add(Map.of("superAdmin", superAdmin, "sub", discordId)).build();
    }

    private void stubAllowedGuild(String guildId) {
        lenient().when(adminSettingsService.isGuildAllowed(guildId)).thenReturn(true);
        lenient().when(discordBotService.jda()).thenReturn(Optional.of(jda));
        lenient().when(jda.getGuildById(guildId)).thenReturn(guild);
    }

    private void stubManagerRole(String guildId, String roleId) {
        lenient().when(adminSettingsService.dashboardSettings(guildId))
                .thenReturn(new DashboardSettings(List.of(roleId)));
    }

    @Test
    void isSuperAdminTrueOnlyWhenClaimIsExactlyTrue() {
        assertThat(guildAccessService.isSuperAdmin(claimsOf(true, "discord-1"))).isTrue();
        assertThat(guildAccessService.isSuperAdmin(claimsOf(false, "discord-1"))).isFalse();
    }

    @Test
    void isSuperAdminFalseWhenIdRemovedFromConfigEvenIfClaimStillTrue() {
        // Old, still-unexpired token claims superAdmin - but the ID was since removed from
        // SUPER_ADMIN_IDS and the app redeployed. Must lose access on this exact next call, not
        // only at the token's next refresh.
        assertThat(guildAccessService.isSuperAdmin(claimsOf(true, "discord-revoked"))).isFalse();
    }

    @Test
    void isSuperAdminFalseWhenClaimMissingEntirely() {
        Claims claims = Jwts.claims().add("sub", "discord-1").build();

        assertThat(guildAccessService.isSuperAdmin(claims)).isFalse();
    }

    @Test
    void guildIdsReturnsEmptyListWhenClaimMissingInsteadOfThrowing() {
        Claims claims = Jwts.claims().add("superAdmin", false).build();

        assertThat(guildAccessService.guildIds(claims)).isEmpty();
    }

    @Test
    void guildIdsReturnsConfiguredList() {
        Claims claims = Jwts.claims().add(Map.of("superAdmin", false, "guildIds", List.of("guild-1", "guild-2"))).build();

        assertThat(guildAccessService.guildIds(claims)).containsExactly("guild-1", "guild-2");
    }

    @Test
    void canManageGuildTrueForSuperAdminRegardlessOfLiveDiscordState() {
        Claims claims = claimsOf(true, "discord-1");

        assertThat(guildAccessService.canManageGuild(claims, "any-guild")).isTrue();
    }

    @Test
    void canManageGuildTrueWhenMemberCurrentlyHoldsTheConfiguredManagerRole() {
        stubAllowedGuild("guild-1");
        stubManagerRole("guild-1", "role-mgr");
        when(guild.getMemberById("discord-1")).thenReturn(member);
        when(member.getRoles()).thenReturn(List.of(managerRole));
        when(managerRole.getId()).thenReturn("role-mgr");

        assertThat(guildAccessService.canManageGuild(claimsOf(false, "discord-1"), "guild-1")).isTrue();
    }

    @Test
    void canManageGuildFalseWhenMemberNoLongerHoldsTheManagerRole() {
        stubAllowedGuild("guild-1");
        stubManagerRole("guild-1", "role-mgr");
        when(guild.getMemberById("discord-1")).thenReturn(member);
        when(member.getRoles()).thenReturn(List.of());

        assertThat(guildAccessService.canManageGuild(claimsOf(false, "discord-1"), "guild-1")).isFalse();
    }

    @Test
    void canManageGuildFalseWhenGuildNotInAllowedList() {
        lenient().when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(false);

        assertThat(guildAccessService.canManageGuild(claimsOf(false, "discord-1"), "guild-1")).isFalse();
    }

    @Test
    void canManageGuildFalseWhenNoManagerRolesConfiguredForGuild() {
        stubAllowedGuild("guild-1");
        lenient().when(adminSettingsService.dashboardSettings("guild-1")).thenReturn(DashboardSettings.empty());

        assertThat(guildAccessService.canManageGuild(claimsOf(false, "discord-1"), "guild-1")).isFalse();
    }

    @Test
    void canManageGuildFalseWhenBotNotConnected() {
        lenient().when(adminSettingsService.isGuildAllowed("guild-1")).thenReturn(true);
        lenient().when(discordBotService.jda()).thenReturn(Optional.empty());

        assertThat(guildAccessService.canManageGuild(claimsOf(false, "discord-1"), "guild-1")).isFalse();
    }

    @Test
    void assertSuperAdminThrowsForRegularManager() {
        assertThatThrownBy(() -> guildAccessService.assertSuperAdmin(claimsOf(false, "discord-1")))
                .isInstanceOf(GuildAccessDeniedException.class)
                .hasMessageContaining("Super Admin");
    }

    @Test
    void assertSuperAdminPassesSilentlyForSuperAdmin() {
        assertThatNoException().isThrownBy(() -> guildAccessService.assertSuperAdmin(claimsOf(true, "discord-1")));
    }

    @Test
    void assertCanManageGuildThrowsForUnrelatedGuild() {
        lenient().when(adminSettingsService.isGuildAllowed("guild-2")).thenReturn(false);

        assertThatThrownBy(() -> guildAccessService.assertCanManageGuild(claimsOf(false, "discord-1"), "guild-2"))
                .isInstanceOf(GuildAccessDeniedException.class)
                .hasMessageContaining("Manager access");
    }

    @Test
    void assertCanManageGuildPassesSilentlyForGuildInList() {
        stubAllowedGuild("guild-1");
        stubManagerRole("guild-1", "role-mgr");
        when(guild.getMemberById("discord-1")).thenReturn(member);
        when(member.getRoles()).thenReturn(List.of(managerRole));
        when(managerRole.getId()).thenReturn("role-mgr");

        assertThatNoException().isThrownBy(
                () -> guildAccessService.assertCanManageGuild(claimsOf(false, "discord-1"), "guild-1"));
    }
}
