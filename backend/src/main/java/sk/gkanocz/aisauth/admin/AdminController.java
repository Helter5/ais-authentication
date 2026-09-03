package sk.gkanocz.aisauth.admin;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sk.gkanocz.aisauth.auth.AdminProperties;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.PublicToAuthenticated;
import sk.gkanocz.aisauth.auth.SuperAdminAccess;
import sk.gkanocz.aisauth.directory.LdapConnectionSample;
import sk.gkanocz.aisauth.directory.LdapConnectionSampleRepository;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import sk.gkanocz.aisauth.verification.VerificationCodeRepository;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;
import tools.jackson.core.type.TypeReference;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final String MAINTENANCE_KEY = "maintenance_mode";

    /**
     * The three windows the "LDAP Connection" widget can be switched to (see getLdapStatus) - each
     * bucketed at a unit fine enough to give ~a screenful of bars: minute-by-minute for the last
     * hour (each bucket is exactly one LdapUptimeProbeJob sample, so outages show at full
     * resolution), hourly for the last day, daily for the last week.
     */
    private static final Map<String, LdapStatusRange> LDAP_STATUS_RANGES = Map.of(
            "hour", new LdapStatusRange(ChronoUnit.MINUTES, 60),
            "day", new LdapStatusRange(ChronoUnit.HOURS, 24),
            "week", new LdapStatusRange(ChronoUnit.DAYS, 7));

    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final AdminSettingsService adminSettingsService;
    private final AdminProperties adminProperties;
    private final VerifiedUserRepository verifiedUserRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final MaintenanceModeBroadcaster maintenanceModeBroadcaster;
    private final LdapConnectionSampleRepository ldapConnectionSampleRepository;
    private final DashboardAuditLogger dashboardAuditLogger;

    @SuperAdminAccess
    @GetMapping("/settings")
    public Map<String, String> getSettings(@AuthenticationPrincipal Claims claims) {
        guildAccessService.assertSuperAdmin(claims);
        return Map.of("super_admin_users", String.join(",", adminProperties.superAdminIds()));
    }

    @PublicToAuthenticated
    @GetMapping("/access")
    public Map<String, Boolean> getAccess(@AuthenticationPrincipal Claims claims) {
        return Map.of("allowed", guildAccessService.isSuperAdmin(claims));
    }

    @SuperAdminAccess
    @GetMapping("/status")
    public StatusResponse getStatus(@AuthenticationPrincipal Claims claims) {
        guildAccessService.assertSuperAdmin(claims);

        int guildCount = discordBotService.jda().map(jda -> jda.getGuilds().size()).orElse(0);
        long verifiedCount = verifiedUserRepository.count();
        long activeCodesCount = verificationCodeRepository.countByExpiresAtAfter(LocalDateTime.now());
        Runtime runtime = Runtime.getRuntime();
        long memoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        double uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;

        return new StatusResponse(
                uptimeSeconds, guildCount, verifiedCount, activeCodesCount,
                System.getProperty("java.version"), memoryMB);
    }

    /**
     * Feeds the "LDAP Connection" widget on the main dashboard - buckets over the requested window
     * ({@code range}: hour/day/week, see {@link #LDAP_STATUS_RANGES}) built from
     * {@link LdapConnectionSample} rows written by LdapUptimeProbeJob every 60s, plus the single
     * most recent sample for the live status badge. Buckets with no samples at all (app restart,
     * deploy gap) are included as zero/no-data rather than skipped, so the widget can render them
     * as gaps instead of stretching neighbors.
     */
    @SuperAdminAccess
    @GetMapping("/ldap-status")
    public LdapStatusResponse getLdapStatus(
            @AuthenticationPrincipal Claims claims, @RequestParam(defaultValue = "day") String range) {
        guildAccessService.assertSuperAdmin(claims);

        LdapStatusRange config = LDAP_STATUS_RANGES.get(range);
        if (config == null) {
            throw InvalidRequestException.withMessage("range must be one of: " + String.join(", ", LDAP_STATUS_RANGES.keySet()));
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minus(config.count() - 1, config.unit()).truncatedTo(config.unit());
        List<LdapConnectionSample> samples =
                ldapConnectionSampleRepository.findBySampledAtAfterOrderBySampledAtAsc(windowStart);

        Map<LocalDateTime, List<LdapConnectionSample>> byBucket = new LinkedHashMap<>();
        for (int i = 0; i < config.count(); i++) {
            byBucket.put(windowStart.plus(i, config.unit()), new ArrayList<>());
        }
        for (LdapConnectionSample sample : samples) {
            LocalDateTime bucket = sample.getSampledAt().truncatedTo(config.unit());
            byBucket.computeIfAbsent(bucket, key -> new ArrayList<>()).add(sample);
        }

        List<LdapStatusBucket> buckets = byBucket.entrySet().stream()
                .map(entry -> toBucket(entry.getKey(), entry.getValue()))
                .toList();

        long successCount = samples.stream().filter(LdapConnectionSample::isSuccess).count();
        double uptimePercent = samples.isEmpty() ? 0.0 : (100.0 * successCount / samples.size());

        Optional<LdapConnectionSample> last = ldapConnectionSampleRepository.findTopByOrderBySampledAtDesc();
        return new LdapStatusResponse(
                last.map(LdapConnectionSample::isSuccess).orElse(false),
                last.map(LdapConnectionSample::getSampledAt).orElse(null),
                last.map(LdapConnectionSample::getLatencyMs).orElse(null),
                uptimePercent,
                buckets);
    }

    private LdapStatusBucket toBucket(LocalDateTime bucketStart, List<LdapConnectionSample> bucketSamples) {
        int successCount = (int) bucketSamples.stream().filter(LdapConnectionSample::isSuccess).count();
        int failCount = bucketSamples.size() - successCount;
        OptionalDouble avg = bucketSamples.stream()
                .filter(LdapConnectionSample::isSuccess)
                .map(LdapConnectionSample::getLatencyMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average();
        Long avgLatencyMs = avg.isPresent() ? (long) avg.getAsDouble() : null;
        return new LdapStatusBucket(bucketStart, successCount, failCount, avgLatencyMs);
    }

    @SuperAdminAccess
    @GetMapping("/maintenance")
    public Map<String, Boolean> getMaintenance(@AuthenticationPrincipal Claims claims) {
        guildAccessService.assertSuperAdmin(claims);
        return Map.of("enabled", currentMaintenanceState());
    }

    @SuperAdminAccess
    @PostMapping("/maintenance")
    public Map<String, Boolean> setMaintenance(
            @AuthenticationPrincipal Claims claims, @RequestBody SetMaintenanceRequest request) {
        guildAccessService.assertSuperAdmin(claims);
        if (request.enabled() == null) {
            throw InvalidRequestException.withMessage("enabled must be boolean");
        }
        adminSettingsService.set(MAINTENANCE_KEY, request.enabled());
        maintenanceModeBroadcaster.broadcast(request.enabled());
        // Bot-wide, not tied to one guild - but the Logs page can only ever be viewed guild-by-guild,
        // so this is logged once per currently-joined guild instead of once with no guild at all
        // (which would be unreachable from any dashboard's Logs tab).
        discordBotService.jda().ifPresent(jda -> jda.getGuilds().forEach(guild ->
                dashboardAuditLogger.log(claims, guild, request.enabled() ? "Enabled maintenance mode" : "Disabled maintenance mode",
                        Map.of("enabled", request.enabled()))));
        return Map.of("success", true, "enabled", request.enabled());
    }

    /**
     * Not super-admin-gated on purpose - the maintenance banner is shown to every logged-in
     * manager, not just super admins, so every dashboard tab needs to be able to subscribe.
     */
    @PublicToAuthenticated
    @GetMapping("/maintenance/stream")
    public SseEmitter streamMaintenance() {
        return maintenanceModeBroadcaster.subscribe(currentMaintenanceState());
    }

    private boolean currentMaintenanceState() {
        return adminSettingsService.get(MAINTENANCE_KEY, Boolean.class, false);
    }

    @SuperAdminAccess
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
            String nodeVersion,
            long memoryMB) {
    }

    public record SetMaintenanceRequest(Boolean enabled) {
    }

    public record BotGuildResponse(
            String id, String name, String icon, int memberCount, long verifiedCount, boolean allowed) {
    }

    public record LdapStatusResponse(
            boolean currentlyUp,
            LocalDateTime lastCheckedAt,
            Long lastLatencyMs,
            double uptimePercent,
            List<LdapStatusBucket> buckets) {
    }

    public record LdapStatusBucket(
            LocalDateTime bucketStart, int successCount, int failCount, Long avgLatencyMs) {
    }

    private record LdapStatusRange(ChronoUnit unit, int count) {
    }
}
