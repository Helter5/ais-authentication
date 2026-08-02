package sk.gkanocz.aisauth.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.wipe.WipeService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On every bot startup, checks each guild's verified-role membership against the verified_users
 * table and drops rows for anyone who no longer holds the role (left the server, or the role was
 * removed by some other means) - deliberately Discord-cache-only, no LDAP calls, since that's
 * reserved for /wipe to avoid hammering the university LDAP server on a schedule.
 *
 * Guards against overlapping with an in-progress /wipe run on the same guild (both delete rows
 * from verified_users) - the deletes themselves are idempotent so a real overlap wouldn't corrupt
 * anything, but skipping avoids redundant work and confusing duplicate audit-log entries.
 * WipeService is injected via ObjectProvider rather than directly: it depends (transitively, via
 * DashboardAuditLogger) on DiscordBotService, which is what constructs this service, so a direct
 * constructor dependency here would recreate the exact bean-creation cycle that previously broke
 * backend startup. ObjectProvider defers resolution until isRunning() is actually called, by
 * which point DiscordBotService already exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseSyncService {

    private static final String REMOVED_DETAILS_KEY_PREFIX = "database_sync_removed_";

    private final VerifiedUserRepository verifiedUserRepository;
    private final GuildSettingsService guildSettingsService;
    private final AuditLogService auditLogService;
    private final AdminSettingsService adminSettingsService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<WipeService> wipeServiceProvider;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void syncAllGuildsAsync(List<Guild> guilds) {
        executor.submit(() -> {
            for (Guild guild : guilds) {
                try {
                    syncGuild(guild);
                } catch (Exception e) {
                    log.error("Database sync failed for guild {}", guild.getId(), e);
                }
            }
        });
    }

    private void syncGuild(Guild guild) {
        String guildId = guild.getId();
        if (wipeServiceProvider.getObject().isRunning(guildId)) {
            log.info("Database sync: skipping guild {} - a wipe is currently in progress", guildId);
            return;
        }

        GuildSettings settings = guildSettingsService.getOrCreate(guildId);
        String verifiedRoleId = settings.getVerifiedRoleId();
        if (verifiedRoleId == null) {
            return;
        }
        Role verifiedRole = guild.getRoleById(verifiedRoleId);
        if (verifiedRole == null) {
            log.warn("Database sync: verified role {} not found in guild {}", verifiedRoleId, guildId);
            return;
        }

        try {
            guild.loadMembers().setTimeout(Duration.ofSeconds(30)).get();
        } catch (Exception e) {
            log.error("Database sync: failed to load members for guild {}, skipping this run", guildId, e);
            return;
        }

        List<VerifiedUser> dbUsers = verifiedUserRepository.findByGuildId(guildId);
        long roleMemberCount = guild.getMembersWithRoles(verifiedRole).size();

        List<RemovedVerificationEntry> removedEntries = new ArrayList<>();
        if (roleMemberCount != dbUsers.size()) {
            for (VerifiedUser user : dbUsers) {
                Member member = guild.getMemberById(user.getDiscordId());
                boolean stillVerified = member != null && member.getRoles().contains(verifiedRole);
                if (!stillVerified) {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                            verifiedUserRepository.deleteByDiscordIdAndGuildId(user.getDiscordId(), guildId));
                    removedEntries.add(new RemovedVerificationEntry(
                            user.getDiscordId(), member == null ? null : member.getUser().getName(),
                            user.getAisId(), user.getEmail()));
                }
            }
        }
        int removed = removedEntries.size();

        guildSettingsService.recordDatabaseSync(guildId, dbUsers.size(), removed);
        adminSettingsService.set(REMOVED_DETAILS_KEY_PREFIX + guildId, removedEntries);
        log.info("Database sync for guild {}: checked {}, removed {}", guildId, dbUsers.size(), removed);

        if (removed > 0) {
            try {
                auditLogService.log(new AuditLogEntry(
                        "dashboard", "Database sync removed stale verification(s)",
                        guild.getId(), guild.getName(), null, null, "system", "System",
                        Map.of("checked", dbUsers.size(), "removed", removed)));
            } catch (Exception e) {
                log.error("Failed to write database sync audit log", e);
            }
        }
    }
}
