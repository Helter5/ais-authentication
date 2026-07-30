package sk.gkanocz.aisauth;

import io.jsonwebtoken.security.Keys;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Boots a real Postgres for tests instead of requiring one to be running locally. Sharing this
 * across @SpringBootTest classes means the whole entity set gets validated against real schema
 * (ddl-auto=validate) on every test run, not just at deploy time.
 *
 * <p>Also provides a fixed-key {@link JwtDecoder} bean so tests don't need a real Keycloak
 * container: it satisfies Spring's {@code @ConditionalOnMissingBean(JwtDecoder.class)} on the
 * issuer-uri-based resource-server autoconfiguration, so that one never attempts the network call
 * to Keycloak's discovery endpoint during test context startup. {@link
 * sk.gkanocz.aisauth.support.AuthenticatedRequestHelper} signs test tokens with this same key.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final SecretKey TEST_JWT_SIGNING_KEY =
            Keys.hmacShaKeyFor("test-only-signing-key-at-least-32-bytes-long!!".getBytes(StandardCharsets.UTF_8));

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(TEST_JWT_SIGNING_KEY)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Real DiscordBotService never connects in tests (no bot token configured), so its jda()
     * always returns Optional.empty(). GuildAccessService.canManageGuild now checks live JDA
     * member-cache state instead of the guildIds JWT claim (see its javadoc), so this mock
     * replaces the real bean - AuthenticatedRequestHelper.managerTokenFor stubs it per-guild to
     * simulate "this test manager currently holds the configured role in this guild".
     */
    @Bean
    @Primary
    DiscordBotService discordBotService() {
        return Mockito.mock(DiscordBotService.class);
    }
}
