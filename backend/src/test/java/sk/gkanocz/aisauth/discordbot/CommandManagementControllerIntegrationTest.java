package sk.gkanocz.aisauth.discordbot;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommandManagementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    @Test
    void commandStatesForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/command-states")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanSetAndReadCommandStateForTheirOwnGuild() throws Exception {
        String guildId = "guild-cmd-states-1";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(patch("/api/command-states")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"command\":\"verify\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/command-states")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verify").value(false));
    }

    @Test
    void managerCanSaveAndReadCommandPermissionsForTheirOwnGuild() throws Exception {
        String guildId = "guild-cmd-perms-1";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/command-permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"command\":\"warn\","
                                + "\"allowedRoles\":[\"role-1\"],\"adminOnly\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/command-permissions")
                        .param("guildId", guildId)
                        .param("command", "warn")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedRoles[0]").value("role-1"))
                .andExpect(jsonPath("$.adminOnly").value(true));

        mockMvc.perform(get("/api/command-permissions/summary")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].command").value("warn"))
                .andExpect(jsonPath("$[0].adminOnly").value(true));
    }

    @Test
    void managerCanSaveAndReadCommandSettingsForTheirOwnGuild() throws Exception {
        String guildId = "guild-cmd-settings-1";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/command-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"command\":\"autodelete\",\"delaySeconds\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/command-settings")
                        .param("guildId", guildId)
                        .param("command", "autodelete")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delaySeconds").value(30));
    }

    @Test
    void deployCommandsForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(post("/api/deploy-commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"some-other-guild\"}"))
                .andExpect(status().isForbidden());
    }
}
