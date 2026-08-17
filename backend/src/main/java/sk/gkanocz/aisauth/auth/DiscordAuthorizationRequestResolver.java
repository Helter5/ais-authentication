package sk.gkanocz.aisauth.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * DefaultOAuth2AuthorizationRequestResolver always matches "{baseUri}/{registrationId}" - handing
 * it baseUri "/api/auth" (to land the login-initiation URL on GET /api/auth/discord) makes it also
 * claim every other single-segment path under /api/auth/ (POST /api/auth/exchange,
 * /api/auth/refresh, /api/auth/logout, GET /api/auth/session), mis-parsing e.g. "exchange" as a
 * registrationId and throwing InvalidClientRegistrationIdException instead of falling through to
 * AuthController. There's only ever one registration ("discord"), so resolve it ourselves off an
 * exact path match and skip the templated matching entirely.
 */
@Component
public class DiscordAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String AUTHORIZATION_REQUEST_URI = "/api/auth/discord";
    private static final String REGISTRATION_ID = "discord";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public DiscordAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        // baseUri here is never actually matched against (resolve(request, registrationId) below
        // skips its internal matcher entirely) - any value that can't collide with a real route works.
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/api/auth/oauth2/unused-internal-base-uri");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        if (!AUTHORIZATION_REQUEST_URI.equals(request.getRequestURI())) {
            return null;
        }
        return delegate.resolve(request, REGISTRATION_ID);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return delegate.resolve(request, registrationId);
    }
}
