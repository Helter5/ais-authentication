package sk.gkanocz.aisauth.ticket;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTranscriptResponseTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    void serializesMessagesAsRawJsonArrayNotAsEscapedString() {
        String rawTranscript = "[{\"authorId\":\"3\",\"authorTag\":\"tester\",\"content\":\"hi\",\"attachments\":[]}]";
        TicketTranscriptResponse response = new TicketTranscriptResponse(
                "1", "2", "3", "tester", "closed", "4", "closer",
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 9, 0),
                rawTranscript);

        String json = jsonMapper.writeValueAsString(response);

        assertThat(json).contains("\"messages\":[{\"authorId\":\"3\"");
        assertThat(json).doesNotContain("\"messages\":\"[");
    }
}
