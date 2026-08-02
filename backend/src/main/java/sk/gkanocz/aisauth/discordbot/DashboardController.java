package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.audit.AuditLog;
import sk.gkanocz.aisauth.audit.AuditLogRepository;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.directory.VerificationProperties;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.verification.RemovedVerificationEntry;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;
import tools.jackson.core.type.TypeReference;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private static final int RECENT_ACTIVITY_LIMIT = 8;

    private final DiscordBotService discordBotService;
    private final GuildAccessService guildAccessService;
    private final VerificationProperties verificationProperties;
    private final GuildSettingsService guildSettingsService;
    private final VerifiedUserRepository verifiedUserRepository;
    private final AuditLogRepository auditLogRepository;
    private final AdminSettingsService adminSettingsService;

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);

        DashboardResponse.ServerInfo server = new DashboardResponse.ServerInfo(
                guild.getId(),
                guild.getName(),
                guild.getIconUrl(),
                guild.getMemberCount(),
                guild.getCategories().size(),
                guild.getTextChannels().size(),
                guild.getVoiceChannels().size(),
                guild.getRoles().size());

        GuildSettings settings = guildSettingsService.getOrCreate(guildId);
        List<RemovedVerificationEntry> removedUsers = adminSettingsService.get(
                "database_sync_removed_" + guildId, new TypeReference<List<RemovedVerificationEntry>>() { }, List.of());
        DashboardResponse.Synchronization synchronization = new DashboardResponse.Synchronization(
                settings.getLastSyncAt() == null ? null : settings.getLastSyncAt().toString(),
                settings.getLastSyncCheckedCount(),
                settings.getLastSyncRemovedCount(),
                removedUsers);

        DashboardResponse.VerificationConfig verificationConfig = new DashboardResponse.VerificationConfig(
                verificationProperties.allowedFaculties(),
                verificationProperties.requiredAccountStatus(),
                settings.getVerifiedRoleId(),
                roleName(guild, settings.getVerifiedRoleId()),
                settings.getInactiveRoleId(),
                roleName(guild, settings.getInactiveRoleId()),
                (int) verifiedUserRepository.countByGuildId(guildId));

        var recentActivity = auditLogRepository
                .findByGuildIdOrderByCreatedAtDesc(guildId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
                .stream()
                .map(this::toActivityEntry)
                .toList();

        return new DashboardResponse(server, synchronization, verificationConfig, recentActivity);
    }

    private String roleName(Guild guild, String roleId) {
        if (roleId == null) {
            return null;
        }
        Role role = guild.getRoleById(roleId);
        return role == null ? null : role.getName();
    }

    private DashboardResponse.ActivityEntry toActivityEntry(AuditLog log) {
        return new DashboardResponse.ActivityEntry(log.getCategory(), log.getAction(), log.getUsername(), log.getCreatedAt());
    }
}
