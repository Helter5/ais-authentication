package sk.gkanocz.aisauth.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TicketController {

    private final IncidentTicketRepository incidentTicketRepository;

    @GetMapping("/tickets/{channelId}")
    public TicketTranscriptResponse getTicket(@PathVariable String channelId, @RequestParam String guildId) {
        IncidentTicket ticket = incidentTicketRepository.findByChannelIdAndGuildId(channelId, guildId)
                .orElseThrow(() -> TicketNotFoundException.withChannelId(channelId));
        return TicketTranscriptResponse.from(ticket);
    }
}
