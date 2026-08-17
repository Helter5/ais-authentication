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
class AutoDeleteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    @Test
    void getEnabledForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/autodelete/enabled")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setEnabledForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(post("/api/autodelete/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"some-other-guild\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/autodelete")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(post("/api/autodelete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"some-other-guild\",\"channel_id\":\"chan-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(patch("/api/autodelete/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"some-other-guild\",\"channel_id\":\"chan-1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(delete("/api/autodelete/999")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateNotFoundWhenIdDoesNotBelongToGuild() throws Exception {
        String guildId = "guild-ad-3";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(patch("/api/autodelete/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createdConfigAppliesDefaultsForOmittedFields() throws Exception {
        String guildId = "guild-ad-1";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/autodelete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel_id").value("chan-1"))
                .andExpect(jsonPath("$.delay_seconds").value(60))
                .andExpect(jsonPath("$.notify_via").value("channel"));
    }

    @Test
    void creatingASecondConfigForTheSameChannelConflicts() throws Exception {
        String guildId = "guild-ad-2";
        String token = auth.managerTokenFor(guildId);
        String body = "{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-dup\"}";

        mockMvc.perform(post("/api/autodelete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/autodelete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Config for this channel already exists"));
    }
}
