package sk.gkanocz.aisauth.ticket;

import net.dv8tion.jda.api.entities.Guild;
import sk.gkanocz.aisauth.discordbot.DiscordNames;

import java.time.LocalDateTime;

public record TicketSummaryResponse(
        String channelId,
        String guildId,
        String userId,
        String username,
        String status,
        String closedBy,
        String closedByUsername,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime expiresAt) {

    static TicketSummaryResponse from(IncidentTicket ticket, Guild guild, boolean retentionEnabled, int retentionDays) {
        LocalDateTime expiresAt = retentionEnabled && ticket.getClosedAt() != null
                ? ticket.getClosedAt().plusDays(retentionDays) : null;
        return new TicketSummaryResponse(
                ticket.getChannelId(), ticket.getGuildId(), ticket.getUserId(), DiscordNames.memberName(guild, ticket.getUserId()),
                ticket.getStatus(), ticket.getClosedBy(), DiscordNames.memberName(guild, ticket.getClosedBy()),
                ticket.getClosedAt(), ticket.getCreatedAt(), expiresAt);
    }
}
