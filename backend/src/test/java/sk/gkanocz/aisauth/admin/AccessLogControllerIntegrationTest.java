package sk.gkanocz.aisauth.admin;

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
class AccessLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;
    @Autowired
    private AccessLogRepository accessLogRepository;

    @Test
    void getAccessLogsForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/access-logs")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Forbidden: Manager access required for this server."));
    }

    @Test
    void managerCanListAccessLogsForTheirOwnGuild() throws Exception {
        String guildId = "guild-access-log-1";
        accessLogRepository.save(new AccessLog("discord-1", "someone", "login", guildId, "127.0.0.1"));
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(get("/api/access-logs")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].discord_id").value("discord-1"))
                .andExpect(jsonPath("$[0].username").value("someone"))
                .andExpect(jsonPath("$[0].action").value("login"));
    }
}
