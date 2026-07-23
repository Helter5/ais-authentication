package sk.gkanocz.aisauth.discordbot;

public record DashboardResponse(ServerInfo server, Settings settings, Synchronization synchronization) {

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

    public record Settings(String nickname, String timezone) {
    }

    public record Synchronization(int intervalDays, String lastSync, String nextSync) {
    }
}
