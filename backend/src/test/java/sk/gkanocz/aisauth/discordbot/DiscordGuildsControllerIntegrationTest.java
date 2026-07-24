package sk.gkanocz.aisauth.discordbot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import sk.gkanocz.aisauth.TestcontainersConfiguration;
import sk.gkanocz.aisauth.support.AuthenticatedRequestHelper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DiscordGuildsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    @Test
    void returnsEmptyListWhenBotIsNotConnected() throws Exception {
        String token = auth.managerTokenFor("guild-1");

        mockMvc.perform(get("/api/discord/guilds").header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
