package sk.gkanocz.aisauth.wipe;

import java.util.List;

public record WipeSettings(boolean removeAllRoles, List<String> keepRoleIds) {

    public static WipeSettings empty() {
        return new WipeSettings(false, List.of());
    }
}
