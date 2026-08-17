package sk.gkanocz.aisauth.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Catches whatever the oauth2Login filters throw before OAuth2LoginSuccessHandler ever runs -
 * mismatched/expired CSRF state (invalid_state, formerly checked by hand against
 * OAuthExchangeStore.pendingStates), a failed authorization-code exchange, or a failed
 * users/@me fetch. Mirrors the old AuthController.discordCallback's catch-all redirect.
 */
@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String error = "invalid_state";
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception) {
            error = switch (oAuth2Exception.getError().getErrorCode()) {
                case "invalid_state_parameter", "invalid_request" -> "invalid_state";
                case "invalid_user_info_response" -> "fetch_user_failed";
                default -> "oauth_failed";
            };
        }
        log.warn("Discord OAuth2 login failed: {}", exception.getMessage());
        response.sendRedirect(frontendUrl + "/login?error=" + error);
    }
}
