package sk.gkanocz.aisauth.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private static final JwtProperties JWT_PROPERTIES =
            new JwtProperties("test-only-signing-key-at-least-32-bytes-long!!", 300, 2592000);

    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository(JWT_PROPERTIES);

    private OAuth2AuthorizationRequest sampleRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://discord.com/api/oauth2/authorize")
                .clientId("client-1")
                .redirectUri("http://localhost:8080/api/auth/discord/callback")
                .state("state-1")
                .build();
    }

    private MockHttpServletRequest requestCarryingCookieFrom(MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cookie cookie = response.getCookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        request.setCookies(cookie);
        return request;
    }

    @Test
    void loadReturnsNullWhenNoCookieIsPresent() {
        assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
    }

    @Test
    void savedRequestRoundTripsThroughTheCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest original = sampleRequest();

        repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), response);
        MockHttpServletRequest loadRequest = requestCarryingCookieFrom(response);
        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded.getState()).isEqualTo("state-1");
        assertThat(loaded.getClientId()).isEqualTo("client-1");
        assertThat(loaded.getRedirectUri()).isEqualTo("http://localhost:8080/api/auth/discord/callback");
    }

    @Test
    void cookieIsHttpOnlyAndPathScopedToRoot() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), response);

        Cookie cookie = response.getCookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void savingNullDeletesTheCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

        Cookie cookie = response.getCookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME);
        assertThat(cookie.getMaxAge()).isZero();
    }

    @Test
    void removeReturnsThePreviouslySavedRequestAndClearsTheCookie() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), saveResponse);
        MockHttpServletRequest loadRequest = requestCarryingCookieFrom(saveResponse);

        MockHttpServletResponse removeResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(loadRequest, removeResponse);

        assertThat(removed.getState()).isEqualTo("state-1");
        assertThat(removeResponse.getCookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME).getMaxAge()).isZero();
    }

    @Test
    void loadReturnsNullForATamperedCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), response);
        Cookie cookie = response.getCookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookie.getValue() + "tampered"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadReturnsNullForAGarbageCookieValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, "not-a-jwt"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void loadReturnsNullForATokenSignedWithADifferentKey() {
        CookieOAuth2AuthorizationRequestRepository attackerRepository = new CookieOAuth2AuthorizationRequestRepository(
                new JwtProperties("a-completely-different-signing-key-32-bytes!!", 300, 2592000));
        MockHttpServletResponse forgedResponse = new MockHttpServletResponse();
        attackerRepository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), forgedResponse);
        MockHttpServletRequest request = requestCarryingCookieFrom(forgedResponse);

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }
}
