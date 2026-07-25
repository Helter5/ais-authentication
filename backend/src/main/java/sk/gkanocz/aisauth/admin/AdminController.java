package sk.gkanocz.aisauth.admin;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.AdminProperties;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import sk.gkanocz.aisauth.verification.VerificationCodeRepository;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;
import sk.gkanocz.aisauth.warn.WarnRepository;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final String MAINTENANCE_KEY = "maintenance_mode";

    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final AdminSettingsService adminSettingsService;
    private final AdminProperties adminProperties;
    private final VerifiedUserRepository verifiedUserRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final WarnRepository warnRepository;

    @GetMapping("/settings")
    public Map<String, String> getSettings(@AuthenticationPrincipal Claims claims) {
        guildAccessService.assertSuperAdmin(claims);
        return Map.of("super_admin_users", String.join(",", adminProperties.superAdminIds()));
    }

    @GetMapping("/access")
    public Map<String, Boolean> getAccess(@AuthenticationPrincipal Claims claims) {
        return Map.of("allowed", guildAccessService.isSuperAdmin(claims));
    }

    @GetMapping("/status")
    public StatusResponse getStatus(@AuthenticationPrincipal Claims claims) {
        guildAccessService.assertSuperAdmin(claims);

        int guildCount = discordBotService.jda().map(jda -> jda.getGuilds().size()).orElse(0);
        long verifiedCount = verifiedUserRepository.count();
        long activeCodesCount = verificationCodeRepository.countByExpiresAtAfter(LocalDateTime.now());
        long totalWarns = warnRepository.count();
        Runtime runtime = Runtime.getRuntime();
        long memoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        double uptimeSeconds = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;

        return new StatusResponse(
                uptimeSeconds, guildCount, verifiedCount, activeCodesCount, totalWarns,
                System.getProperty("java.version"), memoryMB);
    }

    @GetMapping("/maintenance")
    public Map<String, Boolean> getMaintenance(@AuthenticationPrincipal Claims claims) {
        guildAccessService.assertSuperAdmin(claims);
        return Map.of("enabled", adminSettingsService.get(MAINTENANCE_KEY, Boolean.class, false));
    }

    @PostMapping("/maintenance")
    public Map<String, Boolean> setMaintenance(
            @AuthenticationPrincipal Claims claims, @RequestBody SetMaintenanceRequest request) {
        guildAccessService.assertSuperAdmin(claims);
        if (request.enabled() == null) {
            throw InvalidRequestException.withMessage("enabled must be boolean");
        }
        adminSettingsService.set(MAINTENANCE_KEY, request.enabled());
        return Map.of("success", true, "enabled", request.enabled());
    }

    @GetMapping("/bot-guilds")
    public List<BotGuildResponse> getBotGuilds(@AuthenticationPrincipal Claims claims) {
        guildAccessService.assertSuperAdmin(claims);
        List<String> allowedGuildIds = adminSettingsService.get(
                "allowed_guild_ids", new TypeReference<List<String>>() { }, List.of());

        return discordBotService.jda()
                .map(jda -> jda.getGuilds().stream()
                        .map(guild -> toBotGuildResponse(guild, allowedGuildIds))
                        .toList())
                .orElseGet(List::of);
    }

    private BotGuildResponse toBotGuildResponse(Guild guild, List<String> allowedGuildIds) {
        long verifiedCount = verifiedUserRepository.countByGuildId(guild.getId());
        return new BotGuildResponse(
                guild.getId(), guild.getName(), guild.getIconUrl(), guild.getMemberCount(),
                verifiedCount, allowedGuildIds.contains(guild.getId()));
    }

    public record StatusResponse(
            double uptime,
            int guildCount,
            long verifiedCount,
            long activeCodesCount,
            long totalWarns,
            String nodeVersion,
            long memoryMB) {
    }

    public record SetMaintenanceRequest(Boolean enabled) {
    }

    public record BotGuildResponse(
            String id, String name, String icon, int memberCount, long verifiedCount, boolean allowed) {
    }
}
