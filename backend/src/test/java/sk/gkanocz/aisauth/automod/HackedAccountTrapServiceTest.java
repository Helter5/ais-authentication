package sk.gkanocz.aisauth.automod;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HackedAccountTrapServiceTest {

    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private GuildSettingsService guildSettingsService;
    @Mock
    private Guild guild;
    @Mock
    private TextChannel trapChannel;

    private HackedAccountTrapService hackedAccountTrapService;

    @BeforeEach
    void setUp() {
        hackedAccountTrapService = new HackedAccountTrapService(
                adminSettingsService, guildSettingsService, new tools.jackson.databind.json.JsonMapper());
        lenient().when(guild.getId()).thenReturn("guild-1");
        lenient().when(guild.getTextChannelById("trap-channel")).thenReturn(trapChannel);
        lenient().when(guildSettingsService.getOrCreate("guild-1")).thenReturn(new GuildSettings("guild-1"));
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest validRequest() {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                "guild-1", true, "trap-channel", "timeout", 60,
                true, true, 60, List.of(), true,
                false, "dm message", "reason",
                false, null, null, "hacked-{user}", false,
                "incident message", false, false, List.of());
    }

    @Test
    void rejectsInvalidAction() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withAction(validRequest(), "dance");

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid module state or action");
    }

    @Test
    void rejectsTimeoutMinutesOutOfRange() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withTimeoutMinutes(validRequest(), 0);

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Timeout must be between");
    }

    @Test
    void rejectsCleanupMinutesOutOfRange() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withCleanupMinutes(validRequest(), 2000);

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cleanup period must be between");
    }

    @Test
    void rejectsDmMessageTooLong() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withDmMessage(validRequest(), "x".repeat(2001));

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("too long");
    }

    @Test
    void rejectsBlankIncidentChannelNameTemplate() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withNameTemplate(validRequest(), "   ");

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Incident channel name");
    }

    @Test
    void rejectsUnknownTrapChannel() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withTrapChannel(validRequest(), "does-not-exist");

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid trap channel");
    }

    @Test
    void rejectsEnablingModuleWithoutATrapChannel() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withTrapChannel(validRequest(), null);

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Choose a trap channel");
    }

    @Test
    void rejectsUnknownExemptRole() {
        when(guild.getRoleById("bad-role")).thenReturn(null);
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withExemptRoles(validRequest(), List.of("bad-role"));

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("exempt roles are invalid");
    }

    @Test
    void rejectsUnknownIncidentCategory() {
        when(guild.getCategoryById("bad-category")).thenReturn(null);
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withIncidentCategory(validRequest(), "bad-category");

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid incident channel category");
    }

    @Test
    void acceptsValidRequestAndPersistsSettingsPlusSyncsLegacyGuildSettingsFields() {
        HackedAccountTrapSettings result = hackedAccountTrapService.save(guild, validRequest());

        assertThat(result.enabled()).isTrue();
        assertThat(result.trapChannelId()).isEqualTo("trap-channel");
        assertThat(result.action()).isEqualTo("timeout");

        verify(adminSettingsService).set(anyString(), any());
        verify(guildSettingsService).updateField("guild-1", "spam_trap_channel_id", "trap-channel");
        verify(guildSettingsService).updateField("guild-1", "spam_delete_interval", 60);
    }

    @Test
    void deduplicatesExemptRoleIds() {
        when(guild.getRoleById("role-a")).thenReturn(org.mockito.Mockito.mock(Role.class));
        HackedAccountTrapService.HackedAccountTrapSaveRequest request =
                withExemptRoles(validRequest(), List.of("role-a", "role-a", "role-a"));

        HackedAccountTrapSettings result = hackedAccountTrapService.save(guild, request);

        assertThat(result.exemptRoleIds()).containsExactly("role-a");
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withAction(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, String action) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), action, r.timeoutMinutes(),
                r.deleteTriggerMessage(), r.deleteRecentMessages(), r.cleanupMinutes(), r.exemptRoleIds(), r.ignoreAdministrators(),
                r.dmUser(), r.dmMessage(), r.reason(), r.incidentChannelEnabled(), r.incidentChannelCategoryId(),
                r.incidentChannelClosedCategoryId(), r.incidentChannelNameTemplate(), r.incidentChannelIncludeUser(),
                r.incidentChannelMessage(), r.incidentChannelPostDmStatus(), r.incidentChannelTagRoles(), r.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withTimeoutMinutes(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, Integer timeoutMinutes) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.action(), timeoutMinutes,
                r.deleteTriggerMessage(), r.deleteRecentMessages(), r.cleanupMinutes(), r.exemptRoleIds(), r.ignoreAdministrators(),
                r.dmUser(), r.dmMessage(), r.reason(), r.incidentChannelEnabled(), r.incidentChannelCategoryId(),
                r.incidentChannelClosedCategoryId(), r.incidentChannelNameTemplate(), r.incidentChannelIncludeUser(),
                r.incidentChannelMessage(), r.incidentChannelPostDmStatus(), r.incidentChannelTagRoles(), r.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withCleanupMinutes(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, Integer cleanupMinutes) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.action(), r.timeoutMinutes(),
                r.deleteTriggerMessage(), r.deleteRecentMessages(), cleanupMinutes, r.exemptRoleIds(), r.ignoreAdministrators(),
                r.dmUser(), r.dmMessage(), r.reason(), r.incidentChannelEnabled(), r.incidentChannelCategoryId(),
                r.incidentChannelClosedCategoryId(), r.incidentChannelNameTemplate(), r.incidentChannelIncludeUser(),
                r.incidentChannelMessage(), r.incidentChannelPostDmStatus(), r.incidentChannelTagRoles(), r.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withDmMessage(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, String dmMessage) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.action(), r.timeoutMinutes(),
                r.deleteTriggerMessage(), r.deleteRecentMessages(), r.cleanupMinutes(), r.exemptRoleIds(), r.ignoreAdministrators(),
                r.dmUser(), dmMessage, r.reason(), r.incidentChannelEnabled(), r.incidentChannelCategoryId(),
                r.incidentChannelClosedCategoryId(), r.incidentChannelNameTemplate(), r.incidentChannelIncludeUser(),
                r.incidentChannelMessage(), r.incidentChannelPostDmStatus(), r.incidentChannelTagRoles(), r.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withNameTemplate(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, String nameTemplate) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.action(), r.timeoutMinutes(),
                r.deleteTriggerMessage(), r.deleteRecentMessages(), r.cleanupMinutes(), r.exemptRoleIds(), r.ignoreAdministrators(),
                r.dmUser(), r.dmMessage(), r.reason(), r.incidentChannelEnabled(), r.incidentChannelCategoryId(),
                r.incidentChannelClosedCategoryId(), nameTemplate, r.incidentChannelIncludeUser(),
                r.incidentChannelMessage(), r.incidentChannelPostDmStatus(), r.incidentChannelTagRoles(), r.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withTrapChannel(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, String trapChannelId) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), trapChannelId, r.action(), r.timeoutMinutes(),
                r.deleteTriggerMessage(), r.deleteRecentMessages(), r.cleanupMinutes(), r.exemptRoleIds(), r.ignoreAdministrators(),
                r.dmUser(), r.dmMessage(), r.reason(), r.incidentChannelEnabled(), r.incidentChannelCategoryId(),
                r.incidentChannelClosedCategoryId(), r.incidentChannelNameTemplate(), r.incidentChannelIncludeUser(),
                r.incidentChannelMessage(), r.incidentChannelPostDmStatus(), r.incidentChannelTagRoles(), r.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withExemptRoles(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, List<String> exemptRoleIds) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.action(), r.timeoutMinutes(),
                r.deleteTriggerMessage(), r.deleteRecentMessages(), r.cleanupMinutes(), exemptRoleIds, r.ignoreAdministrators(),
                r.dmUser(), r.dmMessage(), r.reason(), r.incidentChannelEnabled(), r.incidentChannelCategoryId(),
                r.incidentChannelClosedCategoryId(), r.incidentChannelNameTemplate(), r.incidentChannelIncludeUser(),
                r.incidentChannelMessage(), r.incidentChannelPostDmStatus(), r.incidentChannelTagRoles(), r.incidentChannelTagRoleIds());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withIncidentCategory(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, String categoryId) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.action(), r.timeoutMinutes(),
                r.deleteTriggerMessage(), r.deleteRecentMessages(), r.cleanupMinutes(), r.exemptRoleIds(), r.ignoreAdministrators(),
                r.dmUser(), r.dmMessage(), r.reason(), r.incidentChannelEnabled(), categoryId,
                r.incidentChannelClosedCategoryId(), r.incidentChannelNameTemplate(), r.incidentChannelIncludeUser(),
                r.incidentChannelMessage(), r.incidentChannelPostDmStatus(), r.incidentChannelTagRoles(), r.incidentChannelTagRoleIds());
    }
}
