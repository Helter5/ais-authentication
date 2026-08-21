package sk.gkanocz.aisauth.semester;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import org.springframework.stereotype.Service;
import sk.gkanocz.aisauth.discordbot.BotPermissionChecker;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.DiscordModerationService;
import sk.gkanocz.aisauth.discordbot.RecapChannelPoster;
import sk.gkanocz.aisauth.semester.SwitchSemesterSettings.AdditionalChannel;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;
import sk.gkanocz.aisauth.subjectrole.SubjectRoleService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Owns the standalone Setup operation and the low-level step engine (visibility + role-mapping +
 * cleanup, or just visibility + cleanup for a Setup step) that both standalone Setup and every
 * {@link SemesterPlanService} plan step run through - one implementation of "how a single semester
 * gets shown/hidden and has its roles carried over", reused instead of duplicated per caller.
 *
 * <p>The old bare single-pair "run a switch directly" action is retired - a Switch is now always a
 * named {@link SwitchSemesterSettings.SwitchPlan} run via {@link SemesterPlanService}, since a real
 * calendar semester boundary moves several year-cohorts (each its own from/to pair, plus sometimes a
 * fresh cohort's Setup) at once, not one pair in isolation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemesterOperationService {

    private final AdminSettingsService adminSettingsService;
    private final DashboardAuditLogger dashboardAuditLogger;
    private final RecapChannelPoster recapChannelPoster;
    private final DiscordModerationService moderationService;
    private final SemesterVisibilityService semesterVisibilityService;
    private final LogRoutingService logRoutingService;
    private final SubjectRoleService subjectRoleService;
    private final SemesterRoleMigrationRepository semesterRoleMigrationRepository;
    private final SemesterSwitchHistoryRepository semesterSwitchHistoryRepository;
    private final SemesterVisibilityMigrationRepository semesterVisibilityMigrationRepository;
    private final SemesterStatusBroadcaster semesterStatusBroadcaster;

    private static final String CURRENT_PLAN_PREFIX = "semester_current_plan_";

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Guards the read-check-write sequence in startSetup/SemesterPlanService.startPlan (read "is
     * anything already running for this guild" from AdminSettingsService, validate, then persist the
     * new running state) - without this, two concurrent start calls for the same guild (a
     * double-click, or a setup and a plan racing each other) can both observe "not running" before
     * either writes, both proceed, and the second run's initial tracker.save() silently clobbers the
     * first's persisted progress. One lock object per guild, package-private so
     * SemesterPlanService/SemesterRollbackService synchronize on the exact same lock rather than a
     * second independent one that wouldn't actually exclude anything.
     */
    private final Map<String, Object> operationLocks = new ConcurrentHashMap<>();

    Object lockFor(String guildId) {
        return operationLocks.computeIfAbsent(guildId, id -> new Object());
    }

    /**
     * At most one of setup/plan/rollback may run per guild at a time - they all mutate the same
     * channel visibility and member roles, so letting two race would produce the same
     * silently-clobbered-progress bug the lock comment above describes. Called from inside the
     * caller's own {@code synchronized(lockFor(...))} block (the intrinsic lock is reentrant, so
     * this doesn't deadlock) right before that caller persists its own "running" state.
     */
    void assertNoOtherOperationRunning(String guildId, String ownProgressKey) {
        synchronized (lockFor(guildId)) {
            for (String key : List.of("semester_setup_log_" + guildId, "switchplan_log_" + guildId,
                    "switchsemester_rollback_log_" + guildId)) {
                if (key.equals(ownProgressKey)) {
                    continue;
                }
                SemesterOperationState other = adminSettingsService.get(key, SemesterOperationState.class, null);
                if (other != null && other.running()) {
                    throw SemesterOperationInProgressException.withMessage(
                            "Another semester operation is already in progress for this guild.");
                }
            }
            SemesterOperationState own = adminSettingsService.get(ownProgressKey, SemesterOperationState.class, null);
            if (own != null && own.running()) {
                throw SemesterOperationInProgressException.withMessage("This operation is already in progress.");
            }
        }
    }

    // ── Current plan (plan-path lock) ───────────────────────────────────────

    /** The guild's tracked current plan id, or null if never set (no plan has completed yet). */
    public String currentPlan(String guildId) {
        return adminSettingsService.get(CURRENT_PLAN_PREFIX + guildId, String.class, null);
    }

    /**
     * The semester type (WINTER/SUMMER) the guild is currently in, or null if no plan has completed
     * yet or the current plan's resulting semester has no type configured. Used outside this package
     * to filter which subject roles /pridatpredmet may grant per semester.
     */
    public String currentSemesterType(String guildId) {
        String planId = currentPlan(guildId);
        if (planId == null) {
            return null;
        }
        SwitchSemesterSettings settings = adminSettingsService.get(
                SemesterController.configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
        return settings.resultingSemesterType(planId);
    }

    void setCurrentPlan(String guildId, String planId) {
        adminSettingsService.set(CURRENT_PLAN_PREFIX + guildId, planId);
        // Single choke point for both a completed Plan run (SemesterPlanService.runPlan) and a
        // manual override (below) - push the new type instantly to every open dashboard tab instead
        // of leaving them to find out on their next poll/reload.
        SwitchSemesterSettings settings = adminSettingsService.get(
                SemesterController.configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
        semesterStatusBroadcaster.broadcast(guildId, settings.resultingSemesterType(planId));
    }

    /**
     * Escape hatch for a desynced current-plan pointer (bad data migration, manual Discord role
     * cleanup, ...) - sets it directly with no role/channel changes. Everyday corrections should go
     * through {@link SemesterPlanService#startPlan} or a rollback instead; this exists only for when
     * those aren't the right tool.
     */
    public void overrideCurrentPlan(Guild guild, String planId, String actorId, String actorName) {
        String guildId = guild.getId();
        SwitchSemesterSettings settings = adminSettingsService.get(
                SemesterController.configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
        SwitchSemesterSettings.SwitchPlan plan = settings.findPlan(planId);
        if (plan == null) {
            throw InvalidRequestException.withMessage("Plan \"" + planId + "\" not configured.");
        }
        String previous = currentPlan(guildId);
        setCurrentPlan(guildId, planId);
        logAudit(actorId, actorName, guild, "Manually set current plan", Map.of(
                "before", previous == null ? "(none)" : previous, "after", plan.name()));
    }

    /**
     * What "Select switch" would auto-run right now with no manual picking - the tracked current
     * plan plus whatever the configured, perpetually-cycling {@code planPath} says comes right
     * after it. Either side is null when there's nothing to infer yet (no tracked position, or no
     * configured path) - callers fall back to a plain plan picker.
     */
    public NextPlan nextPlan(String guildId) {
        String current = currentPlan(guildId);
        SwitchSemesterSettings settings = adminSettingsService.get(
                SemesterController.configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
        String nextId = settings.nextPlanId(current);
        return new NextPlan(current, nextId);
    }

    public record NextPlan(String currentPlanId, String nextPlanId) {
    }

    // ── Setup (standalone) ──────────────────────────────────────────────────

    public void startSetup(Guild guild, String semesterName, boolean visible, boolean clearRoles, boolean resume,
                            String actorId, String actorName) {
        String guildId = guild.getId();
        String recapChannelId = logRoutingService.channelIdFor(guildId, LogEventType.SEMESTER_RECAP).orElse(null);
        if (recapChannelId == null) {
            throw InvalidRequestException.withMessage("Semester log channel not configured.");
        }

        String progressKey = "semester_setup_log_" + guildId;
        SemesterProgressTracker tracker;
        SemesterDefinition sem;
        boolean everyoneViewChannel;
        String migrationId;

        synchronized (lockFor(guildId)) {
            assertNoOtherOperationRunning(guildId, progressKey);
            SemesterOperationState previous = adminSettingsService.get(progressKey, SemesterOperationState.class, null);

            SwitchSemesterSettings settings = adminSettingsService.get(
                    SemesterController.configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
            sem = settings.find(semesterName);
            if (sem == null) {
                throw InvalidRequestException.withMessage("Semester \"" + semesterName + "\" not configured.");
            }

            everyoneViewChannel = visible && sem.isEveryoneViewChannel();
            migrationId = resume && previous != null && previous.params() != null && previous.params().get("migrationId") != null
                    ? String.valueOf(previous.params().get("migrationId"))
                    : UUID.randomUUID().toString();

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("semesterName", semesterName);
            params.put("visible", visible);
            params.put("everyoneViewChannel", everyoneViewChannel);
            params.put("clearRoles", clearRoles);
            params.put("migrationId", migrationId);

            if (resume) {
                boolean canResume = previous != null && List.of("partial", "failed").contains(previous.status())
                        && params.equals(previous.params());
                if (!canResume) {
                    throw InvalidRequestException.withMessage("There is no matching failed semester setup to resume.");
                }
            } else {
                semesterSwitchHistoryRepository.save(SemesterSwitchHistory.forSetup(
                        guildId, migrationId, semesterName, actorId, actorName, LocalDateTime.now()));
            }

            tracker = new SemesterProgressTracker(adminSettingsService, progressKey, resume, previous, params, "setup");
            tracker.save(0, resume
                    ? "Resuming setup: " + semesterName + " (" + tracker.completedCount() + " step"
                            + (tracker.completedCount() == 1 ? "" : "s") + " already complete)"
                    : "Setup: " + semesterName + " — " + (visible ? "showing" : "hiding") + " channels, @everyone View Channel = "
                            + everyoneViewChannel + (clearRoles ? ", clearing subject roles" : ""));
        }

        String finalMigrationId = migrationId;
        executor.submit(() -> runSetup(guild, sem, semesterName, visible, everyoneViewChannel, clearRoles,
                tracker, finalMigrationId, recapChannelId, actorId, actorName));
    }

    private void runSetup(
            Guild guild, SemesterDefinition sem, String semesterName, boolean visible, boolean everyoneViewChannel,
            boolean clearRoles, SemesterProgressTracker tracker, String migrationId, String recapChannelId,
            String actorId, String actorName) {
        try {
            boolean completed = executeSetupStep(
                    guild, sem, semesterName, visible, everyoneViewChannel, clearRoles, tracker, "", migrationId, 0, null, 0, 100);
            String finalStatus = completed ? "success" : "partial";
            tracker.save(100, completed
                    ? "Semester setup complete."
                    : "[WARN] Semester setup finished with unfinished step(s). Use Resume to retry them.", finalStatus);

            logAudit(actorId, actorName, guild, "Ran semester setup", Map.of(
                    "semesterName", semesterName, "visible", visible, "everyoneViewChannel", everyoneViewChannel,
                    "clearRoles", clearRoles, "status", finalStatus));

            postRecap(guild, recapChannelId,
                    "**Semester Setup " + (completed ? "Complete" : "Finished With Errors") + "** — `"
                            + semesterName + "` channels " + (visible ? "shown" : "hidden")
                            + (clearRoles ? ", cleanup roles processed" : ""),
                    tracker.logsSnapshot(), "semester-setup-report.txt");
        } catch (Exception e) {
            tracker.save(100, "[ERROR] Fatal error: " + e.getMessage(), "failed");
            log.error("[setupsemester/run] Error", e);
        }
    }

    // ── Step engine (shared by standalone Setup and every SemesterPlanService step) ────────────

    /**
     * Shows/hides one semester's categories and optionally clears its cleanup roles from every
     * member holding them. {@code stepPrefix} namespaces the tracker's completed-step keys (empty
     * for standalone Setup, {@code "step<N>:"} for a plan step) so a multi-step Plan's resume
     * tracking doesn't collide between steps sharing one tracker. {@code rangeStart}/{@code rangeEnd}
     * rescale this step's own 0-100 progress numbers into its slice of the overall run.
     *
     * @return true if every sub-part of this step completed with zero errors.
     */
    boolean executeSetupStep(
            Guild guild, SemesterDefinition sem, String semesterName, boolean visible, boolean everyoneViewChannel,
            boolean clearRoles, SemesterProgressTracker tracker, String stepPrefix, String migrationId, int stepIndex,
            String stepLabel, int rangeStart, int rangeEnd) {
        List<Permission> requiredPerms = new ArrayList<>();
        if (!sem.categoryIdsOrEmpty().isEmpty()) {
            requiredPerms.add(Permission.MANAGE_CHANNEL);
        }
        if (clearRoles) {
            requiredPerms.add(Permission.MANAGE_ROLES);
        }
        List<String> missingPerms = BotPermissionChecker.missingPermissions(guild, requiredPerms.toArray(new Permission[0]));
        if (!missingPerms.isEmpty()) {
            tracker.save(rescale(100, rangeStart, rangeEnd), "[WARN] Bot is missing required permissions: "
                    + String.join(", ", missingPerms) + ". Step skipped.");
            return false;
        }

        boolean visibilityDone;
        if (tracker.hasCompleted(stepPrefix + "visibility")) {
            tracker.save(rescale(60, rangeStart, rangeEnd), "Channel visibility already completed — skipped");
            visibilityDone = true;
        } else if (!sem.categoryIdsOrEmpty().isEmpty()) {
            int count = sem.categoryIdsOrEmpty().size();
            tracker.save(rescale(10, rangeStart, rangeEnd),
                    (visible ? "Showing " : "Hiding ") + count + " categor" + (count == 1 ? "y" : "ies") + " for " + semesterName + "...");
            SemesterVisibilityService.Result r;
            try {
                r = semesterVisibilityService.apply(guild, sem.categoryIdsOrEmpty(), visible, everyoneViewChannel);
            } catch (Exception e) {
                tracker.save(rescale(60, rangeStart, rangeEnd), "[WARN] Error setting visibility: " + e.getMessage());
                r = null;
            }
            for (String line : r == null ? List.<String>of() : r.logs()) {
                if (line.contains("ERROR") || line.contains("WARNING")) {
                    tracker.save(rescale(60, rangeStart, rangeEnd), "[WARN] " + line);
                }
            }
            visibilityDone = r != null && r.errors() == 0;
            if (visibilityDone) {
                tracker.completeStep(stepPrefix + "visibility");
                // "hide" snapshots sem's OWN everyoneViewChannel (what showing it again should look
                // like), not the false just applied - matches the switch-step convention below.
                logVisibilityMigration(guild, migrationId, stepIndex, stepLabel, sem.categoryIdsOrEmpty(),
                        visible ? SemesterVisibilityMigration.DIRECTION_SHOW : SemesterVisibilityMigration.DIRECTION_HIDE,
                        visible ? everyoneViewChannel : sem.isEveryoneViewChannel());
                tracker.save(rescale(60, rangeStart, rangeEnd), (visible ? "Shown " : "Hidden ") + r.channelsUpdated()
                        + " channels (" + r.categoriesUpdated() + " categories)");
            }
        } else {
            tracker.completeStep(stepPrefix + "visibility");
            tracker.save(rescale(60, rangeStart, rangeEnd), "No categories configured — skipped");
            visibilityDone = true;
        }

        boolean cleanupDone;
        if (!clearRoles) {
            tracker.completeStep(stepPrefix + "cleanupRoles");
            tracker.save(rescale(95, rangeStart, rangeEnd), "Subject role clear skipped");
            cleanupDone = true;
        } else if (tracker.hasCompleted(stepPrefix + "cleanupRoles")) {
            tracker.save(rescale(95, rangeStart, rangeEnd), "Subject role cleanup already completed — skipped");
            cleanupDone = true;
        } else if (!sem.semesterRolesOrEmpty().isEmpty()) {
            tracker.save(rescale(65, rangeStart, rangeEnd),
                    "Clearing " + sem.semesterRolesOrEmpty().size() + " subject role(s) from all members...");
            int removed = 0;
            int failed = 0;
            try {
                List<Member> allMembers = guild.loadMembers().get();
                Map<String, Integer> failureReasons = new LinkedHashMap<>();
                for (String roleId : sem.semesterRolesOrEmpty()) {
                    Role role = guild.getRoleById(roleId);
                    List<Member> targets = allMembers.stream().filter(m -> hasRole(m, roleId) && !m.getUser().isBot()).toList();
                    if (!targets.isEmpty()) {
                        tracker.save(rescale(65, rangeStart, rangeEnd), "Clearing @" + (role != null ? role.getName() : roleId)
                                + " from " + targets.size() + " member(s)...");
                        for (Member member : targets) {
                            try {
                                guild.removeRoleFromMember(member, role).complete();
                                semesterRoleMigrationRepository.save(new SemesterRoleMigration(
                                        guild.getId(), migrationId, stepIndex, stepLabel, member.getId(), roleId, null, false, LocalDateTime.now()));
                                removed++;
                            } catch (Exception e) {
                                failed++;
                                recordFailure(failureReasons, e);
                            }
                        }
                    }
                }
                cleanupDone = failed == 0;
                if (cleanupDone) {
                    tracker.completeStep(stepPrefix + "cleanupRoles");
                }
                tracker.save(rescale(95, rangeStart, rangeEnd), "Subject roles cleared: " + removed + " removed"
                        + (failed > 0 ? ", " + failed + " failed: " + summarizeFailures(failureReasons) : ""));
            } catch (Exception e) {
                tracker.save(rescale(95, rangeStart, rangeEnd), "[ERROR] Could not clear subject roles: " + e.getMessage());
                cleanupDone = false;
            }
        } else {
            tracker.completeStep(stepPrefix + "cleanupRoles");
            tracker.save(rescale(95, rangeStart, rangeEnd), "No subject roles configured — skipped");
            cleanupDone = true;
        }

        return visibilityDone && cleanupDone;
    }

    /**
     * Hides {@code oldSem}'s categories, shows {@code newSem}'s, applies {@code oldSem}'s role
     * mappings, then clears its cleanup roles - one Plan step's worth of "switch away from oldSem
     * onto newSem". Same {@code stepPrefix}/rescale contract as {@link #executeSetupStep}.
     *
     * @return true if every sub-part of this step completed with zero errors.
     */
    boolean executeSwitchStep(
            Guild guild, SemesterDefinition oldSem, SemesterDefinition newSem, String oldName, String newName,
            boolean newEveryoneViewChannel, SemesterProgressTracker tracker, String stepPrefix, String migrationId,
            int stepIndex, String stepLabel, int rangeStart, int rangeEnd) {
        List<String> missingPerms = BotPermissionChecker.missingPermissions(guild, Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES);
        if (!missingPerms.isEmpty()) {
            tracker.save(rescale(100, rangeStart, rangeEnd), "[WARN] Bot is missing required permissions: "
                    + String.join(", ", missingPerms) + ". Step skipped.");
            return false;
        }

        boolean hideDone;
        if (tracker.hasCompleted(stepPrefix + "hideOld")) {
            tracker.save(rescale(35, rangeStart, rangeEnd), "Hiding " + oldName + " already completed — skipped");
            hideDone = true;
        } else if (!oldSem.categoryIdsOrEmpty().isEmpty()) {
            int count = oldSem.categoryIdsOrEmpty().size();
            tracker.save(rescale(5, rangeStart, rangeEnd), "Hiding " + count + " categor" + (count == 1 ? "y" : "ies") + " for " + oldName + "...");
            SemesterVisibilityService.Result r;
            try {
                r = semesterVisibilityService.apply(guild, oldSem.categoryIdsOrEmpty(), false, false);
            } catch (Exception e) {
                tracker.save(rescale(35, rangeStart, rangeEnd), "[WARN] Error hiding old semester: " + e.getMessage());
                r = null;
            }
            for (String line : r == null ? List.<String>of() : r.logs()) {
                if (line.contains("ERROR") || line.contains("WARNING")) {
                    tracker.save(rescale(35, rangeStart, rangeEnd), "[WARN] " + line);
                }
            }
            hideDone = r != null && r.errors() == 0;
            if (hideDone) {
                tracker.completeStep(stepPrefix + "hideOld");
                // Snapshot oldSem's OWN everyoneViewChannel value here, not the false just applied to
                // hide it - a later revert needs "what showing this semester again should look like".
                logVisibilityMigration(guild, migrationId, stepIndex, stepLabel, oldSem.categoryIdsOrEmpty(),
                        SemesterVisibilityMigration.DIRECTION_HIDE, oldSem.isEveryoneViewChannel());
                tracker.save(rescale(35, rangeStart, rangeEnd), "Hidden " + r.channelsUpdated() + " channels (" + r.categoriesUpdated() + " categories)");
            }
        } else {
            tracker.completeStep(stepPrefix + "hideOld");
            tracker.save(rescale(35, rangeStart, rangeEnd), "No categories for " + oldName + " — skipped");
            hideDone = true;
        }

        boolean showDone;
        if (tracker.hasCompleted(stepPrefix + "showNew")) {
            tracker.save(rescale(65, rangeStart, rangeEnd), "Showing " + newName + " already completed — skipped");
            showDone = true;
        } else if (!newSem.categoryIdsOrEmpty().isEmpty()) {
            int count = newSem.categoryIdsOrEmpty().size();
            tracker.save(rescale(40, rangeStart, rangeEnd), "Showing " + count + " categor" + (count == 1 ? "y" : "ies") + " for " + newName + "...");
            SemesterVisibilityService.Result r;
            try {
                r = semesterVisibilityService.apply(guild, newSem.categoryIdsOrEmpty(), true, newEveryoneViewChannel);
            } catch (Exception e) {
                tracker.save(rescale(65, rangeStart, rangeEnd), "[WARN] Error showing new semester: " + e.getMessage());
                r = null;
            }
            for (String line : r == null ? List.<String>of() : r.logs()) {
                if (line.contains("ERROR") || line.contains("WARNING")) {
                    tracker.save(rescale(65, rangeStart, rangeEnd), "[WARN] " + line);
                }
            }
            showDone = r != null && r.errors() == 0;
            if (showDone) {
                tracker.completeStep(stepPrefix + "showNew");
                logVisibilityMigration(guild, migrationId, stepIndex, stepLabel, newSem.categoryIdsOrEmpty(),
                        SemesterVisibilityMigration.DIRECTION_SHOW, newEveryoneViewChannel);
                tracker.save(rescale(65, rangeStart, rangeEnd), "Shown " + r.channelsUpdated() + " channels (" + r.categoriesUpdated() + " categories)");
            }
        } else {
            tracker.completeStep(stepPrefix + "showNew");
            tracker.save(rescale(65, rangeStart, rangeEnd), "No categories for " + newName + " — skipped");
            showDone = true;
        }

        boolean needsMemberFetch = (!tracker.hasCompleted(stepPrefix + "roleMappings") && !oldSem.roleMappingsOrEmpty().isEmpty())
                || (!tracker.hasCompleted(stepPrefix + "cleanupRoles") && !oldSem.semesterRolesOrEmpty().isEmpty());
        List<Member> allMembers;
        try {
            allMembers = needsMemberFetch ? guild.loadMembers().get() : null;
        } catch (Exception e) {
            tracker.save(rescale(95, rangeStart, rangeEnd), "[ERROR] Could not load members: " + e.getMessage());
            return false;
        }

        boolean roleMappingsDone;
        List<SemesterDefinition.RoleMapping> roleMappings = oldSem.roleMappingsOrEmpty();
        if (tracker.hasCompleted(stepPrefix + "roleMappings")) {
            tracker.save(rescale(95, rangeStart, rangeEnd), "Role mappings already completed — skipped");
            roleMappingsDone = true;
        } else if (!roleMappings.isEmpty()) {
            tracker.save(rescale(70, rangeStart, rangeEnd), "Applying " + roleMappings.size() + " role mapping(s)...");
            int rolesProcessed = 0;
            Map<String, Integer> mappingFailureReasons = new LinkedHashMap<>();
            for (int i = 0; i < roleMappings.size(); i++) {
                SemesterDefinition.RoleMapping mapping = roleMappings.get(i);
                if (mapping.fromRoleId() == null || mapping.toRoleIdsOrEmpty().isEmpty()) {
                    continue;
                }
                List<String> conditionRoleIds = mapping.conditionRoleIdsOrEmpty();
                List<Member> targets = allMembers.stream()
                        .filter(m -> hasRole(m, mapping.fromRoleId()) && !m.getUser().isBot()
                                && conditionRoleIds.stream().allMatch(id -> hasRole(m, id)))
                        .toList();
                Role fromRole = guild.getRoleById(mapping.fromRoleId());
                List<Role> toRoles = mapping.toRoleIdsOrEmpty().stream()
                        .map(guild::getRoleById).filter(Objects::nonNull).toList();
                String toNames = toRoles.stream().map(Role::getName).collect(Collectors.joining(", "));
                String conditionNames = conditionRoleIds.stream()
                        .map(id -> { Role r = guild.getRoleById(id); return r != null ? r.getName() : id; })
                        .collect(Collectors.joining("+"));
                String conditionStr = conditionNames.isEmpty() ? "" : " [if has " + conditionNames + "]";
                String keepStr = mapping.isKeepFromRole() ? " (keep from)" : "";
                int pct = rescale(70 + (int) Math.round((i / (double) roleMappings.size()) * 25), rangeStart, rangeEnd);
                tracker.save(pct, "@" + (fromRole != null ? fromRole.getName() : mapping.fromRoleId()) + conditionStr
                        + keepStr + " → " + toNames + " (" + targets.size() + " members)");
                for (Member member : targets) {
                    try {
                        for (Role role : toRoles) {
                            guild.addRoleToMember(member, role).complete();
                            semesterRoleMigrationRepository.save(new SemesterRoleMigration(
                                    guild.getId(), migrationId, stepIndex, stepLabel, member.getId(), mapping.fromRoleId(),
                                    role.getId(), mapping.isKeepFromRole(), LocalDateTime.now()));
                        }
                        if (!mapping.isKeepFromRole() && fromRole != null) {
                            guild.removeRoleFromMember(member, fromRole).complete();
                        }
                        rolesProcessed++;
                    } catch (Exception e) {
                        recordFailure(mappingFailureReasons, e);
                    }
                }
            }
            int rolesFailed = mappingFailureReasons.values().stream().mapToInt(Integer::intValue).sum();
            roleMappingsDone = rolesFailed == 0;
            if (roleMappingsDone) {
                tracker.completeStep(stepPrefix + "roleMappings");
            }
            tracker.save(rescale(95, rangeStart, rangeEnd), "Roles: " + rolesProcessed + " switched"
                    + (rolesFailed > 0 ? ", " + rolesFailed + " failed: " + summarizeFailures(mappingFailureReasons) : ""));
        } else {
            tracker.completeStep(stepPrefix + "roleMappings");
            tracker.save(rescale(95, rangeStart, rangeEnd), "No role mappings — skipped");
            roleMappingsDone = true;
        }

        boolean cleanupDone;
        List<String> semesterRoles = oldSem.semesterRolesOrEmpty();
        if (tracker.hasCompleted(stepPrefix + "cleanupRoles")) {
            tracker.save(rescale(98, rangeStart, rangeEnd), "Semester role cleanup already completed — skipped");
            cleanupDone = true;
        } else if (!semesterRoles.isEmpty()) {
            tracker.save(rescale(96, rangeStart, rangeEnd), "Removing " + semesterRoles.size() + " semester role(s) from " + oldName + " members...");
            int removed = 0;
            int failed = 0;
            try {
                Map<String, Integer> failureReasons = new LinkedHashMap<>();
                for (String roleId : semesterRoles) {
                    Role role = guild.getRoleById(roleId);
                    List<Member> targets = allMembers.stream().filter(m -> hasRole(m, roleId) && !m.getUser().isBot()).toList();
                    if (!targets.isEmpty()) {
                        tracker.save(rescale(96, rangeStart, rangeEnd), "Clearing @" + (role != null ? role.getName() : roleId)
                                + " from " + targets.size() + " member(s)...");
                        for (Member member : targets) {
                            try {
                                guild.removeRoleFromMember(member, role).complete();
                                semesterRoleMigrationRepository.save(new SemesterRoleMigration(
                                        guild.getId(), migrationId, stepIndex, stepLabel, member.getId(), roleId, null, false, LocalDateTime.now()));
                                removed++;
                            } catch (Exception e) {
                                failed++;
                                recordFailure(failureReasons, e);
                            }
                        }
                    }
                }
                cleanupDone = failed == 0;
                if (cleanupDone) {
                    tracker.completeStep(stepPrefix + "cleanupRoles");
                }
                tracker.save(rescale(98, rangeStart, rangeEnd), "Semester roles cleared: " + removed + " removed"
                        + (failed > 0 ? ", " + failed + " failed: " + summarizeFailures(failureReasons) : ""));
            } catch (Exception e) {
                tracker.save(rescale(98, rangeStart, rangeEnd), "[ERROR] Could not clear semester roles: " + e.getMessage());
                cleanupDone = false;
            }
        } else {
            tracker.completeStep(stepPrefix + "cleanupRoles");
            tracker.save(rescale(98, rangeStart, rangeEnd), "No semester roles — skipped");
            cleanupDone = true;
        }

        return hideDone && showDone && roleMappingsDone && cleanupDone;
    }

    /**
     * Forces one plan's plan-wide list of individually-targeted channels (outside any category, each
     * with its own fixed @everyone View Channel state) - e.g. showing "❄️pv-predmety-roles" and
     * hiding "☀️pv-predmety-roles" once the whole plan finishes. Called once per plan run (see
     * {@link SemesterPlanService#runPlan}), after every step - not per step, since these channels
     * don't belong to any one step. Logged to the same rollback ledger as category visibility (see
     * {@link SemesterVisibilityMigration}), tagged with {@code stepIndex} one past the last real step
     * so it sections separately in the rollback UI, only once the whole batch applies error-free.
     */
    boolean applyAdditionalChannels(
            Guild guild, List<AdditionalChannel> additionalChannels, SemesterProgressTracker tracker,
            String stepPrefix, String migrationId, int stepIndex, int rangeStart, int rangeEnd) {
        if (tracker.hasCompleted(stepPrefix + "additionalChannels")) {
            tracker.save(rescale(50, rangeStart, rangeEnd), "Additional channels already completed — skipped");
            return true;
        }
        if (additionalChannels.isEmpty()) {
            tracker.completeStep(stepPrefix + "additionalChannels");
            return true;
        }
        tracker.save(rescale(10, rangeStart, rangeEnd), "Applying " + additionalChannels.size() + " additional channel(s)...");
        SemesterVisibilityService.Result r;
        try {
            r = semesterVisibilityService.applyChannels(guild, additionalChannels);
        } catch (Exception e) {
            tracker.save(rescale(50, rangeStart, rangeEnd), "[WARN] Error applying additional channels: " + e.getMessage());
            return false;
        }
        for (String line : r.logs()) {
            if (line.contains("ERROR") || line.contains("WARNING")) {
                tracker.save(rescale(50, rangeStart, rangeEnd), "[WARN] " + line);
            }
        }
        boolean done = r.errors() == 0;
        if (done) {
            tracker.completeStep(stepPrefix + "additionalChannels");
            logChannelVisibilityMigration(guild, migrationId, stepIndex, "Additional channels", additionalChannels);
        }
        tracker.save(rescale(90, rangeStart, rangeEnd), "Additional channels: " + r.channelsUpdated() + " updated"
                + (r.errors() > 0 ? ", " + r.errors() + " failed" : ""));
        return done;
    }

    private int rescale(int p, int rangeStart, int rangeEnd) {
        return rangeStart + (int) Math.round(p * (rangeEnd - rangeStart) / 100.0);
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private boolean hasRole(Member member, String roleId) {
        return member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
    }

    /**
     * Records one revertible row per category actually touched by a visibility sub-step - only
     * called once that sub-step's whole batch came back error-free, so every category in
     * {@code categoryIds} is known to have applied successfully.
     */
    private void logVisibilityMigration(
            Guild guild, String migrationId, int stepIndex, String stepLabel, List<String> categoryIds,
            String direction, boolean everyoneViewChannel) {
        for (String categoryId : categoryIds) {
            Category category = guild.getCategoryById(categoryId);
            semesterVisibilityMigrationRepository.save(new SemesterVisibilityMigration(
                    guild.getId(), migrationId, stepIndex, stepLabel, categoryId, category != null ? category.getName() : null,
                    direction, everyoneViewChannel, false, LocalDateTime.now()));
        }
    }

    /** Same as {@link #logVisibilityMigration}, but for plan-wide additional channels - each entry
     * has its own visible/everyoneViewChannel rather than one shared direction for the whole list. */
    private void logChannelVisibilityMigration(
            Guild guild, String migrationId, int stepIndex, String stepLabel, List<AdditionalChannel> channels) {
        for (AdditionalChannel entry : channels) {
            semesterVisibilityMigrationRepository.save(new SemesterVisibilityMigration(
                    guild.getId(), migrationId, stepIndex, stepLabel, entry.channelId(), entry.channelName(),
                    entry.isVisible() ? SemesterVisibilityMigration.DIRECTION_SHOW : SemesterVisibilityMigration.DIRECTION_HIDE,
                    entry.isEveryoneViewChannel(), true, LocalDateTime.now()));
        }
    }

    private void logAudit(String actorId, String actorName, Guild guild, String action, Map<String, Object> details) {
        dashboardAuditLogger.log(actorId, actorName, guild, action, details);
    }

    private void postRecap(Guild guild, String recapChannelId, String content, List<String> logs, String filename) {
        recapChannelPoster.post(guild, recapChannelId, content, logs, filename);
    }

    /**
     * Records why a single member's role change failed (missing permission, role hierarchy, ...)
     * instead of the old behavior of just incrementing a bare counter with no explanation - so a
     * "3 failed" result on the dashboard can actually be diagnosed without digging through logs.
     */
    private void recordFailure(Map<String, Integer> reasons, Exception e) {
        reasons.merge(moderationService.describeFailure(e), 1, Integer::sum);
    }

    private String summarizeFailures(Map<String, Integer> reasons) {
        return reasons.entrySet().stream()
                .map(entry -> entry.getKey() + " (x" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    /** Called from SemesterPlanService right before a genuinely new (non-resume) plan run starts. */
    void resetSubjectRoleSemester(String guildId) {
        subjectRoleService.resetSemester(guildId);
    }

    public SemesterOperationState status(String guildId, String operation) {
        String prefix = switch (operation) {
            case "setup" -> "semester_setup_log_";
            case "rollback" -> "switchsemester_rollback_log_";
            default -> "switchplan_log_";
        };
        return adminSettingsService.get(prefix + guildId, SemesterOperationState.class, SemesterOperationState.idle());
    }
}
