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
class DiscordResourcesControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    @Test
    void rolesForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        assertForbidden("/api/discord/roles");
    }

    @Test
    void channelsForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        assertForbidden("/api/discord/channels");
    }

    @Test
    void categoriesForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        assertForbidden("/api/discord/categories");
    }

    @Test
    void emojisForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        assertForbidden("/api/discord/emojis");
    }

    private void assertForbidden(String path) throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get(path)
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Forbidden: Manager access required for this server."));
    }
}
