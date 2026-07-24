package sk.gkanocz.aisauth.semester;

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
class SemesterControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    @Test
    void savingAMappingWithAnOutOfRangeRocnikIsBadRequest() throws Exception {
        String guildId = "guild-sem-1";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/semester/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"rocnik\":9,\"semester\":1,\"categories\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid rocnik (1-3) or semester (1-6)"));
    }

    @Test
    void savedMappingIsReturnedByGetMappings() throws Exception {
        String guildId = "guild-sem-2";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/semester/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"rocnik\":2,\"semester\":3,"
                                + "\"categories\":[{\"id\":\"cat-1\",\"name\":\"Subjects\"}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/semester/mappings")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.2.3[0].id").value("cat-1"))
                .andExpect(jsonPath("$.2.3[0].name").value("Subjects"));
    }
}
