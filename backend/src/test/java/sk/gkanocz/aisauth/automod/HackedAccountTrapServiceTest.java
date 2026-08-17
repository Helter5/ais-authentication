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
                "guild-1", true, "trap-channel", true, true, List.of(),
                false, 3600, false, "dm message", "reason");
    }

    @Test
    void rejectsMissingEnabledState() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withEnabled(validRequest(), null);

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid module state");
    }

    @Test
    void rejectsInvalidDeleteMessageHistoryDuration() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request =
                withDeleteMessageHistory(validRequest(), true, 42);

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid delete message history duration");
    }

    @Test
    void acceptsEveryDiscordDeleteMessageHistoryOption() {
        for (Integer seconds : HackedAccountTrapSettings.DELETE_MESSAGE_HISTORY_SECONDS_OPTIONS) {
            HackedAccountTrapService.HackedAccountTrapSaveRequest request =
                    withDeleteMessageHistory(validRequest(), true, seconds);

            HackedAccountTrapSettings result = hackedAccountTrapService.save(guild, request);

            assertThat(result.deleteMessageHistorySeconds()).isEqualTo(seconds);
        }
    }

    @Test
    void rejectsDmMessageTooLong() {
        HackedAccountTrapService.HackedAccountTrapSaveRequest request = withDmMessage(validRequest(), "x".repeat(2001));

        assertThatThrownBy(() -> hackedAccountTrapService.save(guild, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("too long");
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
    void acceptsValidRequestAndPersistsSettingsPlusSyncsLegacyTrapChannelField() {
        HackedAccountTrapSettings result = hackedAccountTrapService.save(guild, validRequest());

        assertThat(result.enabled()).isTrue();
        assertThat(result.trapChannelId()).isEqualTo("trap-channel");

        verify(adminSettingsService).set(anyString(), any());
        verify(guildSettingsService).updateField("guild-1", "spam_trap_channel_id", "trap-channel");
    }

    @Test
    void deduplicatesExemptRoleIds() {
        when(guild.getRoleById("role-a")).thenReturn(org.mockito.Mockito.mock(Role.class));
        HackedAccountTrapService.HackedAccountTrapSaveRequest request =
                withExemptRoles(validRequest(), List.of("role-a", "role-a", "role-a"));

        HackedAccountTrapSettings result = hackedAccountTrapService.save(guild, request);

        assertThat(result.exemptRoleIds()).containsExactly("role-a");
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withEnabled(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, Boolean enabled) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), enabled, r.trapChannelId(), r.deleteTriggerMessage(), r.ignoreAdministrators(),
                r.exemptRoleIds(), r.deleteMessageHistory(), r.deleteMessageHistorySeconds(), r.dmUser(), r.dmMessage(), r.reason());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withDeleteMessageHistory(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, Boolean deleteMessageHistory, Integer seconds) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.deleteTriggerMessage(), r.ignoreAdministrators(),
                r.exemptRoleIds(), deleteMessageHistory, seconds, r.dmUser(), r.dmMessage(), r.reason());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withDmMessage(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, String dmMessage) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.deleteTriggerMessage(), r.ignoreAdministrators(),
                r.exemptRoleIds(), r.deleteMessageHistory(), r.deleteMessageHistorySeconds(), r.dmUser(), dmMessage, r.reason());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withTrapChannel(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, String trapChannelId) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), trapChannelId, r.deleteTriggerMessage(), r.ignoreAdministrators(),
                r.exemptRoleIds(), r.deleteMessageHistory(), r.deleteMessageHistorySeconds(), r.dmUser(), r.dmMessage(), r.reason());
    }

    private HackedAccountTrapService.HackedAccountTrapSaveRequest withExemptRoles(
            HackedAccountTrapService.HackedAccountTrapSaveRequest r, List<String> exemptRoleIds) {
        return new HackedAccountTrapService.HackedAccountTrapSaveRequest(
                r.guildId(), r.enabled(), r.trapChannelId(), r.deleteTriggerMessage(), r.ignoreAdministrators(),
                exemptRoleIds, r.deleteMessageHistory(), r.deleteMessageHistorySeconds(), r.dmUser(), r.dmMessage(), r.reason());
    }
}
