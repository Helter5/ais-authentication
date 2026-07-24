package sk.gkanocz.aisauth.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import sk.gkanocz.aisauth.TestcontainersConfiguration;
import sk.gkanocz.aisauth.support.AuthenticatedRequestHelper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GuildAccessAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    @Test
    void managerRolesForbiddenForRegularManager() throws Exception {
        String token = auth.managerTokenFor("some-guild");

        mockMvc.perform(get("/api/admin/manager-roles")
                        .param("guildId", "some-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Forbidden: Super Admin access required."));
    }

    @Test
    void allowedGuildsForbiddenForRegularManager() throws Exception {
        String token = auth.managerTokenFor("some-guild");

        mockMvc.perform(get("/api/admin/allowed-guilds")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Forbidden: Super Admin access required."));
    }

    @Test
    void superAdminCanSetAndReadAllowedGuilds() throws Exception {
        String token = auth.superAdminToken();

        mockMvc.perform(post("/api/admin/allowed-guilds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildIds\":[\"111111111111111111\",\"222222222222222222\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.guildIds[0]").value("111111111111111111"))
                .andExpect(jsonPath("$.guildIds[1]").value("222222222222222222"));

        mockMvc.perform(get("/api/admin/allowed-guilds")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guildIds[0]").value("111111111111111111"))
                .andExpect(jsonPath("$.guildIds[1]").value("222222222222222222"));
    }

    @Test
    void settingAllowedGuildsRejectsInvalidDiscordIds() throws Exception {
        String token = auth.superAdminToken();

        mockMvc.perform(post("/api/admin/allowed-guilds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildIds\":[\"not-a-snowflake\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("At least one valid Discord guild ID is required"));
    }
}
