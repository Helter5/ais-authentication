package sk.gkanocz.aisauth.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.admin.AccessLog;
import sk.gkanocz.aisauth.admin.AccessLogRepository;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

import java.io.IOException;
import java.util.List;

/**
 * Replaces the old AuthController.discordCallback() body now that Spring Security's oauth2Login
 * owns the Discord authorization-code exchange + user-info fetch. Discord OAuth2 has already proven
 * identity by the time this runs; this only decides dashboard eligibility and mints the app's own
 * session token (see JwtService) - what KeycloakUserService.provisionAndIssueToken used to do.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AdminProperties adminProperties;
    private final EligibleGuildsResolver eligibleGuildsResolver;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AdminSessionRepository adminSessionRepository;
    private final AccessLogRepository accessLogRepository;
    private final DiscordBotService discordBotService;
    private final OAuthExchangeStore exchangeStore;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String discordId = oAuth2User.getAttribute("id");
        String username = oAuth2User.getAttribute("username");
        String avatar = oAuth2User.getAttribute("avatar");

        if (discordBotService.jda().isEmpty()) {
            // Bot not connected yet (e.g. right after a deploy) - computeEligibleGuildIds would
            // return an empty list for everyone, including super admins. Refuse the login instead
            // of silently minting a zero-guild session.
            redirectToFrontendLogin(response, "bot_not_ready");
            return;
        }

        boolean isSuperAdmin = adminProperties.superAdminIds().contains(discordId);
        List<String> eligibleGuildIds = eligibleGuildsResolver.computeEligibleGuildIds(discordId, isSuperAdmin);

        if (!isSuperAdmin && eligibleGuildIds.isEmpty()) {
            redirectToFrontendLogin(response, "unauthorized_manager_required");
            return;
        }

        JwtService.IssuedAccessToken issuedToken =
                jwtService.mintAccessToken(discordId, username, avatar, isSuperAdmin, eligibleGuildIds);
        adminSessionRepository.save(new AdminSession(issuedToken.jti(), discordId, issuedToken.expiresAt()));
        String refreshToken = refreshTokenService.issue(discordId, username, avatar, isSuperAdmin, eligibleGuildIds);
        recordAccessLog(discordId, username, eligibleGuildIds, clientIp(request));

        log.info("[OAuth SUCCESS] ({}) {} eligible guild(s)", username, eligibleGuildIds.size());

        String exchangeCode = exchangeStore.putToken(issuedToken.token(), refreshToken);
        response.sendRedirect(frontendUrl + "/select-server?code=" + exchangeCode);
    }

    private void recordAccessLog(String discordId, String username, List<String> guildIds, String ip) {
        for (String guildId : guildIds) {
            accessLogRepository.save(new AccessLog(discordId, username, "login", guildId, ip));
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void redirectToFrontendLogin(HttpServletResponse response, String error) throws IOException {
        response.sendRedirect(frontendUrl + "/login?error=" + error);
    }
}
