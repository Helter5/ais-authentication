package sk.gkanocz.aisauth.ticket;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;

public record TicketTranscriptResponse(
        String channelId,
        String guildId,
        String userId,
        String status,
        String closedBy,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        @JsonRawValue String messages) {

    static TicketTranscriptResponse from(IncidentTicket ticket) {
        String messages = ticket.getTranscript() != null ? ticket.getTranscript() : "[]";
        return new TicketTranscriptResponse(
                ticket.getChannelId(), ticket.getGuildId(), ticket.getUserId(),
                ticket.getStatus(), ticket.getClosedBy(), ticket.getClosedAt(),
                ticket.getCreatedAt(), messages);
    }
}
