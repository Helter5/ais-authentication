package sk.gkanocz.aisauth.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsRepository;
import sk.gkanocz.aisauth.ticket.IncidentTicketRepository;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Per-guild dashboard-configurable ticket transcript retention (Settings -> Ticket Transcripts):
 * once a day, for every guild with retention enabled, deletes tickets closed longer ago than that
 * guild's configured retention window outright - there's no ongoing value in keeping an inert
 * ticket row around once its transcript has aged out, so once it's expired it's just gone rather
 * than lingering with its content cleared.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketTranscriptRetentionJob {

    private final GuildSettingsRepository guildSettingsRepository;
    private final IncidentTicketRepository incidentTicketRepository;

    @Scheduled(fixedRate = 24, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void expireTranscripts() {
        for (GuildSettings settings : guildSettingsRepository.findByTicketRetentionEnabledTrue()) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(settings.getTicketRetentionDays());
            incidentTicketRepository.deleteByGuildIdAndClosedAtBefore(settings.getGuildId(), cutoff);
        }
        log.debug("Ticket transcript retention sweep complete");
    }
}
