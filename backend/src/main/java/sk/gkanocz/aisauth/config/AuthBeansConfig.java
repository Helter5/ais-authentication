package sk.gkanocz.aisauth.config;

import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import sk.gkanocz.aisauth.auth.DiscordOAuthProperties;
import sk.gkanocz.aisauth.auth.JwtProperties;

import java.nio.charset.StandardCharsets;

/**
 * Discord isn't one of Spring Security's built-in CommonOAuth2Provider entries, so its
 * ClientRegistration is hand-assembled here instead of coming from
 * spring.security.oauth2.client.registration.* auto-config. Endpoint paths (authorizationEndpoint /
 * redirectionEndpoint baseUri in SecurityConfig) are set to match this registration's redirectUri
 * exactly, so DISCORD_REDIRECT_URI / the Discord Developer Portal app config don't need to change.
 *
 * <p>The JwtDecoder here validates the app's own locally-signed session tokens (see JwtService) -
 * no external issuer, no JWKS fetch, same fixed-key setup tests already use via
 * TestcontainersConfiguration.
 */
@Configuration
@RequiredArgsConstructor
public class AuthBeansConfig {

    private final DiscordOAuthProperties discordOAuthProperties;
    private final JwtProperties jwtProperties;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        // ClientRegistration.Builder#build() rejects a blank clientId for the authorization_code
        // grant type. DISCORD_CLIENT_ID/SECRET are optional at startup (README: "appka beží, ale
        // login/bot sa nepripoja") - the old hand-rolled DiscordOAuthClient only ever failed this
        // at request time against Discord's API, never crashed the whole context. Fall back to a
        // placeholder so the bean (and app) still boots; an actual login attempt then fails
        // normally against Discord instead of preventing startup entirely.
        ClientRegistration discord = ClientRegistration.withRegistrationId("discord")
                .clientId(orPlaceholder(discordOAuthProperties.clientId()))
                .clientSecret(orPlaceholder(discordOAuthProperties.clientSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(discordOAuthProperties.redirectUri())
                .scope("identify")
                .authorizationUri("https://discord.com/api/oauth2/authorize")
                .tokenUri("https://discord.com/api/oauth2/token")
                .userInfoUri("https://discord.com/api/users/@me")
                .userNameAttributeName("id")
                .clientName("Discord")
                .build();
        return new InMemoryClientRegistrationRepository(discord);
    }

    private String orPlaceholder(String value) {
        return value == null || value.isBlank() ? "unconfigured" : value;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
