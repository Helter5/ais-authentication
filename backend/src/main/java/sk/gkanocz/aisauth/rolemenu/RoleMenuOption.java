package sk.gkanocz.aisauth.rolemenu;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One button/select entry. Clicking it grants (or removes, per the menu's message type) every
 *  role in {@code roleIds} together as a single bundle - e.g. "2. ROCNIK + API" mapping to two
 *  Discord roles at once. */
public record RoleMenuOption(
        @JsonProperty("role_ids") List<String> roleIds,
        String label,
        String emoji,
        String description) {
}
