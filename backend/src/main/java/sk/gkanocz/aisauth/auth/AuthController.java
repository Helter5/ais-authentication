package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.admin.AccessLog;
import sk.gkanocz.aisauth.admin.AccessLogRepository;
import sk.gkanocz.aisauth.auth.keycloak.KeycloakAdminClient;
import sk.gkanocz.aisauth.auth.keycloak.KeycloakUserService;
import sk.gkanocz.aisauth.auth.keycloak.RefreshTokenInvalidException;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import tools.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTH_COOKIE_NAME = "auth_token";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final int REFRESH_COOKIE_MAX_AGE = 30 * 24 * 60 * 60;

    private final DiscordOAuthProperties discordOAuthProperties;
    private final AdminProperties adminProperties;
    private final DiscordOAuthClient discordOAuthClient;
    private final KeycloakUserService keycloakUserService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final JwtDecoder jwtDecoder;
    private final AdminSessionRepository adminSessionRepository;
    private final OAuthExchangeStore exchangeStore;
    private final DiscordBotService discordBotService;
    private final AdminSettingsService adminSettingsService;
    private final AccessLogRepository accessLogRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping("/discord")
    public ResponseEntity<Void> startDiscordLogin() {
        String state = exchangeStore.createState();
        String redirectUri = URLEncoder.encode(discordOAuthProperties.redirectUri(), StandardCharsets.UTF_8);

        String authorizeUrl = "https://discord.com/api/oauth2/authorize"
                + "?client_id=" + discordOAuthProperties.clientId()
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=identify"
                + "&state=" + state;

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authorizeUrl)).build();
    }

    @GetMapping("/discord/callback")
    public ResponseEntity<Void> discordCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request) {

        if (code == null || state == null || !exchangeStore.consumeState(state)) {
            return redirectToFrontendLogin("invalid_state");
        }

        if (discordBotService.jda().isEmpty()) {
            // Bot not connected yet (e.g. right after a deploy) - computeEligibleGuildIds would
            // return an empty list for everyone, including super admins, and that empty list gets
            // baked into the Keycloak user's guild_ids attribute and the minted token for its full
            // lifetime. Refuse the login instead of silently granting a zero-guild session.
            return redirectToFrontendLogin("bot_not_ready");
        }

        DiscordOAuthClient.DiscordTokenResponse tokenResponse = discordOAuthClient.exchangeCodeForToken(code);
        DiscordOAuthClient.DiscordUserResponse user = discordOAuthClient.fetchUser(tokenResponse.accessToken());

        boolean isSuperAdmin = adminProperties.superAdminIds().contains(user.id());
        List<String> eligibleGuildIds = computeEligibleGuildIds(user.id(), isSuperAdmin);

        if (!isSuperAdmin && eligibleGuildIds.isEmpty()) {
            return redirectToFrontendLogin("unauthorized_manager_required");
        }

        KeycloakUserService.IssuedToken issuedToken = keycloakUserService.provisionAndIssueToken(
                user.id(), user.username(), user.avatar(), isSuperAdmin, eligibleGuildIds);
        adminSessionRepository.save(new AdminSession(issuedToken.jti(), user.id(), issuedToken.expiresAt()));
        recordAccessLog(user, eligibleGuildIds, clientIp(request));

        String exchangeCode = exchangeStore.putToken(issuedToken.token(), issuedToken.refreshToken());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/select-server?code=" + exchangeCode))
                .build();
    }

    private void recordAccessLog(DiscordOAuthClient.DiscordUserResponse user, List<String> guildIds, String ip) {
        for (String guildId : guildIds) {
            accessLogRepository.save(new AccessLog(user.id(), user.username(), "login", guildId, ip));
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/exchange")
    public ResponseEntity<SessionResponse> exchange(
            @RequestBody ExchangeRequest request, HttpServletResponse response) {
        OAuthExchangeStore.ExchangedTokens tokens = exchangeStore.consumeToken(request.code());
        if (tokens == null) {
            return ResponseEntity.badRequest().build();
        }

        Claims claims = new JwtClaimsAdapter(jwtDecoder.decode(tokens.accessToken()));
        response.addCookie(authCookie(tokens.accessToken()));
        response.addCookie(refreshCookie(tokens.refreshToken()));
        return ResponseEntity.ok(new SessionResponse(CurrentUserResponse.fromClaims(claims)));
    }

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
     * Also re-derives guild eligibility from live Discord roles on every call (mirroring
     * discordCallback) and re-provisions the Keycloak user if it changed. Without this, a session
     * kept alive purely by silent refreshes would keep whatever guildIds/superAdmin claims were
     * baked in at the last full Discord login for up to ssoSessionMaxLifespan (30 days) - a revoked
     * manager role or removed guild wouldn't take effect until the browser did a fresh OAuth login.
     * Skipped entirely if the bot isn't connected right now (e.g. mid-deploy), since JDA having zero
     * guilds would otherwise look identical to "revoked from everything" and log out every manager.
     */
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
        if (refreshToken == null) {
            throw RefreshTokenInvalidException.create();
        }

        KeycloakAdminClient.MintedToken minted = keycloakAdminClient.refreshAccessToken(refreshToken);
        Claims claims = new JwtClaimsAdapter(jwtDecoder.decode(minted.accessToken()));

        if (discordBotService.jda().isPresent()) {
            minted = reconcileEligibility(minted, claims);
            claims = new JwtClaimsAdapter(jwtDecoder.decode(minted.accessToken()));
        }

        LocalDateTime expiresAt =
                LocalDateTime.ofInstant(Instant.ofEpochSecond(minted.expiresAtEpochSeconds()), ZoneId.systemDefault());
        adminSessionRepository.save(new AdminSession(minted.jti(), claims.getSubject(), expiresAt));

        response.addCookie(authCookie(minted.accessToken()));
        response.addCookie(refreshCookie(minted.refreshToken()));
        return ResponseEntity.ok().build();
    }

    @SuppressWarnings("unchecked")
    private KeycloakAdminClient.MintedToken reconcileEligibility(
            KeycloakAdminClient.MintedToken minted, Claims claims) {
        String discordId = claims.getSubject();
        boolean isSuperAdmin = adminProperties.superAdminIds().contains(discordId);
        List<String> eligibleGuildIds = computeEligibleGuildIds(discordId, isSuperAdmin);

        if (!isSuperAdmin && eligibleGuildIds.isEmpty()) {
            keycloakAdminClient.revokeRefreshToken(minted.refreshToken());
            throw RefreshTokenInvalidException.create();
        }

        boolean superAdminClaim = Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class));
        List<String> guildIdsClaim = claims.get("guildIds", List.class);
        if (isSuperAdmin == superAdminClaim
                && eligibleGuildIds.equals(guildIdsClaim == null ? List.of() : guildIdsClaim)) {
            return minted;
        }

        keycloakAdminClient.findOrCreateUser(
                discordId, claims.get("username", String.class), claims.get("avatar", String.class),
                isSuperAdmin, eligibleGuildIds);
        return keycloakAdminClient.refreshAccessToken(minted.refreshToken());
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Claims claims, HttpServletRequest request, HttpServletResponse response) {
        adminSessionRepository.deleteByJti(claims.getId());
        String refreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
        if (refreshToken != null) {
            keycloakAdminClient.revokeRefreshToken(refreshToken);
        }
        response.addCookie(expireCookie(AUTH_COOKIE_NAME));
        response.addCookie(expireCookie(REFRESH_COOKIE_NAME));
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
     * Super admins can manage every guild the bot is in. Regular managers are restricted to
     * guilds explicitly onboarded via allowed_guild_ids AND where they hold one of that guild's
     * configured manager roles.
     */
    private List<String> computeEligibleGuildIds(String discordId, boolean isSuperAdmin) {
        return discordBotService.jda().map(jda -> {
            List<String> allowedGuildIds = adminSettingsService.get(
                    "allowed_guild_ids", new TypeReference<List<String>>() { }, List.of());

            List<String> eligible = new ArrayList<>();
            for (Guild guild : jda.getGuilds()) {
                if (isSuperAdmin) {
                    eligible.add(guild.getId());
                    continue;
                }
                if (!allowedGuildIds.contains(guild.getId())) {
                    continue;
                }
                DashboardSettings dashboardSettings = adminSettingsService.dashboardSettings(guild.getId());
                if (dashboardSettings.managerRoleIds().isEmpty()) {
                    continue;
                }
                Member member = fetchMemberSafely(guild, discordId);
                boolean hasManagerRole = member != null && member.getRoles().stream()
                        .anyMatch(role -> dashboardSettings.managerRoleIds().contains(role.getId()));
                if (hasManagerRole) {
                    eligible.add(guild.getId());
                }
            }
            return eligible;
        }).orElseGet(List::of);
    }

    private Member fetchMemberSafely(Guild guild, String discordId) {
        try {
            return guild.retrieveMemberById(discordId).complete();
        } catch (Exception e) {
            log.warn("Could not fetch member {} in guild {} while computing manager-role eligibility: {}",
                    discordId, guild.getId(), e.getMessage());
            return null;
        }
    }

    private Cookie expireCookie(String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        return cookie;
    }

    private Cookie authCookie(String token) {
        Cookie cookie = new Cookie(AUTH_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);
        return cookie;
    }

    private Cookie refreshCookie(String token) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(REFRESH_COOKIE_MAX_AGE);
        return cookie;
    }

    private ResponseEntity<Void> redirectToFrontendLogin(String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/login?error=" + error))
                .build();
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
