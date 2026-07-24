package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.admin.AccessLog;
import sk.gkanocz.aisauth.admin.AccessLogRepository;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.DashboardSettings;
import tools.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTH_COOKIE_NAME = "auth_token";

    private final DiscordOAuthProperties discordOAuthProperties;
    private final AdminProperties adminProperties;
    private final DiscordOAuthClient discordOAuthClient;
    private final JwtService jwtService;
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

        DiscordOAuthClient.DiscordTokenResponse tokenResponse = discordOAuthClient.exchangeCodeForToken(code);
        DiscordOAuthClient.DiscordUserResponse user = discordOAuthClient.fetchUser(tokenResponse.accessToken());

        boolean isSuperAdmin = adminProperties.superAdminIds().contains(user.id());
        List<String> eligibleGuildIds = computeEligibleGuildIds(user.id(), isSuperAdmin);

        if (!isSuperAdmin && eligibleGuildIds.isEmpty()) {
            return redirectToFrontendLogin("unauthorized_manager_required");
        }

        JwtService.IssuedToken issuedToken =
                jwtService.issueToken(user.id(), user.username(), user.avatar(), isSuperAdmin, eligibleGuildIds);
        adminSessionRepository.save(new AdminSession(issuedToken.jti(), user.id(), issuedToken.expiresAt()));
        recordAccessLog(user, eligibleGuildIds, clientIp(request));

        String exchangeCode = exchangeStore.putToken(issuedToken.token());
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
        String token = exchangeStore.consumeToken(request.code());
        if (token == null) {
            return ResponseEntity.badRequest().build();
        }

        Claims claims = jwtService.parseToken(token);
        response.addCookie(authCookie(token));
        return ResponseEntity.ok(new SessionResponse(CurrentUserResponse.fromClaims(claims)));
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(@AuthenticationPrincipal Claims claims) {
        return ResponseEntity.ok(new SessionResponse(CurrentUserResponse.fromClaims(claims)));
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Claims claims, HttpServletResponse response) {
        adminSessionRepository.deleteByJti(claims.getId());
        response.addCookie(logoutCookie());
        return ResponseEntity.ok().build();
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
                DashboardSettings dashboardSettings = adminSettingsService.get(
                        "dashboard_settings_" + guild.getId(), DashboardSettings.class, DashboardSettings.empty());
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
            return null;
        }
    }

    private Cookie logoutCookie() {
        Cookie cookie = new Cookie(AUTH_COOKIE_NAME, "");
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
