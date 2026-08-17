package sk.gkanocz.aisauth.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecapChannelBackfillRunnerTest {

    @Mock
    private AdminSettingRepository adminSettingRepository;
    @Mock
    private LogChannelSubscriptionRepository logChannelSubscriptionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RecapChannelBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new RecapChannelBackfillRunner(adminSettingRepository, logChannelSubscriptionRepository, objectMapper);

        when(adminSettingRepository.findByKeyStartingWith("recap_channel_wipe_")).thenReturn(List.of());
        when(adminSettingRepository.findByKeyStartingWith("recap_channel_semester_")).thenReturn(List.of());
    }

    @Test
    void migratesWipeRecapRowIntoSubscriptionWhenNoneExistsYet() {
        AdminSetting setting = new AdminSetting("recap_channel_wipe_guild-1", "\"channel-9\"");
        when(adminSettingRepository.findByKeyStartingWith("recap_channel_wipe_")).thenReturn(List.of(setting));
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.empty());

        runner.run(null);

        ArgumentCaptor<LogChannelSubscription> captor = ArgumentCaptor.forClass(LogChannelSubscription.class);
        verify(logChannelSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getGuildId()).isEqualTo("guild-1");
        assertThat(captor.getValue().getChannelId()).isEqualTo("channel-9");
        assertThat(captor.getValue().getEventType()).isEqualTo(LogEventType.WIPE_RECAP);
        verify(adminSettingRepository).delete(setting);
    }

    @Test
    void migratesSemesterRecapRowUsingCorrectEventType() {
        AdminSetting setting = new AdminSetting("recap_channel_semester_guild-2", "\"channel-5\"");
        when(adminSettingRepository.findByKeyStartingWith("recap_channel_semester_")).thenReturn(List.of(setting));
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-2", LogEventType.SEMESTER_RECAP))
                .thenReturn(Optional.empty());

        runner.run(null);

        ArgumentCaptor<LogChannelSubscription> captor = ArgumentCaptor.forClass(LogChannelSubscription.class);
        verify(logChannelSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getGuildId()).isEqualTo("guild-2");
        assertThat(captor.getValue().getEventType()).isEqualTo(LogEventType.SEMESTER_RECAP);
        verify(adminSettingRepository).delete(setting);
    }

    @Test
    void skipsSaveButStillDeletesWhenSubscriptionAlreadyExists() {
        AdminSetting setting = new AdminSetting("recap_channel_wipe_guild-1", "\"channel-9\"");
        when(adminSettingRepository.findByKeyStartingWith("recap_channel_wipe_")).thenReturn(List.of(setting));
        LogChannelSubscription existing = new LogChannelSubscription("guild-1", "channel-old", LogEventType.WIPE_RECAP);
        when(logChannelSubscriptionRepository.findByGuildIdAndEventType("guild-1", LogEventType.WIPE_RECAP))
                .thenReturn(Optional.of(existing));

        runner.run(null);

        verify(logChannelSubscriptionRepository, never()).save(any());
        verify(adminSettingRepository).delete(setting);
    }

    @Test
    void deletesRowWithoutSavingWhenStoredChannelIdIsNull() {
        AdminSetting setting = new AdminSetting("recap_channel_wipe_guild-1", "null");
        when(adminSettingRepository.findByKeyStartingWith("recap_channel_wipe_")).thenReturn(List.of(setting));

        runner.run(null);

        verify(logChannelSubscriptionRepository, never()).save(any());
        verify(adminSettingRepository).delete(setting);
    }

    @Test
    void deletesRowWithoutSavingWhenValueFailsToParse() {
        AdminSetting setting = new AdminSetting("recap_channel_wipe_guild-1", "{not-json");
        when(adminSettingRepository.findByKeyStartingWith("recap_channel_wipe_")).thenReturn(List.of(setting));

        runner.run(null);

        verify(logChannelSubscriptionRepository, never()).save(any());
        verify(adminSettingRepository).delete(setting);
    }
}
