package sk.gkanocz.aisauth.admin;

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
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    @Test
    void accessReflectsSuperAdminFlagForSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/access").header(HttpHeaders.AUTHORIZATION, auth.bearer(auth.superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void accessReflectsSuperAdminFlagForRegularManager() throws Exception {
        String token = auth.managerTokenFor("some-guild");

        mockMvc.perform(get("/api/admin/access").header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void statusIsForbiddenForRegularManager() throws Exception {
        String token = auth.managerTokenFor("some-guild");

        mockMvc.perform(get("/api/admin/status").header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusIsOkForSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/status").header(HttpHeaders.AUTHORIZATION, auth.bearer(auth.superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeVersion").exists());
    }
}
