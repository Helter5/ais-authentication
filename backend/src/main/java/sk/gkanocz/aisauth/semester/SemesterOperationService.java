package sk.gkanocz.aisauth.semester;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;
import sk.gkanocz.aisauth.discordbot.BotPermissionChecker;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.DiscordModerationService;
import sk.gkanocz.aisauth.discordbot.RecapChannelPoster;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ── Setup ────────────────────────────────────────────────────────────────

    public void startSetup(Guild guild, String semesterName, boolean visible, boolean clearRoles, boolean resume,
                            String actorId, String actorName) {
        String guildId = guild.getId();
        String recapChannelId = logRoutingService.channelIdFor(guildId, LogEventType.SEMESTER_RECAP).orElse(null);
        if (recapChannelId == null) {
            throw InvalidRequestException.withMessage("Semester log channel not configured.");
        }

        String progressKey = "semester_setup_log_" + guildId;
        SemesterOperationState previous = adminSettingsService.get(progressKey, SemesterOperationState.class, null);
        if (previous != null && previous.running()) {
            throw SemesterOperationInProgressException.withMessage("Semester setup already in progress");
        }
        SemesterOperationState activeSwitch = adminSettingsService.get(
                "switchsemester_log_" + guildId, SemesterOperationState.class, null);
        if (activeSwitch != null && activeSwitch.running()) {
            throw SemesterOperationInProgressException.withMessage("A semester switch is already in progress");
        }

        SwitchSemesterSettings settings = adminSettingsService.get(
                SemesterController.configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
        SemesterDefinition sem = settings.find(semesterName);
        if (sem == null) {
            throw InvalidRequestException.withMessage("Semester \"" + semesterName + "\" not configured.");
        }

        boolean everyoneViewChannel = visible && sem.isEveryoneViewChannel();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("semesterName", semesterName);
        params.put("visible", visible);
        params.put("everyoneViewChannel", everyoneViewChannel);
        params.put("clearRoles", clearRoles);

        if (resume) {
            boolean canResume = previous != null && List.of("partial", "failed").contains(previous.status())
                    && params.equals(previous.params());
            if (!canResume) {
                throw InvalidRequestException.withMessage("There is no matching failed semester setup to resume.");
            }
        }

        SemesterProgressTracker tracker = new SemesterProgressTracker(adminSettingsService, progressKey, resume, previous, params, "setup");
        tracker.save(0, resume
                ? "Resuming setup: " + semesterName + " (" + tracker.completedCount() + " step"
                        + (tracker.completedCount() == 1 ? "" : "s") + " already complete)"
                : "Setup: " + semesterName + " — " + (visible ? "showing" : "hiding") + " channels, @everyone View Channel = "
                        + everyoneViewChannel + (clearRoles ? ", clearing subject roles" : ""));

        executor.submit(() -> runSetup(guild, sem, semesterName, visible, everyoneViewChannel, clearRoles,
                tracker, recapChannelId, actorId, actorName));
    }

    private void runSetup(
            Guild guild, SemesterDefinition sem, String semesterName, boolean visible, boolean everyoneViewChannel,
            boolean clearRoles, SemesterProgressTracker tracker, String recapChannelId, String actorId, String actorName) {
        try {
            List<Permission> requiredPerms = new ArrayList<>();
            if (!sem.categoryIdsOrEmpty().isEmpty()) {
                requiredPerms.add(Permission.MANAGE_CHANNEL);
            }
            if (clearRoles) {
                requiredPerms.add(Permission.MANAGE_ROLES);
            }
            List<String> missingPerms = BotPermissionChecker.missingPermissions(guild, requiredPerms.toArray(new Permission[0]));
            if (!missingPerms.isEmpty()) {
                tracker.save(100, "[ERROR] Bot is missing required permissions: " + String.join(", ", missingPerms)
                        + ". Setup aborted.", "failed");
                return;
            }

            int errorCount = 0;

            if (tracker.hasCompleted("visibility")) {
                tracker.save(60, "Channel visibility already completed — skipped");
            } else if (!sem.categoryIdsOrEmpty().isEmpty()) {
                int count = sem.categoryIdsOrEmpty().size();
                tracker.save(10, (visible ? "Showing " : "Hiding ") + count + " categor" + (count == 1 ? "y" : "ies") + "...");
                try {
                    SemesterVisibilityService.Result r =
                            semesterVisibilityService.apply(guild, sem.categoryIdsOrEmpty(), visible, everyoneViewChannel);
                    errorCount += r.errors();
                    for (String line : r.logs()) {
                        if (line.contains("ERROR") || line.contains("WARNING")) {
                            tracker.save(60, "[WARN] " + line);
                        }
                    }
                    if (r.errors() == 0) {
                        tracker.completeStep("visibility");
                    }
                    tracker.save(60, (visible ? "Shown " : "Hidden ") + r.channelsUpdated()
                            + " channels (" + r.categoriesUpdated() + " categories)");
                } catch (Exception e) {
                    errorCount++;
                    tracker.save(60, "[WARN] Error setting visibility: " + e.getMessage());
                }
            } else {
                tracker.completeStep("visibility");
                tracker.save(60, "No categories configured — skipped");
            }

            if (!clearRoles) {
                tracker.completeStep("cleanupRoles");
                tracker.save(95, "Subject role clear skipped");
            } else if (tracker.hasCompleted("cleanupRoles")) {
                tracker.save(95, "Subject role cleanup already completed — skipped");
            } else if (!sem.semesterRolesOrEmpty().isEmpty()) {
                tracker.save(65, "Clearing " + sem.semesterRolesOrEmpty().size() + " subject role(s) from all members...");
                try {
                    List<Member> allMembers = guild.loadMembers().get();
                    int removed = 0;
                    Map<String, Integer> failureReasons = new LinkedHashMap<>();
                    for (String roleId : sem.semesterRolesOrEmpty()) {
                        Role role = guild.getRoleById(roleId);
                        List<Member> targets = allMembers.stream().filter(m -> hasRole(m, roleId) && !m.getUser().isBot()).toList();
                        if (!targets.isEmpty()) {
                            tracker.save(65, "Clearing @" + (role != null ? role.getName() : roleId)
                                    + " from " + targets.size() + " member(s)...");
                            for (Member member : targets) {
                                try {
                                    guild.removeRoleFromMember(member, role).complete();
                                    removed++;
                                } catch (Exception e) {
                                    recordFailure(failureReasons, e);
                                }
                            }
                        }
                    }
                    int failed = failureReasons.values().stream().mapToInt(Integer::intValue).sum();
                    errorCount += failed;
                    if (failed == 0) {
                        tracker.completeStep("cleanupRoles");
                    }
                    tracker.save(95, "Subject roles cleared: " + removed + " removed"
                            + (failed > 0 ? ", " + failed + " failed: " + summarizeFailures(failureReasons) : ""));
                } catch (Exception e) {
                    errorCount++;
                    tracker.save(95, "[ERROR] Could not clear subject roles: " + e.getMessage());
                }
            } else {
                tracker.completeStep("cleanupRoles");
                tracker.save(95, "No subject roles configured — skipped");
            }

            List<String> incompleteSteps = Stream.of("visibility", "cleanupRoles").filter(s -> !tracker.hasCompleted(s)).toList();
            String finalStatus = incompleteSteps.isEmpty() ? "success" : "partial";
            tracker.save(100, incompleteSteps.isEmpty()
                    ? "Semester setup complete."
                    : "[WARN] Semester setup finished with unfinished step(s): " + String.join(", ", incompleteSteps)
                            + ". Use Resume to retry them.", finalStatus);

            logAudit(actorId, actorName, guild, "Ran semester setup", Map.of(
                    "semesterName", semesterName, "visible", visible, "everyoneViewChannel", everyoneViewChannel,
                    "clearRoles", clearRoles, "status", finalStatus, "errorCount", errorCount, "incompleteSteps", incompleteSteps));

            postRecap(guild, recapChannelId,
                    "**Semester Setup " + ("success".equals(finalStatus) ? "Complete" : "Finished With Errors") + "** — `"
                            + semesterName + "` channels " + (visible ? "shown" : "hidden")
                            + (clearRoles ? ", cleanup roles processed" : ""),
                    tracker.logsSnapshot(), "semester-setup-report.txt");
        } catch (Exception e) {
            tracker.save(100, "[ERROR] Fatal error: " + e.getMessage(), "failed");
            log.error("[setupsemester/run] Error", e);
        }
    }

    // ── Switch ───────────────────────────────────────────────────────────────

    public void startSwitch(Guild guild, String oldName, String newName, boolean resume, String actorId, String actorName) {
        String guildId = guild.getId();

        String progressKey = "switchsemester_log_" + guildId;
        SemesterOperationState previous = adminSettingsService.get(progressKey, SemesterOperationState.class, null);
        if (previous != null && previous.running()) {
            throw SemesterOperationInProgressException.withMessage("Semester switch already in progress");
        }
        SemesterOperationState activeSetup = adminSettingsService.get(
                "semester_setup_log_" + guildId, SemesterOperationState.class, null);
        if (activeSetup != null && activeSetup.running()) {
            throw SemesterOperationInProgressException.withMessage("A semester setup is already in progress");
        }

        SwitchSemesterSettings settings = adminSettingsService.get(
                SemesterController.configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
        SemesterDefinition oldSem = settings.find(oldName);
        SemesterDefinition newSem = settings.find(newName);
        if (oldSem == null) {
            throw InvalidRequestException.withMessage("Semester \"" + oldName + "\" not configured.");
        }
        if (newSem == null) {
            throw InvalidRequestException.withMessage("Semester \"" + newName + "\" not configured.");
        }
        if (!settings.transitionAllowed(oldName, newName)) {
            throw InvalidRequestException.withMessage(
                    "Transition \"" + oldName + "\" → \"" + newName + "\" is not in the allowed transitions list.");
        }

        boolean newEveryoneViewChannel = newSem.isEveryoneViewChannel();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("oldName", oldName);
        params.put("newName", newName);
        params.put("oldEveryoneViewChannel", false);
        params.put("newEveryoneViewChannel", newEveryoneViewChannel);

        if (resume) {
            boolean canResume = previous != null && List.of("partial", "failed").contains(previous.status())
                    && params.equals(previous.params());
            if (!canResume) {
                throw InvalidRequestException.withMessage("There is no matching failed semester switch to resume.");
            }
        }

        SemesterProgressTracker tracker = new SemesterProgressTracker(adminSettingsService, progressKey, resume, previous, params, "switch");
        tracker.save(0, resume
                ? "Resuming: " + oldName + " → " + newName + " (" + tracker.completedCount() + " step"
                        + (tracker.completedCount() == 1 ? "" : "s") + " already complete)"
                : "Starting: " + oldName + " → " + newName + " (@everyone View Channel = " + newEveryoneViewChannel + " for " + newName + ")");

        executor.submit(() -> runSwitch(guild, oldSem, newSem, oldName, newName, newEveryoneViewChannel, tracker, actorId, actorName));
    }

    private void runSwitch(
            Guild guild, SemesterDefinition oldSem, SemesterDefinition newSem, String oldName, String newName,
            boolean newEveryoneViewChannel, SemesterProgressTracker tracker, String actorId, String actorName) {
        try {
            List<String> missingPerms = BotPermissionChecker.missingPermissions(
                    guild, Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES);
            if (!missingPerms.isEmpty()) {
                tracker.save(100, "[ERROR] Bot is missing required permissions: " + String.join(", ", missingPerms)
                        + ". Semester switch aborted.", "failed");
                return;
            }

            int errorCount = 0;
            int catHidden = 0;
            int chanHidden = 0;
            int catShown = 0;
            int chanShown = 0;
            int rolesProcessed = 0;
            int rolesFailed = 0;

            if (tracker.hasCompleted("hideOld")) {
                tracker.save(35, "Hiding " + oldName + " already completed — skipped");
            } else if (!oldSem.categoryIdsOrEmpty().isEmpty()) {
                int count = oldSem.categoryIdsOrEmpty().size();
                tracker.save(5, "Hiding " + count + " categor" + (count == 1 ? "y" : "ies") + " for " + oldName + "...");
                try {
                    SemesterVisibilityService.Result r = semesterVisibilityService.apply(guild, oldSem.categoryIdsOrEmpty(), false, false);
                    catHidden = r.categoriesUpdated();
                    chanHidden = r.channelsUpdated();
                    errorCount += r.errors();
                    for (String line : r.logs()) {
                        if (line.contains("ERROR") || line.contains("WARNING")) {
                            tracker.save(35, "[WARN] " + line);
                        }
                    }
                    if (r.errors() == 0) {
                        tracker.completeStep("hideOld");
                    }
                    tracker.save(35, "Hidden " + r.channelsUpdated() + " channels (" + r.categoriesUpdated() + " categories)");
                } catch (Exception e) {
                    errorCount++;
                    tracker.save(35, "[WARN] Error hiding old semester: " + e.getMessage());
                }
            } else {
                tracker.completeStep("hideOld");
                tracker.save(35, "No categories for " + oldName + " — skipped");
            }

            if (tracker.hasCompleted("showNew")) {
                tracker.save(65, "Showing " + newName + " already completed — skipped");
            } else if (!newSem.categoryIdsOrEmpty().isEmpty()) {
                int count = newSem.categoryIdsOrEmpty().size();
                tracker.save(40, "Showing " + count + " categor" + (count == 1 ? "y" : "ies") + " for " + newName + "...");
                try {
                    SemesterVisibilityService.Result r =
                            semesterVisibilityService.apply(guild, newSem.categoryIdsOrEmpty(), true, newEveryoneViewChannel);
                    catShown = r.categoriesUpdated();
                    chanShown = r.channelsUpdated();
                    errorCount += r.errors();
                    for (String line : r.logs()) {
                        if (line.contains("ERROR") || line.contains("WARNING")) {
                            tracker.save(65, "[WARN] " + line);
                        }
                    }
                    if (r.errors() == 0) {
                        tracker.completeStep("showNew");
                    }
                    tracker.save(65, "Shown " + r.channelsUpdated() + " channels (" + r.categoriesUpdated() + " categories)");
                } catch (Exception e) {
                    errorCount++;
                    tracker.save(65, "[WARN] Error showing new semester: " + e.getMessage());
                }
            } else {
                tracker.completeStep("showNew");
                tracker.save(65, "No categories for " + newName + " — skipped");
            }

            boolean needsMemberFetch = (!tracker.hasCompleted("roleMappings") && !oldSem.roleMappingsOrEmpty().isEmpty())
                    || (!tracker.hasCompleted("cleanupRoles") && !oldSem.semesterRolesOrEmpty().isEmpty());
            List<Member> allMembers = needsMemberFetch ? guild.loadMembers().get() : null;

            List<SemesterDefinition.RoleMapping> roleMappings = oldSem.roleMappingsOrEmpty();
            if (tracker.hasCompleted("roleMappings")) {
                tracker.save(95, "Role mappings already completed — skipped");
            } else if (!roleMappings.isEmpty()) {
                tracker.save(70, "Applying " + roleMappings.size() + " role mapping(s)...");
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
                    int pct = 70 + (int) Math.round((i / (double) roleMappings.size()) * 25);
                    tracker.save(pct, "@" + (fromRole != null ? fromRole.getName() : mapping.fromRoleId()) + conditionStr
                            + keepStr + " → " + toNames + " (" + targets.size() + " members)");
                    for (Member member : targets) {
                        try {
                            for (Role role : toRoles) {
                                guild.addRoleToMember(member, role).complete();
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
                rolesFailed = mappingFailureReasons.values().stream().mapToInt(Integer::intValue).sum();
                errorCount += rolesFailed;
                if (rolesFailed == 0) {
                    tracker.completeStep("roleMappings");
                }
                tracker.save(95, "Roles: " + rolesProcessed + " switched"
                        + (rolesFailed > 0 ? ", " + rolesFailed + " failed: " + summarizeFailures(mappingFailureReasons) : ""));
            } else {
                tracker.completeStep("roleMappings");
                tracker.save(95, "No role mappings — skipped");
            }

            List<String> semesterRoles = oldSem.semesterRolesOrEmpty();
            if (tracker.hasCompleted("cleanupRoles")) {
                tracker.save(98, "Semester role cleanup already completed — skipped");
            } else if (!semesterRoles.isEmpty()) {
                tracker.save(96, "Removing " + semesterRoles.size() + " semester role(s) from " + oldName + " members...");
                try {
                    int removed = 0;
                    Map<String, Integer> failureReasons = new LinkedHashMap<>();
                    for (String roleId : semesterRoles) {
                        Role role = guild.getRoleById(roleId);
                        List<Member> targets = allMembers.stream().filter(m -> hasRole(m, roleId) && !m.getUser().isBot()).toList();
                        if (!targets.isEmpty()) {
                            tracker.save(96, "Clearing @" + (role != null ? role.getName() : roleId)
                                    + " from " + targets.size() + " member(s)...");
                            for (Member member : targets) {
                                try {
                                    guild.removeRoleFromMember(member, role).complete();
                                    removed++;
                                } catch (Exception e) {
                                    recordFailure(failureReasons, e);
                                }
                            }
                        }
                    }
                    int removeFailed = failureReasons.values().stream().mapToInt(Integer::intValue).sum();
                    errorCount += removeFailed;
                    if (removeFailed == 0) {
                        tracker.completeStep("cleanupRoles");
                    }
                    tracker.save(98, "Semester roles cleared: " + removed + " removed"
                            + (removeFailed > 0 ? ", " + removeFailed + " failed: " + summarizeFailures(failureReasons) : ""));
                } catch (Exception e) {
                    errorCount++;
                    tracker.save(98, "[ERROR] Could not clear semester roles: " + e.getMessage());
                }
            } else {
                tracker.completeStep("cleanupRoles");
                tracker.save(98, "No semester roles — skipped");
            }

            List<String> incompleteSteps = Stream.of("hideOld", "showNew", "roleMappings", "cleanupRoles")
                    .filter(s -> !tracker.hasCompleted(s)).toList();
            String finalStatus = incompleteSteps.isEmpty() ? "success" : "partial";
            tracker.save(100, incompleteSteps.isEmpty()
                    ? "Semester switch complete."
                    : "[WARN] Semester switch finished with unfinished step(s): " + String.join(", ", incompleteSteps)
                            + ". Use Resume to retry them.", finalStatus);

            Map<String, Object> auditDetails = new LinkedHashMap<>();
            auditDetails.put("oldName", oldName);
            auditDetails.put("newName", newName);
            auditDetails.put("newEveryoneViewChannel", newEveryoneViewChannel);
            auditDetails.put("catHidden", catHidden);
            auditDetails.put("chanHidden", chanHidden);
            auditDetails.put("catShown", catShown);
            auditDetails.put("chanShown", chanShown);
            auditDetails.put("rolesProcessed", rolesProcessed);
            auditDetails.put("rolesFailed", rolesFailed);
            auditDetails.put("status", finalStatus);
            auditDetails.put("errorCount", errorCount);
            auditDetails.put("incompleteSteps", incompleteSteps);
            logAudit(actorId, actorName, guild, "Ran semester switch", auditDetails);

            String recapChannelId = logRoutingService.channelIdFor(guild.getId(), LogEventType.SEMESTER_RECAP).orElse(null);
            if (recapChannelId != null) {
                postRecap(guild, recapChannelId,
                        "**Semester Switch " + ("success".equals(finalStatus) ? "Complete" : "Finished With Errors") + "** — `"
                                + oldName + "` → `" + newName + "`\n" + chanHidden + " channels hidden, " + chanShown
                                + " shown, " + rolesProcessed + " roles switched",
                        tracker.logsSnapshot(), "semester-switch-report.txt");
            }
        } catch (Exception e) {
            tracker.save(100, "[ERROR] Fatal error: " + e.getMessage(), "failed");
            log.error("[switchsemester/run] Error", e);
        }
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private boolean hasRole(Member member, String roleId) {
        return member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId));
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

    public SemesterOperationState status(String guildId, String operation) {
        String key = ("setup".equals(operation) ? "semester_setup_log_" : "switchsemester_log_") + guildId;
        return adminSettingsService.get(key, SemesterOperationState.class, SemesterOperationState.idle());
    }
}
