package sk.gkanocz.aisauth.automod;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IgnoreListJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsAnEmptyJsonArrayAsAnEmptyList() {
        assertThat(IgnoreListJson.read(objectMapper, "[]")).isEmpty();
    }

    @Test
    void readsAJsonArrayOfIdsIntoAList() {
        assertThat(IgnoreListJson.read(objectMapper, "[\"id-1\",\"id-2\"]")).containsExactly("id-1", "id-2");
    }
}
