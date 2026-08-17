package sk.gkanocz.aisauth.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsRepository;
import sk.gkanocz.aisauth.ticket.IncidentTicketRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketTranscriptRetentionJobTest {

    @Mock
    private GuildSettingsRepository guildSettingsRepository;
    @Mock
    private IncidentTicketRepository incidentTicketRepository;

    private TicketTranscriptRetentionJob job;

    @BeforeEach
    void setUp() {
        job = new TicketTranscriptRetentionJob(guildSettingsRepository, incidentTicketRepository);
    }

    @Test
    void doesNothingWhenNoGuildHasRetentionEnabled() {
        when(guildSettingsRepository.findByTicketRetentionEnabledTrue()).thenReturn(List.of());

        job.expireTranscripts();

        verifyNoInteractions(incidentTicketRepository);
    }

    @Test
    void deletesExpiredTicketsUsingEachGuildsRetentionWindow() {
        GuildSettings settings = new GuildSettings("guild-1");
        settings.setTicketRetentionDays(30);
        when(guildSettingsRepository.findByTicketRetentionEnabledTrue()).thenReturn(List.of(settings));

        job.expireTranscripts();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(incidentTicketRepository).deleteByGuildIdAndClosedAtBefore(eq("guild-1"), cutoffCaptor.capture());
        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(30);
        assertThat(Duration.between(cutoffCaptor.getValue(), expectedCutoff).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void sweepsEveryEnabledGuildIndependently() {
        GuildSettings settingsA = new GuildSettings("guild-a");
        settingsA.setTicketRetentionDays(7);
        GuildSettings settingsB = new GuildSettings("guild-b");
        settingsB.setTicketRetentionDays(90);
        when(guildSettingsRepository.findByTicketRetentionEnabledTrue()).thenReturn(List.of(settingsA, settingsB));

        job.expireTranscripts();

        verify(incidentTicketRepository).deleteByGuildIdAndClosedAtBefore(eq("guild-a"), org.mockito.ArgumentMatchers.any());
        verify(incidentTicketRepository).deleteByGuildIdAndClosedAtBefore(eq("guild-b"), org.mockito.ArgumentMatchers.any());
    }
}
