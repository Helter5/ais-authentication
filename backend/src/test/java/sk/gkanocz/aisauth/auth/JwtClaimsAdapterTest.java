package sk.gkanocz.aisauth.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtClaimsAdapterTest {

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "raw-token-value",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:05:00Z"),
                Map.of("alg", "HS256"),
                claims);
    }

    @Test
    void getSubjectPrefersTheDiscordIdClaimOverTheJwtsRealSubject() {
        JwtClaimsAdapter adapter = new JwtClaimsAdapter(jwt(Map.of("sub", "internal-uuid", "discord_id", "discord-1")));

        assertThat(adapter.getSubject()).isEqualTo("discord-1");
    }

    @Test
    void getSubjectFallsBackToTheRealSubjectWhenNoDiscordIdClaim() {
        JwtClaimsAdapter adapter = new JwtClaimsAdapter(jwt(Map.of("sub", "internal-uuid")));

        assertThat(adapter.getSubject()).isEqualTo("internal-uuid");
    }

    @Test
    void getIdReturnsTheJwtId() {
        Jwt jwt = new Jwt(
                "raw", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:05:00Z"),
                Map.of("alg", "HS256"), Map.of("sub", "u", "jti", "jti-123"));

        assertThat(new JwtClaimsAdapter(jwt).getId()).isEqualTo("jti-123");
    }

    @Test
    void getReturnsArbitraryClaimsByNameAndType() {
        JwtClaimsAdapter adapter = new JwtClaimsAdapter(jwt(Map.of("sub", "u", "superAdmin", true, "guildIds", List.of("g1", "g2"))));

        assertThat(adapter.get("superAdmin", Boolean.class)).isTrue();
        assertThat(adapter.get("guildIds", List.class)).containsExactly("g1", "g2");
    }

    @Test
    void getExpirationReflectsTheJwtsExpiresAt() {
        JwtClaimsAdapter adapter = new JwtClaimsAdapter(jwt(Map.of("sub", "u")));

        assertThat(adapter.getExpiration().toInstant()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));
    }

    @Test
    void entrySetExposesAllClaims() {
        JwtClaimsAdapter adapter = new JwtClaimsAdapter(jwt(Map.of("sub", "u", "username", "someuser")));

        assertThat(adapter.entrySet())
                .extracting(Map.Entry::getKey)
                .contains("sub", "username");
    }
}
