package sk.gkanocz.aisauth.support;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.TestcontainersConfiguration;
import sk.gkanocz.aisauth.auth.AdminSession;
import sk.gkanocz.aisauth.auth.AdminSessionRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues a real JWT (signed with the fixed test key that {@link TestcontainersConfiguration}'s
 * JwtDecoder bean trusts - no live Keycloak needed) + a matching admin_sessions row, since
 * JwtAuthenticationFilter requires both (a valid signature AND a live session row) before it'll
 * authenticate a request. Lets integration tests exercise the real security filter chain instead
 * of mocking auth away.
 */
@Component
public class AuthenticatedRequestHelper {

    @Autowired
    private AdminSessionRepository adminSessionRepository;

    public String tokenFor(String discordId, boolean superAdmin, List<String> guildIds) {
        IssuedToken issued = rawIssue(discordId, "test_user", superAdmin, guildIds);
        adminSessionRepository.save(new AdminSession(issued.jti(), discordId, LocalDateTime.now().plusHours(1)));
        return issued.token();
    }

    /** Mints a token WITHOUT persisting an admin_sessions row - lets tests simulate a revoked/logged-out session. */
    public IssuedToken rawIssue(String discordId, String username, boolean superAdmin, List<String> guildIds) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        String token = Jwts.builder()
                .subject(discordId)
                .claim("discord_id", discordId)
                .claim("username", username)
                .claim("superAdmin", superAdmin)
                .claim("guildIds", guildIds)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(TestcontainersConfiguration.TEST_JWT_SIGNING_KEY)
                .compact();

        return new IssuedToken(token, jti, LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
    }

    public String superAdminToken() {
        return tokenFor("super-admin-1", true, List.of());
    }

    public String managerTokenFor(String guildId) {
        return tokenFor("manager-1", false, List.of(guildId));
    }

    public String bearer(String token) {
        return "Bearer " + token;
    }

    public record IssuedToken(String token, String jti, LocalDateTime expiresAt) {
    }
}
