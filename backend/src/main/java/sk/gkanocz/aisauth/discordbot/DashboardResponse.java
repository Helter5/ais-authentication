package sk.gkanocz.aisauth.discordbot;

public record DashboardResponse(ServerInfo server, Synchronization synchronization) {

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

    public record Synchronization(int intervalDays, String lastSync, String nextSync) {
    }
}
