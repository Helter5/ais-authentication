package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * This is the authorization gate on nearly every admin/dashboard controller: a bug here means
 * either legitimate managers get locked out, or someone gets access to a guild they shouldn't.
 */
class GuildAccessServiceTest {

    private final GuildAccessService guildAccessService = new GuildAccessService();

    private Claims claimsOf(boolean superAdmin, List<String> guildIds) {
        return Jwts.claims().add(Map.of("superAdmin", superAdmin, "guildIds", guildIds)).build();
    }

    private Claims claimsWithNoGuildIdsClaim() {
        return Jwts.claims().add("superAdmin", false).build();
    }

    @Test
    void isSuperAdminTrueOnlyWhenClaimIsExactlyTrue() {
        assertThat(guildAccessService.isSuperAdmin(claimsOf(true, List.of()))).isTrue();
        assertThat(guildAccessService.isSuperAdmin(claimsOf(false, List.of()))).isFalse();
    }

    @Test
    void isSuperAdminFalseWhenClaimMissingEntirely() {
        Claims claims = Jwts.claims().add("sub", "discord-1").build();

        assertThat(guildAccessService.isSuperAdmin(claims)).isFalse();
    }

    @Test
    void guildIdsReturnsEmptyListWhenClaimMissingInsteadOfThrowing() {
        assertThat(guildAccessService.guildIds(claimsWithNoGuildIdsClaim())).isEmpty();
    }

    @Test
    void guildIdsReturnsConfiguredList() {
        Claims claims = claimsOf(false, List.of("guild-1", "guild-2"));

        assertThat(guildAccessService.guildIds(claims)).containsExactly("guild-1", "guild-2");
    }

    @Test
    void canManageGuildTrueForSuperAdminRegardlessOfGuildList() {
        Claims claims = claimsOf(true, List.of());

        assertThat(guildAccessService.canManageGuild(claims, "any-guild")).isTrue();
    }

    @Test
    void canManageGuildTrueForManagerOfThatSpecificGuildOnly() {
        Claims claims = claimsOf(false, List.of("guild-1"));

        assertThat(guildAccessService.canManageGuild(claims, "guild-1")).isTrue();
        assertThat(guildAccessService.canManageGuild(claims, "guild-2")).isFalse();
    }

    @Test
    void assertSuperAdminThrowsForRegularManager() {
        Claims claims = claimsOf(false, List.of("guild-1"));

        assertThatThrownBy(() -> guildAccessService.assertSuperAdmin(claims))
                .isInstanceOf(GuildAccessDeniedException.class)
                .hasMessageContaining("Super Admin");
    }

    @Test
    void assertSuperAdminPassesSilentlyForSuperAdmin() {
        assertThatNoException().isThrownBy(() -> guildAccessService.assertSuperAdmin(claimsOf(true, List.of())));
    }

    @Test
    void assertCanManageGuildThrowsForUnrelatedGuild() {
        Claims claims = claimsOf(false, List.of("guild-1"));

        assertThatThrownBy(() -> guildAccessService.assertCanManageGuild(claims, "guild-2"))
                .isInstanceOf(GuildAccessDeniedException.class)
                .hasMessageContaining("Manager access");
    }

    @Test
    void assertCanManageGuildPassesSilentlyForGuildInList() {
        Claims claims = claimsOf(false, List.of("guild-1"));

        assertThatNoException().isThrownBy(() -> guildAccessService.assertCanManageGuild(claims, "guild-1"));
    }
}
