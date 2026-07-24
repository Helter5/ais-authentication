package sk.gkanocz.aisauth.warn;

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
class WarnAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;
    @Autowired
    private WarnRepository warnRepository;

    @Test
    void getWarningsForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/admin/warnings")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Forbidden: Manager access required for this server."));
    }

    @Test
    void managerCanListWarningsForTheirOwnGuild() throws Exception {
        String guildId = "guild-warn-admin-1";
        warnRepository.save(new Warn(guildId, "user-1", "mod-1", "spamming"));
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(get("/api/admin/warnings")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].discord_id").value("user-1"))
                .andExpect(jsonPath("$[0].moderator_id").value("mod-1"))
                .andExpect(jsonPath("$[0].reason").value("spamming"));
    }
}
