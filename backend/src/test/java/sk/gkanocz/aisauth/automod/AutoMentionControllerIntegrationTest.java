package sk.gkanocz.aisauth.automod;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AutoMentionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    @Test
    void getEnabledForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/auto-mentions/enabled")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setEnabledForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(post("/api/auto-mentions/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"some-other-guild\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/auto-mentions")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(post("/api/auto-mentions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"some-other-guild\",\"channel_id\":\"chan-1\",\"role_id\":\"role-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(patch("/api/auto-mentions/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"some-other-guild\",\"channel_id\":\"chan-1\",\"role_id\":\"role-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(delete("/api/auto-mentions/999")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createdAutoMentionAppliesDefaultsForOmittedFields() throws Exception {
        String guildId = "guild-am-1";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/auto-mentions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\",\"role_id\":\"role-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel_id").value("chan-1"))
                .andExpect(jsonPath("$.role_id").value("role-1"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.delete_after_seconds").doesNotExist());
    }

    @Test
    void creatingASecondAutoMentionForTheSameChannelConflicts() throws Exception {
        String guildId = "guild-am-2";
        String token = auth.managerTokenFor(guildId);
        String body = "{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-dup\",\"role_id\":\"role-1\"}";

        mockMvc.perform(post("/api/auto-mentions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auto-mentions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Auto-mention for this channel already exists"));
    }

    @Test
    void updateNotFoundWhenIdDoesNotBelongToGuild() throws Exception {
        String guildId = "guild-am-3";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(patch("/api/auto-mentions/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\",\"role_id\":\"role-1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAutoMentionSucceedsForOwningGuild() throws Exception {
        String guildId = "guild-am-4";
        String token = auth.managerTokenFor(guildId);

        String response = mockMvc.perform(post("/api/auto-mentions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\",\"role_id\":\"role-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = new tools.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/auto-mentions/" + id)
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/auto-mentions")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
