package sk.gkanocz.aisauth.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordAuthorizationRequestResolverTest {

    private ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration discord = ClientRegistration.withRegistrationId("discord")
                .clientId("client-1")
                .clientSecret("secret-1")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/api/auth/discord/callback")
                .scope("identify")
                .authorizationUri("https://discord.com/api/oauth2/authorize")
                .tokenUri("https://discord.com/api/oauth2/token")
                .userInfoUri("https://discord.com/api/users/@me")
                .userNameAttributeName("id")
                .clientName("Discord")
                .build();
        return new InMemoryClientRegistrationRepository(discord);
    }

    @Test
    void resolveReturnsNullForAnyPathOtherThanTheDiscordLoginUri() {
        DiscordAuthorizationRequestResolver resolver = new DiscordAuthorizationRequestResolver(clientRegistrationRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/exchange");

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void resolveBuildsAnAuthorizationRequestForTheExactDiscordLoginUri() {
        DiscordAuthorizationRequestResolver resolver = new DiscordAuthorizationRequestResolver(clientRegistrationRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/discord");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getClientId()).isEqualTo("client-1");
        assertThat(authorizationRequest.getAuthorizationUri()).isEqualTo("https://discord.com/api/oauth2/authorize");
        assertThat(authorizationRequest.getRedirectUri()).isEqualTo("http://localhost:8080/api/auth/discord/callback");
    }

    @Test
    void resolveWithExplicitRegistrationIdDelegatesRegardlessOfRequestPath() {
        DiscordAuthorizationRequestResolver resolver = new DiscordAuthorizationRequestResolver(clientRegistrationRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request, "discord");

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getClientId()).isEqualTo("client-1");
    }
}
