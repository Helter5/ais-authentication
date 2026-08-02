package sk.gkanocz.aisauth.ticket;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TicketController {

    private final IncidentTicketRepository incidentTicketRepository;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final GuildSettingsService guildSettingsService;

    @GetMapping("/tickets")
    public List<TicketSummaryResponse> listTickets(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId,
            @RequestParam(defaultValue = "200") int limit) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.jda().map(jda -> jda.getGuildById(guildId)).orElse(null);
        GuildSettings guildSettings = guildSettingsService.getOrCreate(guildId);
        return incidentTicketRepository
                .findByGuildIdAndTranscriptIsNotNullOrderByClosedAtDesc(guildId, PageRequest.of(0, limit))
                .stream()
                .map(ticket -> TicketSummaryResponse.from(
                        ticket, guild, guildSettings.isTicketRetentionEnabled(), guildSettings.getTicketRetentionDays()))
                .toList();
    }

    @GetMapping("/tickets/{channelId}")
    public TicketTranscriptResponse getTicket(
            @AuthenticationPrincipal Claims claims, @PathVariable String channelId, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        IncidentTicket ticket = incidentTicketRepository.findByChannelIdAndGuildId(channelId, guildId)
                .orElseThrow(() -> TicketNotFoundException.withChannelId(channelId));
        Guild guild = discordBotService.jda().map(jda -> jda.getGuildById(guildId)).orElse(null);
        return TicketTranscriptResponse.from(ticket, guild);
    }
}
