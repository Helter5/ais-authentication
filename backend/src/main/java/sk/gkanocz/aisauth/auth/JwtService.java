package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints the dashboard's own access tokens - what used to be Keycloak's job (see the old
 * KeycloakUserService/KeycloakAdminClient password-grant hack, needed only because Discord isn't a
 * native Keycloak identity provider). Discord OAuth2 still proves who the user is; this signs the
 * resulting session token directly with a local HMAC secret. Same claim shape
 * {@link sk.gkanocz.aisauth.support.AuthenticatedRequestHelper} already used for tests.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    public IssuedAccessToken mintAccessToken(
            String discordId, String username, String avatar, boolean superAdmin, List<String> guildIds) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.accessTokenTtlSeconds(), ChronoUnit.SECONDS);

        String token = Jwts.builder()
                .subject(discordId)
                .claim("discord_id", discordId)
                .claim("username", username)
                .claim("avatar", avatar)
                .claim("superAdmin", superAdmin)
                .claim("guildIds", guildIds)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey(), Jwts.SIG.HS256)
                .compact();

        return new IssuedAccessToken(token, jti, LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
    }

    /**
     * Plain SecretKeySpec, not Keys.hmacShaKeyFor - that helper picks HS256/384/512 based on the
     * key's byte length, which silently disagreed with AuthBeansConfig.jwtDecoder()'s
     * hardcoded HS256 for any secret longer than ~48 bytes (e.g. a real `openssl rand -base64 48`
     * value ends up 64 bytes -> auto-selected HS512 -> decoder rejected it: "Another algorithm
     * expected"). Pinning the algorithm explicitly at both sign and decode time makes this
     * independent of how long app.jwt.secret happens to be.
     */
    private SecretKey secretKey() {
        return new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public record IssuedAccessToken(String token, String jti, LocalDateTime expiresAt) {
    }
}
