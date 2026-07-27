package sk.gkanocz.aisauth.automod;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Decodes an AutoDeleteConfig ignore-list column (JSON string) back into a List&lt;String&gt;.
 * Shared by AutoDeleteController (API responses) and AutoDeleteListener (the actual filter check)
 * so both read the same column the same way.
 */
final class IgnoreListJson {

    private IgnoreListJson() {
    }

    static List<String> read(ObjectMapper objectMapper, String json) {
        return objectMapper.readValue(json, new TypeReference<List<String>>() { });
    }
}
