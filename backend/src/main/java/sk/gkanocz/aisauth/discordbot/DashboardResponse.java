package sk.gkanocz.aisauth.discordbot;

import sk.gkanocz.aisauth.verification.RemovedVerificationEntry;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        ServerInfo server,
        Synchronization synchronization,
        VerificationConfig verificationConfig,
        List<ActivityEntry> recentActivity) {

    public record ServerInfo(
            String id,
            String name,
            String icon,
            int memberCount,
            int categoryCount,
            int textChannelCount,
            int voiceChannelCount,
            int roleCount) {
    }

    public record Synchronization(
            String lastSyncAt, Integer checkedCount, Integer removedCount, List<RemovedVerificationEntry> removedUsers) {
    }

    public record VerificationConfig(
            List<String> allowedFaculties,
            String requiredAccountStatus,
            String verifiedRoleId,
            String verifiedRoleName,
            String inactiveRoleId,
            String inactiveRoleName,
            int verifiedCount) {
    }

    public record ActivityEntry(String category, String action, String username, LocalDateTime createdAt) {
    }
}
