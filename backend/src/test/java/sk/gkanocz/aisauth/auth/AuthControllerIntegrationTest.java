package sk.gkanocz.aisauth.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import sk.gkanocz.aisauth.TestcontainersConfiguration;
import sk.gkanocz.aisauth.support.AuthenticatedRequestHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;
    @Autowired
    private AdminSessionRepository adminSessionRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    void sessionWithoutAnyTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void sessionWithValidTokenReturnsCurrentUser() throws Exception {
        String token = auth.tokenFor("discord-42", false, java.util.List.of("guild-9"));

        mockMvc.perform(get("/api/auth/session").header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("discord-42"))
                .andExpect(jsonPath("$.user.guildIds[0]").value("guild-9"));
    }

    @Test
    void sessionWithTokenWhoseSessionRowWasRevokedIsUnauthorized() throws Exception {
        JwtService.IssuedToken issued = jwtService.issueToken("discord-1", "u", null, false, java.util.List.of());
        // deliberately not saving an AdminSession row for this jti - simulates a revoked/logged-out session

        mockMvc.perform(get("/api/auth/session").header(HttpHeaders.AUTHORIZATION, auth.bearer(issued.token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutActuallyDeletesTheSessionRow() throws Exception {
        JwtService.IssuedToken issued = jwtService.issueToken("discord-7", "u", null, false, java.util.List.of());
        adminSessionRepository.save(new AdminSession(
                issued.jti(), "discord-7", java.time.LocalDateTime.now().plusHours(1)));

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, auth.bearer(issued.token())))
                .andExpect(status().isOk());

        assertThat(adminSessionRepository.findById(issued.jti())).isEmpty();
    }
}
