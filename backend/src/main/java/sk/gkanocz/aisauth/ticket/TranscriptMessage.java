package sk.gkanocz.aisauth.ticket;

import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * eventKind is null for a normal chat message. For the bot's own ticket/automod control and status
 * messages it names which lifecycle event this was (e.g. "ticket_closed", "dm_sent") so the
 * transcript viewer can render those as a compact system-event line instead of a full chat bubble.
 * Classified best-effort by TicketService.buildTranscript() from the bot's known message texts —
 * transcripts saved before this field existed simply deserialize it as null.
 */
public record TranscriptMessage(
        String authorId, String authorTag, String authorAvatarUrl, String timestamp, String content,
        List<Attachment> attachments, String eventKind) {

    public static final TypeReference<List<TranscriptMessage>> LIST_TYPE = new TypeReference<>() { };

    public record Attachment(String url, String name) {
    }
}
