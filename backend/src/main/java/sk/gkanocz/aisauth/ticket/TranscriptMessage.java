package sk.gkanocz.aisauth.ticket;

import tools.jackson.core.type.TypeReference;

import java.util.List;

public record TranscriptMessage(
        String authorId, String authorTag, String timestamp, String content, List<Attachment> attachments) {

    public static final TypeReference<List<TranscriptMessage>> LIST_TYPE = new TypeReference<>() { };

    public record Attachment(String url, String name) {
    }
}
