package sk.gkanocz.aisauth.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuildSettingsServiceTest {

    @Mock
    private GuildSettingsRepository guildSettingsRepository;

    private GuildSettingsService guildSettingsService;

    @BeforeEach
    void setUp() {
        guildSettingsService = new GuildSettingsService(guildSettingsRepository);
    }

    @Test
    void getOrCreateReturnsExistingSettingsWithoutSaving() {
        GuildSettings existing = new GuildSettings("guild-1");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(existing));

        GuildSettings result = guildSettingsService.getOrCreate("guild-1");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getOrCreateSavesNewSettingsWhenMissing() {
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.empty());
        when(guildSettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GuildSettings result = guildSettingsService.getOrCreate("guild-1");

        assertThat(result.getGuildId()).isEqualTo("guild-1");
        verify(guildSettingsRepository).save(any());
    }

    @Test
    void updateFieldSetsVerifiedRoleId() {
        GuildSettings settings = new GuildSettings("guild-1");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(settings));

        guildSettingsService.updateField("guild-1", "verified_role_id", "role-123");

        assertThat(settings.getVerifiedRoleId()).isEqualTo("role-123");
    }

    @Test
    void updateFieldSetsSpamDeleteIntervalFromNumber() {
        GuildSettings settings = new GuildSettings("guild-1");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(settings));

        guildSettingsService.updateField("guild-1", "spam_delete_interval", 90);

        assertThat(settings.getSpamDeleteInterval()).isEqualTo(90);
    }

    @Test
    void updateFieldSetsVerificationEnabledFromBoolean() {
        GuildSettings settings = new GuildSettings("guild-1");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(settings));

        guildSettingsService.updateField("guild-1", "verification_enabled", false);

        assertThat(settings.isVerificationEnabled()).isFalse();
    }

    @Test
    void updateFieldRejectsUnknownField() {
        GuildSettings settings = new GuildSettings("guild-1");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> guildSettingsService.updateField("guild-1", "not_a_real_field", "x"))
                .isInstanceOf(UnknownSettingFieldException.class);
    }

    @Test
    void updateFieldSetsTimezone() {
        GuildSettings settings = new GuildSettings("guild-1");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(settings));

        guildSettingsService.updateField("guild-1", "timezone", "Europe/Bratislava");

        assertThat(settings.getTimezone()).isEqualTo("Europe/Bratislava");
    }

    @Test
    void updateFieldDefaultsBlankTimezoneToUtc() {
        GuildSettings settings = new GuildSettings("guild-1");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(settings));

        guildSettingsService.updateField("guild-1", "timezone", "");

        assertThat(settings.getTimezone()).isEqualTo("UTC");
    }

    @Test
    void updateFieldRejectsInvalidTimezone() {
        GuildSettings settings = new GuildSettings("guild-1");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> guildSettingsService.updateField("guild-1", "timezone", "Not/AZone"))
                .isInstanceOf(sk.gkanocz.aisauth.shared.InvalidRequestException.class);
    }

    @Test
    void updateFieldAcceptsNullToClearAChannelId() {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setSpamTrapChannelId("some-channel");
        when(guildSettingsRepository.findById("guild-1")).thenReturn(Optional.of(settings));

        guildSettingsService.updateField("guild-1", "spam_trap_channel_id", null);

        assertThat(settings.getSpamTrapChannelId()).isNull();
    }
}
