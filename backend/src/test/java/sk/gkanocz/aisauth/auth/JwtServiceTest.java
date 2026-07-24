package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService(new JwtProperties("test-only-secret-at-least-32-bytes-long!!", 60));

    @Test
    void issueThenParseRoundTripsAllClaims() {
        JwtService.IssuedToken issued = jwtService.issueToken(
                "discord-1", "some_user", "avatar-hash", true, List.of("guild-1", "guild-2"));

        Claims claims = jwtService.parseToken(issued.token());

        assertThat(claims.getSubject()).isEqualTo("discord-1");
        assertThat(claims.get("username", String.class)).isEqualTo("some_user");
        assertThat(claims.get("avatar", String.class)).isEqualTo("avatar-hash");
        assertThat(claims.get("superAdmin", Boolean.class)).isTrue();
        @SuppressWarnings("unchecked")
        List<String> guildIds = claims.get("guildIds", List.class);
        assertThat(guildIds).containsExactly("guild-1", "guild-2");
        assertThat(claims.getId()).isEqualTo(issued.jti());
    }

    @Test
    void issuedTokenJtiIsUniquePerCall() {
        JwtService.IssuedToken first = jwtService.issueToken("discord-1", "u", "a", false, List.of());
        JwtService.IssuedToken second = jwtService.issueToken("discord-1", "u", "a", false, List.of());

        assertThat(first.jti()).isNotEqualTo(second.jti());
    }

    @Test
    void issuedTokenExpiresAtRespectsConfiguredExpirationMinutes() {
        JwtService.IssuedToken issued = jwtService.issueToken("discord-1", "u", "a", false, List.of());

        LocalDateTime expected = LocalDateTime.now().plusMinutes(60);
        assertThat(issued.expiresAt()).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
    }

    @Test
    void parseTokenRejectsTokenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService(new JwtProperties("a-completely-different-secret-32-bytes!", 60));
        JwtService.IssuedToken issuedByOther = otherService.issueToken("discord-1", "u", "a", false, List.of());

        assertThatThrownBy(() -> jwtService.parseToken(issuedByOther.token()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseTokenRejectsExpiredToken() {
        JwtService alreadyExpiredService = new JwtService(new JwtProperties("test-only-secret-at-least-32-bytes-long!!", -1));
        JwtService.IssuedToken expired = alreadyExpiredService.issueToken("discord-1", "u", "a", false, List.of());

        assertThatThrownBy(() -> jwtService.parseToken(expired.token()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseTokenRejectsGarbageInput() {
        assertThatThrownBy(() -> jwtService.parseToken("not-a-jwt-at-all"))
                .isInstanceOf(JwtException.class);
    }
}
