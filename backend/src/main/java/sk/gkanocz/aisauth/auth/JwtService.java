package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    public IssuedToken issueToken(String discordId, String username, String avatar, boolean superAdmin, List<String> guildIds) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.expirationMinutes(), ChronoUnit.MINUTES);

        String token = Jwts.builder()
                .subject(discordId)
                .claim("username", username)
                .claim("avatar", avatar)
                .claim("superAdmin", superAdmin)
                .claim("guildIds", guildIds)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey())
                .compact();

        LocalDateTime expiresAtLocal = LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());
        return new IssuedToken(token, jti, expiresAtLocal);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedToken(String token, String jti, LocalDateTime expiresAt) {
    }
}
