package sk.gkanocz.aisauth.semester;

import java.util.List;

public record SemesterDefinition(
        String name,
        List<String> categoryIds,
        List<String> semesterRoles,
        List<RoleMapping> roleMappings,
        Boolean everyoneViewChannel,
        String semesterType) {

    public static final String TYPE_WINTER = "WINTER";
    public static final String TYPE_SUMMER = "SUMMER";

    public List<String> categoryIdsOrEmpty() {
        return categoryIds == null ? List.of() : categoryIds;
    }

    public List<String> semesterRolesOrEmpty() {
        return semesterRoles == null ? List.of() : semesterRoles;
    }

    public List<RoleMapping> roleMappingsOrEmpty() {
        return roleMappings == null ? List.of() : roleMappings;
    }

    public boolean isEveryoneViewChannel() {
        return Boolean.TRUE.equals(everyoneViewChannel);
    }

    public record RoleMapping(
            String fromRoleId, List<String> toRoleIds, List<String> conditionRoleIds, Boolean keepFromRole) {

        public List<String> toRoleIdsOrEmpty() {
            return toRoleIds == null ? List.of() : toRoleIds;
        }

        public List<String> conditionRoleIdsOrEmpty() {
            return conditionRoleIds == null ? List.of() : conditionRoleIds;
        }

        public boolean isKeepFromRole() {
            return Boolean.TRUE.equals(keepFromRole);
        }
    }
}
