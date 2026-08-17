package sk.gkanocz.aisauth;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

/**
 * Boots a real Postgres for tests instead of requiring one to be running locally. Sharing this
 * across @SpringBootTest classes means the whole entity set gets validated against real schema
 * (ddl-auto=validate) on every test run, not just at deploy time.
 *
 * <p>No JwtDecoder override needed here: the app's JwtDecoder (see AuthBeansConfig) is
 * self-contained, keyed off app.jwt.secret with no external issuer to reach, so it works the same
 * in tests as in production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
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
