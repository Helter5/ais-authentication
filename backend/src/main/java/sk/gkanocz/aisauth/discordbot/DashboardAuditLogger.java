package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;

import java.util.Map;

/**
 * Records a "dashboard" category audit-log entry for an admin action. Shared by every dashboard
 * controller/service that previously reimplemented the same try/catch-and-swallow wrapper around
 * AuditLogService, so the intentional best-effort behavior (never block the underlying change on
 * a logging failure) lives in exactly one place.
 */
@Component
@RequiredArgsConstructor
public class DashboardAuditLogger {

    private final AuditLogService auditLogService;
    private final DiscordBotService discordBotService;

    public void log(Claims claims, String guildId, String action, Map<String, Object> details) {
        log(claims.getSubject(), claims.get("username", String.class), guildId, action, details);
    }

    public void log(Claims claims, Guild guild, String action, Map<String, Object> details) {
        log(claims.getSubject(), claims.get("username", String.class), guild, action, details);
    }

    public void log(String actorId, String actorName, String guildId, String action, Map<String, Object> details) {
        Guild guild = discordBotService.jda().map(jda -> jda.getGuildById(guildId)).orElse(null);
        logEntry(guildId, guild == null ? null : guild.getName(), actorId, actorName, action, details);
    }

    public void log(String actorId, String actorName, Guild guild, String action, Map<String, Object> details) {
        logEntry(guild.getId(), guild.getName(), actorId, actorName, action, details);
    }

    private void logEntry(
            String guildId, String guildName, String actorId, String actorName, String action, Map<String, Object> details) {
        try {
            auditLogService.log(new AuditLogEntry(
                    "dashboard", action, guildId, guildName, null, null, actorId, actorName, details));
        } catch (Exception e) {
            // best-effort audit trail; never block the underlying change on a logging failure
        }
    }
}
