package sk.gkanocz.aisauth.wipe;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.directory.StudentDirectoryService;
import sk.gkanocz.aisauth.directory.StudentRecord;
import sk.gkanocz.aisauth.directory.VerificationProperties;
import sk.gkanocz.aisauth.discordbot.RecapChannelPoster;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.GuildSettings;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import sk.gkanocz.aisauth.verification.VerifiedUser;
import sk.gkanocz.aisauth.verification.VerifiedUserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WipeService {

    private final VerifiedUserRepository verifiedUserRepository;
    private final GuildSettingsService guildSettingsService;
    private final AdminSettingsService adminSettingsService;
    private final StudentDirectoryService studentDirectoryService;
    private final VerificationProperties verificationProperties;
    private final AuditLogService auditLogService;
    private final PlatformTransactionManager transactionManager;
    private final RecapChannelPoster recapChannelPoster;
    private final LogRoutingService logRoutingService;

    private final Map<String, WipeState> progress = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public boolean isRunning(String guildId) {
        WipeState state = progress.get(guildId);
        return state != null && state.running;
    }

    public WipeStatusResponse status(String guildId) {
        WipeState state = progress.get(guildId);
        if (state == null) {
            return new WipeStatusResponse(false, false, List.of(), null, null, null);
        }
        WipeState.Stats s = state.stats;
        return new WipeStatusResponse(
                state.running, state.done, List.copyOf(state.logs),
                new WipeStatusResponse.StatsResponse(s.total, s.processed.get(), s.inactive.get(), s.errors.get()),
                state.startedAt, state.completedAt);
    }

    public WipeSettings getSettings(String guildId) {
        return adminSettingsService.get("wipe_settings_" + guildId, WipeSettings.class, WipeSettings.empty());
    }

    public int start(Guild guild, boolean removeAllRoles, List<String> keepRoleIds, String actorId, String actorName) {
        String guildId = guild.getId();
        if (isRunning(guildId)) {
            throw WipeInProgressException.create();
        }

        GuildSettings guildSettings = guildSettingsService.getOrCreate(guildId);
        if (guildSettings.getVerifiedRoleId() == null) {
            throw InvalidRequestException.withMessage("Verified role not set. Configure it in Settings first.");
        }
        if (guildSettings.getInactiveRoleId() == null) {
            throw InvalidRequestException.withMessage("Inactive role not set. Configure it in Settings first.");
        }

        List<VerifiedUser> verifiedUsers = verifiedUserRepository.findByGuildId(guildId);
        if (verifiedUsers.isEmpty()) {
            throw InvalidRequestException.withMessage("No verified users in database.");
        }

        Set<String> keepRoleIdSet = removeAllRoles ? new LinkedHashSet<>(keepRoleIds) : Set.of();
        adminSettingsService.set("wipe_settings_" + guildId, new WipeSettings(removeAllRoles, List.copyOf(keepRoleIdSet)));

        WipeState state = new WipeState(verifiedUsers.size());
        state.log("Starting wipe — checking " + verifiedUsers.size() + " verified users against LDAP"
                + (removeAllRoles ? " (remove all roles mode, keeping " + keepRoleIdSet.size() + " role(s))" : "") + "...", "info");
        progress.put(guildId, state);

        executor.submit(() -> runWipe(guild, verifiedUsers, guildSettings, removeAllRoles, keepRoleIdSet, state, actorId, actorName));
        return verifiedUsers.size();
    }

    private void runWipe(
            Guild guild, List<VerifiedUser> verifiedUsers, GuildSettings guildSettings, boolean removeAllRoles,
            Set<String> keepRoleIdSet, WipeState state, String actorId, String actorName) {
        try {
            Role verifiedRole = guild.getRoleById(guildSettings.getVerifiedRoleId());
            Role inactiveRole = guild.getRoleById(guildSettings.getInactiveRoleId());
            if (verifiedRole == null || inactiveRole == null) {
                state.log("Verified or inactive role not found in server.", "error");
                return;
            }

            Member self = guild.getSelfMember();
            if (!self.hasPermission(Permission.MANAGE_ROLES)) {
                state.log("Bot is missing Manage Roles permission. Wipe aborted.", "error");
                return;
            }
            if (!self.canInteract(verifiedRole) || !self.canInteract(inactiveRole)) {
                state.log("Bot role is too low to manage the Verified or Inactive role. Move the bot role above them. Wipe aborted.", "error");
                return;
            }

            state.log("Checking " + verifiedUsers.size() + " users against LDAP (5 concurrent)...", "info");
            List<InactiveEntry> inactiveEntries = Collections.synchronizedList(new ArrayList<>());

            List<CompletableFuture<Void>> futures = verifiedUsers.stream()
                    .map(user -> CompletableFuture.runAsync(() -> processUser(
                            guild, user, verifiedRole, inactiveRole, removeAllRoles, keepRoleIdSet, self,
                            state, inactiveEntries, verifiedUsers.size()), executor))
                    .toList();
            futures.forEach(CompletableFuture::join);

            sendInactiveLogEmbeds(guild, inactiveEntries);

            int processed = state.stats.processed.get();
            int inactive = state.stats.inactive.get();
            int errors = state.stats.errors.get();
            state.log("Wipe complete — " + processed + " checked, " + inactive + " inactive removed, " + errors + " errors", "success");

            try {
                auditLogService.log(new AuditLogEntry(
                        "dashboard", "Ran inactive-user wipe", guild.getId(), guild.getName(),
                        null, null, actorId, actorName,
                        Map.of("processed", processed, "inactive", inactive, "errors", errors)));
            } catch (Exception e) {
                // best-effort audit trail
            }

            sendRecapChannel(guild, processed, inactive, errors, state);
        } catch (Exception e) {
            state.log("Fatal wipe error: " + e.getMessage(), "error");
            log.error("Wipe error", e);
        } finally {
            state.running = false;
            state.done = true;
            state.completedAt = Instant.now().toString();
        }
    }

    private void processUser(
            Guild guild, VerifiedUser user, Role verifiedRole, Role inactiveRole, boolean removeAllRoles,
            Set<String> keepRoleIdSet, Member self, WipeState state, List<InactiveEntry> inactiveEntries, int total) {
        try {
            boolean verified = studentDirectoryService.findByAisId(user.getAisId())
                    .map(this::isEligible).orElse(false);
            int userNum = state.stats.processed.incrementAndGet();

            if (verified) {
                state.log("[" + userNum + "/" + total + "] AIS: " + user.getAisId() + " — active", "info");
                return;
            }

            Member member = retrieveMember(guild, user.getDiscordId());
            boolean inServer = member != null;
            if (member != null) {
                try {
                    if (removeAllRoles) {
                        int botHighest = self.getRoles().stream().mapToInt(Role::getPosition).max().orElse(-1);
                        List<Role> keepRoles = member.getRoles().stream()
                                .filter(r -> r.isManaged() || r.getPosition() >= botHighest || keepRoleIdSet.contains(r.getId()))
                                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                        keepRoles.add(inactiveRole);
                        guild.modifyMemberRoles(member, keepRoles).complete();
                    } else {
                        guild.removeRoleFromMember(member, verifiedRole).complete();
                        guild.addRoleToMember(member, inactiveRole).complete();
                    }
                } catch (Exception roleError) {
                    state.log("[" + userNum + "/" + total + "] " + member.getUser().getName() + " (AIS: " + user.getAisId()
                            + ") — role change failed: " + roleError.getMessage(), "error");
                    state.stats.errors.incrementAndGet();
                    return;
                }
            }

            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        verifiedUserRepository.deleteByDiscordIdAndGuildId(user.getDiscordId(), guild.getId()));
            } catch (Exception e) {
                state.stats.errors.incrementAndGet();
                return;
            }

            String name = member == null ? user.getDiscordId() : member.getUser().getName();
            state.log("[" + userNum + "/" + total + "] " + name + " (AIS: " + user.getAisId() + ") — INACTIVE"
                    + (inServer ? ", removed" : ", not in server (DB removed)"), "warn");
            state.stats.inactive.incrementAndGet();
            inactiveEntries.add(new InactiveEntry(user, member));
        } catch (Exception e) {
            state.stats.errors.incrementAndGet();
            state.log("Error checking AIS: " + user.getAisId(), "error");
        }
    }

    private boolean isEligible(StudentRecord record) {
        return record.hasAccountStatus(verificationProperties.requiredAccountStatus())
                && record.belongsToAnyFaculty(verificationProperties.allowedFaculties());
    }

    private Member retrieveMember(Guild guild, String discordId) {
        try {
            return guild.retrieveMemberById(discordId).complete();
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER || e.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                return null;
            }
            throw e;
        }
    }

    private void sendInactiveLogEmbeds(Guild guild, List<InactiveEntry> inactiveEntries) {
        String logChannelId = logRoutingService.channelIdFor(guild.getId(), LogEventType.WIPE_INACTIVE_USER_REMOVED)
                .orElse(null);
        if (logChannelId == null || inactiveEntries.isEmpty()) {
            return;
        }
        TextChannel logChannel = guild.getTextChannelById(logChannelId);
        if (logChannel == null) {
            return;
        }
        for (InactiveEntry entry : inactiveEntries) {
            try {
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(new java.awt.Color(0xFF6B6B))
                        .setTitle("Wipe - Inactive User Removed")
                        .addField("Discord", entry.member() == null ? "Unknown" : entry.member().getUser().getName(), true)
                        .addField("Discord ID", entry.user().getDiscordId(), true)
                        .addField("AIS ID", entry.user().getAisId(), true)
                        .addField("Email", entry.user().getEmail(), true)
                        .setFooter("Wipe via web dashboard");
                logChannel.sendMessageEmbeds(embed.build()).queue();
            } catch (Exception e) {
                log.error("Wipe: failed to send inactive-user log embed: {}", e.getMessage());
            }
        }
    }

    private void sendRecapChannel(Guild guild, int processed, int inactive, int errors, WipeState state) {
        String recapChannelId = logRoutingService.channelIdFor(guild.getId(), LogEventType.WIPE_RECAP).orElse(null);
        List<String> logLines = state.logs.stream().map(e -> "[" + e.time() + "] " + e.msg()).toList();
        recapChannelPoster.post(guild, recapChannelId,
                "**Wipe Complete** — " + processed + " checked, **" + inactive + " inactive removed**, " + errors + " errors",
                logLines, "wipe-report.txt");
    }

    private record InactiveEntry(VerifiedUser user, Member member) {
    }

    public record WipeStatusResponse(
            boolean running, boolean done, List<WipeState.LogEntry> logs, StatsResponse stats,
            String startedAt, String completedAt) {

        public record StatsResponse(int total, int processed, int inactive, int errors) {
        }
    }
}
