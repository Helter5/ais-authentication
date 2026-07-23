package sk.gkanocz.aisauth.settings;

import java.util.List;

public record DashboardSettings(List<String> managerRoleIds) {

    public static DashboardSettings empty() {
        return new DashboardSettings(List.of());
    }
}
