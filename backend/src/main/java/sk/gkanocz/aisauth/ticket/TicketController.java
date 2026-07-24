package sk.gkanocz.aisauth.ticket;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TicketController {

    private final IncidentTicketRepository incidentTicketRepository;
    private final GuildAccessService guildAccessService;

    @GetMapping("/tickets/{channelId}")
    public TicketTranscriptResponse getTicket(
            @AuthenticationPrincipal Claims claims, @PathVariable String channelId, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        IncidentTicket ticket = incidentTicketRepository.findByChannelIdAndGuildId(channelId, guildId)
                .orElseThrow(() -> TicketNotFoundException.withChannelId(channelId));
        return TicketTranscriptResponse.from(ticket);
    }
}
