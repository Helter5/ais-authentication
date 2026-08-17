package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-only-signing-key-at-least-32-bytes-long!!";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 300, 2_592_000));
    }

    @Test
    void mintedTokenCarriesAllExpectedClaims() {
        JwtService.IssuedAccessToken issued = jwtService.mintAccessToken(
                "discord-1", "someuser", "avatar-hash", true, List.of("guild-1", "guild-2"));

        Claims claims = decode(issued.token());

        assertThat(claims.getSubject()).isEqualTo("discord-1");
        assertThat(claims.get("discord_id", String.class)).isEqualTo("discord-1");
        assertThat(claims.get("username", String.class)).isEqualTo("someuser");
        assertThat(claims.get("avatar", String.class)).isEqualTo("avatar-hash");
        assertThat(claims.get("superAdmin", Boolean.class)).isTrue();
        assertThat(claims.get("guildIds", List.class)).containsExactly("guild-1", "guild-2");
        assertThat(claims.getId()).isEqualTo(issued.jti());
    }

    @Test
    void mintedTokenExpiresAfterConfiguredTtl() {
        LocalDateTime before = LocalDateTime.now().plusSeconds(300);
        JwtService.IssuedAccessToken issued = jwtService.mintAccessToken(
                "discord-1", "someuser", null, false, List.of());
        LocalDateTime after = LocalDateTime.now().plusSeconds(300);

        assertThat(issued.expiresAt()).isBetween(before.minusSeconds(2), after.plusSeconds(2));
    }

    @Test
    void eachMintedTokenGetsAUniqueJti() {
        JwtService.IssuedAccessToken first = jwtService.mintAccessToken("discord-1", "u", null, false, List.of());
        JwtService.IssuedAccessToken second = jwtService.mintAccessToken("discord-1", "u", null, false, List.of());

        assertThat(first.jti()).isNotEqualTo(second.jti());
    }

    @Test
    void tokenIsRejectedWhenSignedWithADifferentSecret() {
        JwtService.IssuedAccessToken issued = jwtService.mintAccessToken("discord-1", "u", null, false, List.of());
        SecretKeySpec wrongKey = new SecretKeySpec(
                "a-completely-different-signing-key-32b".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        assertThatThrownBy(() -> Jwts.parser().verifyWith(wrongKey).build().parseSignedClaims(issued.token()))
                .isInstanceOf(JwtException.class);
    }

    /**
     * Regression guard for the HS256/HS512 mismatch this session's real bug: verifies the algorithm
     * is always HS256 regardless of secret length, matching AuthBeansConfig.jwtDecoder()'s fixed
     * expectation - not just that *a* key of the right length happens to verify it.
     */
    @Test
    void tokenIsAlwaysSignedWithHs256EvenForALongSecret() {
        String longSecret = "x".repeat(64); // e.g. what openssl rand -base64 48 produces
        JwtService longSecretService = new JwtService(new JwtProperties(longSecret, 300, 2_592_000));
        JwtService.IssuedAccessToken issued = longSecretService.mintAccessToken("discord-1", "u", null, false, List.of());

        String[] parts = issued.token().split("\\.");
        String header = new String(java.util.Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    private Claims decode(String token) {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
