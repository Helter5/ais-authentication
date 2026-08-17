package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SecurityConfig runs with SessionCreationPolicy.STATELESS, so the default
 * HttpSessionOAuth2AuthorizationRequestRepository (which needs a servlet HttpSession to survive the
 * redirect round-trip to Discord and back) isn't a good fit. Stash the pending
 * OAuth2AuthorizationRequest (which carries the CSRF state) in a short-lived httpOnly cookie instead
 * - same style as the auth_token/refresh_token session cookies elsewhere in this package.
 *
 * Encoded as a signed JWT (HS256, same convention as {@link JwtService}), not Java serialization:
 * an earlier version ran the raw cookie bytes through Spring's DeserializingConverter
 * (ObjectInputStream.readObject()) on GET /api/auth/discord/callback, a permitAll()/unauthenticated
 * endpoint that deserializes before any OAuth state check - unauthenticated attacker-controlled
 * ObjectInputStream input is a gadget-chain RCE risk (CWE-502) regardless of what's on the classpath.
 * Only the handful of primitive fields Spring Security's OAuth2LoginAuthenticationFilter actually
 * reads back (see requiresAuthentication()) are carried, and the JWT signature makes the cookie
 * tamper-evident on top of no longer touching ObjectInputStream at all.
 */
@Component
@RequiredArgsConstructor
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_MAX_AGE_SECONDS = 15 * 60;

    private final JwtProperties jwtProperties;
    private final SessionCookieFactory cookieFactory;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request).flatMap(this::decode).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }
        cookieFactory.setLax(response, COOKIE_NAME, encode(authorizationRequest), COOKIE_MAX_AGE_SECONDS);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        deleteCookie(response);
        return authorizationRequest;
    }

    private String encode(OAuth2AuthorizationRequest authorizationRequest) {
        return Jwts.builder()
                .claim("authorizationUri", authorizationRequest.getAuthorizationUri())
                .claim("clientId", authorizationRequest.getClientId())
                .claim("redirectUri", authorizationRequest.getRedirectUri())
                .claim("state", authorizationRequest.getState())
                .claim("scopes", authorizationRequest.getScopes())
                .claim("additionalParameters", stringify(authorizationRequest.getAdditionalParameters()))
                .claim("attributes", stringify(authorizationRequest.getAttributes()))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(COOKIE_MAX_AGE_SECONDS, ChronoUnit.SECONDS)))
                .signWith(secretKey(), Jwts.SIG.HS256)
                .compact();
    }

    @SuppressWarnings("unchecked")
    private Optional<OAuth2AuthorizationRequest> decode(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey()).build().parseSignedClaims(token).getPayload();
            return Optional.of(OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(claims.get("authorizationUri", String.class))
                    .clientId(claims.get("clientId", String.class))
                    .redirectUri(claims.get("redirectUri", String.class))
                    .state(claims.get("state", String.class))
                    .scopes(Set.copyOf(claims.get("scopes", List.class)))
                    .additionalParameters((Map<String, Object>) (Map<String, ?>) claims.get("additionalParameters", Map.class))
                    .attributes((Map<String, Object>) (Map<String, ?>) claims.get("attributes", Map.class))
                    .build());
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // Tampered, expired, foreign, or malformed token - treat exactly like a missing cookie
            // (matches the old behaviour on a deserialization failure).
            return Optional.empty();
        }
    }

    /** additionalParameters/attributes values are plain OAuth2 request params in this flow (no PKCE,
     * single "discord" registration) - flattened to String so the round-trip through JWT claims (JSON)
     * is unambiguous instead of guessing arbitrary Object types back from parsed JSON. */
    private Map<String, String> stringify(Map<String, Object> source) {
        Map<String, String> result = new HashMap<>();
        source.forEach((key, value) -> {
            if (value != null) {
                result.put(key, value.toString());
            }
        });
        return result;
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void deleteCookie(HttpServletResponse response) {
        cookieFactory.expireLax(response, COOKIE_NAME);
    }
}
