package sk.gkanocz.aisauth.audit;

import java.util.Map;

public record AuditLogEntry(
        String category,
        String action,
        String guildId,
        String guildName,
        String channelId,
        String channelName,
        String userId,
        String username,
        Map<String, Object> details) {
}
