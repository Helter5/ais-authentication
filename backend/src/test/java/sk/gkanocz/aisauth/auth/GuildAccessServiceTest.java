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
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
@SuppressWarnings("unchecked")
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

    private GuildAccessService guildAccessService;

    @BeforeEach
    void setUp() {
        guildAccessService = new GuildAccessService(discordBotService, adminSettingsService);
    }

    private Claims claimsOf(boolean superAdmin, String discordId) {
        return Jwts.claims().add(Map.of("superAdmin", superAdmin, "sub", discordId)).build();
    }

    private void stubAllowedGuild(String guildId) {
        lenient().when(adminSettingsService.get(eq("allowed_guild_ids"), (TypeReference<List<String>>) any(), eq(List.of())))
                .thenReturn(List.of(guildId));
        lenient().when(discordBotService.jda()).thenReturn(Optional.of(jda));
        lenient().when(jda.getGuildById(guildId)).thenReturn(guild);
    }

    private void stubManagerRole(String guildId, String roleId) {
        lenient().when(adminSettingsService.get(
                        eq("dashboard_settings_" + guildId), eq(DashboardSettings.class), eq(DashboardSettings.empty())))
                .thenReturn(new DashboardSettings(List.of(roleId)));
    }

    @Test
    void isSuperAdminTrueOnlyWhenClaimIsExactlyTrue() {
        assertThat(guildAccessService.isSuperAdmin(claimsOf(true, "discord-1"))).isTrue();
        assertThat(guildAccessService.isSuperAdmin(claimsOf(false, "discord-1"))).isFalse();
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
        lenient().when(adminSettingsService.get(eq("allowed_guild_ids"), (TypeReference<List<String>>) any(), eq(List.of())))
                .thenReturn(List.of("some-other-guild"));

        assertThat(guildAccessService.canManageGuild(claimsOf(false, "discord-1"), "guild-1")).isFalse();
    }

    @Test
    void canManageGuildFalseWhenNoManagerRolesConfiguredForGuild() {
        stubAllowedGuild("guild-1");
        lenient().when(adminSettingsService.get(
                        eq("dashboard_settings_guild-1"), eq(DashboardSettings.class), eq(DashboardSettings.empty())))
                .thenReturn(DashboardSettings.empty());

        assertThat(guildAccessService.canManageGuild(claimsOf(false, "discord-1"), "guild-1")).isFalse();
    }

    @Test
    void canManageGuildFalseWhenBotNotConnected() {
        lenient().when(adminSettingsService.get(eq("allowed_guild_ids"), (TypeReference<List<String>>) any(), eq(List.of())))
                .thenReturn(List.of("guild-1"));
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
        lenient().when(adminSettingsService.get(eq("allowed_guild_ids"), (TypeReference<List<String>>) any(), eq(List.of())))
                .thenReturn(List.of());

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
