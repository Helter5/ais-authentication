package sk.gkanocz.aisauth.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginFailureHandlerTest {

    private static final String FRONTEND_URL = "http://localhost:5173";

    private OAuth2LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginFailureHandler();
        ReflectionTestUtils.setField(handler, "frontendUrl", FRONTEND_URL);
    }

    private String redirectFor(AuthenticationException exception) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);
        return response.getRedirectedUrl();
    }

    @Test
    void nonOAuth2ExceptionRedirectsWithInvalidState() throws Exception {
        assertThat(redirectFor(new AuthenticationException("generic failure") { }))
                .isEqualTo(FRONTEND_URL + "/login?error=invalid_state");
    }

    @Test
    void invalidStateParameterRedirectsWithInvalidState() throws Exception {
        var exception = new OAuth2AuthenticationException(new OAuth2Error("invalid_state_parameter"));

        assertThat(redirectFor(exception)).isEqualTo(FRONTEND_URL + "/login?error=invalid_state");
    }

    @Test
    void invalidRequestRedirectsWithInvalidState() throws Exception {
        var exception = new OAuth2AuthenticationException(new OAuth2Error("invalid_request"));

        assertThat(redirectFor(exception)).isEqualTo(FRONTEND_URL + "/login?error=invalid_state");
    }

    @Test
    void invalidUserInfoResponseRedirectsWithFetchUserFailed() throws Exception {
        var exception = new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info_response"));

        assertThat(redirectFor(exception)).isEqualTo(FRONTEND_URL + "/login?error=fetch_user_failed");
    }

    @Test
    void anyOtherOAuth2ErrorCodeRedirectsWithOauthFailed() throws Exception {
        var exception = new OAuth2AuthenticationException(new OAuth2Error("server_error"));

        assertThat(redirectFor(exception)).isEqualTo(FRONTEND_URL + "/login?error=oauth_failed");
    }
}
