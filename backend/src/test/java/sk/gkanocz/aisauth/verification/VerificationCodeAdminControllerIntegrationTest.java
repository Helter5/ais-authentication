package sk.gkanocz.aisauth.verification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import sk.gkanocz.aisauth.TestcontainersConfiguration;
import sk.gkanocz.aisauth.support.AuthenticatedRequestHelper;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VerificationCodeAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;
    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Test
    void getVerificationCodesForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/verifications")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Forbidden: Manager access required for this server."));
    }

    @Test
    void managerCanListActiveVerificationCodesForTheirOwnGuild() throws Exception {
        String guildId = "guild-pending-verify-1";
        verificationCodeRepository.save(new VerificationCode(
                "discord-1", guildId, "123456", "user@stuba.sk", "ais123", LocalDateTime.now().plusMinutes(10)));
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(get("/api/verifications")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].discord_id").value("discord-1"))
                .andExpect(jsonPath("$[0].ais_id").value("ais123"));
    }

    @Test
    void managerCanAlsoSeeExpiredVerificationCodesForTheirOwnGuild() throws Exception {
        String guildId = "guild-pending-verify-2";
        verificationCodeRepository.save(new VerificationCode(
                "discord-2", guildId, "654321", "expired@stuba.sk", "ais456", LocalDateTime.now().minusMinutes(10)));
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(get("/api/verifications")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].discord_id").value("discord-2"))
                .andExpect(jsonPath("$[0].ais_id").value("ais456"));
    }
}
