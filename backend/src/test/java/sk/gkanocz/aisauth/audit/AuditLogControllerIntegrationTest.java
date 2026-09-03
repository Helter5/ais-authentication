package sk.gkanocz.aisauth.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import sk.gkanocz.aisauth.TestcontainersConfiguration;
import sk.gkanocz.aisauth.support.AuthenticatedRequestHelper;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;
    @Autowired
    private AuditLogService auditLogService;

    @Test
    void getAuditLogsForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("category", "dashboard")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Forbidden: Manager access required for this server."));
    }

    @Test
    void managerCanListAuditLogsForTheirOwnGuild() throws Exception {
        String guildId = "guild-audit-log-1";
        auditLogService.log(new AuditLogEntry(
                "dashboard", "Updated setting", guildId, "My Guild",
                null, null, "user-1", "someone", Map.of("field", "value")));
        String token = auth.managerTokenFor(guildId);

        // AuditLogService.log persists on a background thread now, so poll until it lands.
        await().atMost(Duration.ofSeconds(5)).ignoreExceptions().untilAsserted(() ->
                mockMvc.perform(get("/api/admin/audit-logs")
                                .param("category", "dashboard")
                                .param("guildId", guildId)
                                .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].action").value("Updated setting"))
                        .andExpect(jsonPath("$[0].details.field").value("value")));
    }
}
