package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

import java.util.List;

/**
 * Login itself (Discord OAuth2 authorization-code exchange + user-info fetch) is now owned by
 * Spring Security's oauth2Login filters (see SecurityConfig, OAuth2LoginSuccessHandler); this
 * controller only covers what happens after: handing the resulting one-time exchange code to the
 * frontend as cookies, reading the current session, and refresh/logout.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTH_COOKIE_NAME = "auth_token";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final int REFRESH_COOKIE_MAX_AGE = 30 * 24 * 60 * 60;

    private final AdminProperties adminProperties;
    private final JwtService jwtService;
    private final JwtDecoder jwtDecoder;
    private final AdminSessionRepository adminSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final EligibleGuildsResolver eligibleGuildsResolver;
    private final OAuthExchangeStore exchangeStore;
    private final DiscordBotService discordBotService;
    private final SessionCookieFactory cookieFactory;
    private final AuthEndpointRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @PublicToAuthenticated
    @PostMapping("/exchange")
    public ResponseEntity<SessionResponse> exchange(
            @RequestBody ExchangeRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        rateLimiter.checkAndRecordAttempt("exchange", clientIpResolver.resolve(httpRequest));
        OAuthExchangeStore.ExchangedTokens tokens = exchangeStore.consumeToken(request.code());
        if (tokens == null) {
            return ResponseEntity.badRequest().build();
        }

        Claims claims = new JwtClaimsAdapter(jwtDecoder.decode(tokens.accessToken()));
        setAuthCookies(response, tokens.accessToken(), tokens.refreshToken());
        return ResponseEntity.ok(new SessionResponse(CurrentUserResponse.fromClaims(claims)));
    }

    @PublicToAuthenticated
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(@AuthenticationPrincipal Claims claims) {
        return ResponseEntity.ok(new SessionResponse(CurrentUserResponse.fromClaims(claims)));
    }

    /**
     * Exchanges the refresh_token cookie for a fresh access/refresh token pair once the short-lived
     * access token has expired. Deliberately not behind @AuthenticationPrincipal - by the time the
     * frontend calls this, the access token backing the current request has already failed to
     * authenticate, so authentication here rests entirely on the refresh token instead.
     *
     * Also re-derives guild eligibility from live Discord roles on every call when the bot is
     * connected (mirroring the login flow), and revokes the session outright if that leaves the
     * user with nothing to manage - a revoked manager role or removed guild takes effect on this
     * exact refresh instead of only at the next full Discord login. Skipped entirely if the bot
     * isn't connected right now (e.g. mid-deploy): the refresh token's stored baseline is reused
     * as-is, since JDA having zero guilds would otherwise look identical to "revoked from
     * everything" and log out every manager.
     */
    @PublicToAuthenticated
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        rateLimiter.checkAndRecordAttempt("refresh", clientIpResolver.resolve(request));
        String refreshTokenCookie = extractCookie(request, REFRESH_COOKIE_NAME);
        if (refreshTokenCookie == null) {
            throw RefreshTokenInvalidException.create();
        }

        RefreshTokenService.RefreshSession previous = refreshTokenService.consume(refreshTokenCookie);

        boolean isSuperAdmin = previous.superAdmin();
        List<String> guildIds = previous.guildIds();
        if (discordBotService.jda().isPresent()) {
            isSuperAdmin = adminProperties.superAdminIds().contains(previous.discordId());
            guildIds = eligibleGuildsResolver.computeEligibleGuildIds(previous.discordId(), isSuperAdmin);
            if (!isSuperAdmin && guildIds.isEmpty()) {
                throw RefreshTokenInvalidException.create();
            }
        }

        JwtService.IssuedAccessToken issued = jwtService.mintAccessToken(
                previous.discordId(), previous.username(), previous.avatar(), isSuperAdmin, guildIds);
        adminSessionRepository.save(new AdminSession(issued.jti(), previous.discordId(), issued.expiresAt()));
        String newRefreshToken = refreshTokenService.issue(
                previous.discordId(), previous.username(), previous.avatar(), isSuperAdmin, guildIds);

        setAuthCookies(response, issued.token(), newRefreshToken);
        return ResponseEntity.ok().build();
    }

    @PublicToAuthenticated
    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Claims claims, HttpServletRequest request, HttpServletResponse response) {
        adminSessionRepository.deleteByJti(claims.getId());
        String refreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
        cookieFactory.expireStrict(response, AUTH_COOKIE_NAME);
        cookieFactory.expireStrict(response, REFRESH_COOKIE_NAME);
        return ResponseEntity.ok().build();
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * SameSite=Strict on both (see SessionCookieFactory javadoc): the SPA only ever sends these via
     * same-origin XHR/fetch, never a cross-site navigation, so Strict is safe and is what makes
     * disabling CSRF protection in SecurityConfig a deliberate, verifiable choice.
     */
    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        cookieFactory.setStrict(response, AUTH_COOKIE_NAME, accessToken, 24 * 60 * 60);
        cookieFactory.setStrict(response, REFRESH_COOKIE_NAME, refreshToken, REFRESH_COOKIE_MAX_AGE);
    }

    public record ExchangeRequest(String code) {
    }

    public record SessionResponse(CurrentUserResponse user) {
    }

    public record CurrentUserResponse(String id, String username, String avatar, List<String> guildIds) {
        static CurrentUserResponse fromClaims(Claims claims) {
            return new CurrentUserResponse(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    claims.get("avatar", String.class),
                    guildIdsFromClaims(claims));
        }

        @SuppressWarnings("unchecked")
        private static List<String> guildIdsFromClaims(Claims claims) {
            List<String> guildIds = claims.get("guildIds", List.class);
            return guildIds == null ? List.of() : guildIds;
        }
    }
}
