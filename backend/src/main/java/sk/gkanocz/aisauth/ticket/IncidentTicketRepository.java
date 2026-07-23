package sk.gkanocz.aisauth.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentTicketRepository extends JpaRepository<IncidentTicket, String> {

    Optional<IncidentTicket> findByChannelIdAndGuildId(String channelId, String guildId);
}
