package sk.gkanocz.aisauth.ticket;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IncidentTicketRepository extends JpaRepository<IncidentTicket, String> {

    Optional<IncidentTicket> findByChannelIdAndGuildId(String channelId, String guildId);

    List<IncidentTicket> findByGuildIdAndTranscriptIsNotNullOrderByClosedAtDesc(String guildId, Pageable pageable);

    /**
     * Deletes tickets closed before the cutoff outright - see TicketTranscriptRetentionJob. Open
     * tickets are never matched since closedAt is only set on close, never on creation.
     */
    void deleteByGuildIdAndClosedAtBefore(String guildId, LocalDateTime cutoff);
}
