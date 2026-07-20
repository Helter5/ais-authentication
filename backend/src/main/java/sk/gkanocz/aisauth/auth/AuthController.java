package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
            @RequestParam(required = false) String state) {

        if (code == null || state == null || !exchangeStore.consumeState(state)) {
            return redirectToFrontendLogin("invalid_state");
        }

        DiscordOAuthClient.DiscordTokenResponse tokenResponse = discordOAuthClient.exchangeCodeForToken(code);
        DiscordOAuthClient.DiscordUserResponse user = discordOAuthClient.fetchUser(tokenResponse.accessToken());

        if (!adminProperties.superAdminIds().contains(user.id())) {
            return redirectToFrontendLogin("unauthorized_manager_required");
        }

        JwtService.IssuedToken issuedToken = jwtService.issueToken(user.id(), user.username(), user.avatar());
        adminSessionRepository.save(new AdminSession(issuedToken.jti(), user.id(), issuedToken.expiresAt()));

        String exchangeCode = exchangeStore.putToken(issuedToken.token());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/select-server?code=" + exchangeCode))
                .build();
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
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Claims claims, HttpServletResponse response) {
        adminSessionRepository.deleteByJti(claims.getId());
        response.addCookie(logoutCookie());
        return ResponseEntity.ok().build();
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

    public record CurrentUserResponse(String id, String username, String avatar) {
        static CurrentUserResponse fromClaims(Claims claims) {
            return new CurrentUserResponse(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    claims.get("avatar", String.class));
        }
    }
}
