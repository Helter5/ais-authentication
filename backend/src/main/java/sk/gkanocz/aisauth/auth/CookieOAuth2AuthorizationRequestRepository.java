package sk.gkanocz.aisauth.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Optional;

/**
 * SecurityConfig runs with SessionCreationPolicy.STATELESS, so the default
 * HttpSessionOAuth2AuthorizationRequestRepository (which needs a servlet HttpSession to survive the
 * redirect round-trip to Discord and back) isn't a good fit. Stash the pending
 * OAuth2AuthorizationRequest (which carries the CSRF state + PKCE verifier) in a short-lived
 * httpOnly cookie instead - same style as the auth_token/refresh_token session cookies elsewhere in
 * this package.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_MAX_AGE_SECONDS = 15 * 60;

    private final SerializingConverter serializer = new SerializingConverter();
    private final DeserializingConverter deserializer = new DeserializingConverter();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request)
                .map(value -> (OAuth2AuthorizationRequest) deserializer.convert(Base64.getUrlDecoder().decode(value)))
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }
        byte[] serialized = serializer.convert(authorizationRequest);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(serialized);
        response.addCookie(cookie(value, COOKIE_MAX_AGE_SECONDS));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        deleteCookie(response);
        return authorizationRequest;
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
        response.addCookie(cookie("", 0));
    }

    private Cookie cookie(String value, int maxAge) {
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }
}
