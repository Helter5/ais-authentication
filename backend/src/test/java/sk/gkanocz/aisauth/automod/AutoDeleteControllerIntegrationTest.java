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
                .andExpect(jsonPath("$.ignore_bots").value(true))
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
