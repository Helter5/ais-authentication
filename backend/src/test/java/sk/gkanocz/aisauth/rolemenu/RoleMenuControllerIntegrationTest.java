package sk.gkanocz.aisauth.rolemenu;

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

/**
 * Covers permission boundaries, request validation, and not-found handling - all of which resolve
 * before RoleMenuController ever calls DiscordBotService.requireGuild(...). A successful create/
 * update reaches RoleMenuService.postOrUpdateMenu's real JDA channel-post/edit action chain, which
 * RoleMenuServiceTest already covers directly with mocks; duplicating that through MockMvc here
 * would just re-mock the same JDA chain for no additional coverage value.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleMenuControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticatedRequestHelper auth;

    private String validConfigBody(String guildId) {
        return "{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\",\"title\":\"Pick a role\","
                + "\"ui_type\":\"BUTTONS\",\"selection_mode\":\"SINGLE\","
                + "\"options\":[{\"roleId\":\"role-1\",\"label\":\"One\"}]}";
    }

    // ---- permission boundaries ----

    @Test
    void getEnabledForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/rolemenu/enabled")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setEnabledForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(post("/api/rolemenu/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"some-other-guild\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(get("/api/rolemenu")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(post("/api/rolemenu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content(validConfigBody("some-other-guild")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(patch("/api/rolemenu/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content(validConfigBody("some-other-guild")))
                .andExpect(status().isForbidden());
    }

    @Test
    void repostForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(post("/api/rolemenu/999/repost")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteForbiddenWhenManagerTokenIsForADifferentGuild() throws Exception {
        String token = auth.managerTokenFor("guild-owned-by-manager");

        mockMvc.perform(delete("/api/rolemenu/999")
                        .param("guildId", "some-other-guild")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ---- validation ----

    @Test
    void createRejectsMissingChannel() throws Exception {
        String guildId = "guild-rm-1";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/rolemenu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"title\":\"Pick a role\","
                                + "\"ui_type\":\"BUTTONS\",\"selection_mode\":\"SINGLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a channel for the role menu."));
    }

    @Test
    void createRejectsMissingTitle() throws Exception {
        String guildId = "guild-rm-2";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/rolemenu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\","
                                + "\"ui_type\":\"BUTTONS\",\"selection_mode\":\"SINGLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Give the role menu a title."));
    }

    @Test
    void createRejectsInvalidUiType() throws Exception {
        String guildId = "guild-rm-3";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/rolemenu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\",\"title\":\"Pick a role\","
                                + "\"ui_type\":\"DROPDOWN\",\"selection_mode\":\"SINGLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("uiType must be BUTTONS or SELECT_MENU."));
    }

    @Test
    void createRejectsInvalidSelectionMode() throws Exception {
        String guildId = "guild-rm-4";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/rolemenu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\",\"title\":\"Pick a role\","
                                + "\"ui_type\":\"BUTTONS\",\"selection_mode\":\"ANY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("selectionMode must be SINGLE or MULTI."));
    }

    @Test
    void createRejectsMaxSelectableBelowOne() throws Exception {
        String guildId = "guild-rm-5";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/rolemenu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content("{\"guildId\":\"" + guildId + "\",\"channel_id\":\"chan-1\",\"title\":\"Pick a role\","
                                + "\"ui_type\":\"BUTTONS\",\"selection_mode\":\"MULTI\",\"max_selectable\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Max selectable roles must be at least 1."));
    }

    // ---- not found ----

    @Test
    void updateNotFoundWhenIdDoesNotBelongToGuild() throws Exception {
        String guildId = "guild-rm-6";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(patch("/api/rolemenu/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token))
                        .content(validConfigBody(guildId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void repostNotFoundWhenIdDoesNotBelongToGuild() throws Exception {
        String guildId = "guild-rm-7";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(post("/api/rolemenu/999999/repost")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteNotFoundWhenIdDoesNotBelongToGuild() throws Exception {
        String guildId = "guild-rm-8";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(delete("/api/rolemenu/999999")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isNotFound());
    }

    // ---- list ----

    @Test
    void listReturnsEmptyForAGuildWithNoConfigs() throws Exception {
        String guildId = "guild-rm-9";
        String token = auth.managerTokenFor(guildId);

        mockMvc.perform(get("/api/rolemenu")
                        .param("guildId", guildId)
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
