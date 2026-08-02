package sk.gkanocz.aisauth.rolemenu;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoleMenuOption(
        @JsonProperty("role_id") String roleId,
        String label,
        String emoji,
        String description) {
}
