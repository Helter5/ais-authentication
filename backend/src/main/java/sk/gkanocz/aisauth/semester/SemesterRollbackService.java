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
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Undoes a Setup/Plan run recorded by {@link SemesterOperationService} / {@link SemesterPlanService}.
 * Every revertible unit from the run - each role-mapping/cleanup group, each category visibility
 * change, and (for a Plan run) the plan-path position pointer - can be selected independently, and
 * is sectioned by which step of the run it came from so a multi-step Plan can be reverted one step
 * at a time. There is no "full" vs "selective" mode; a caller that wants the old "undo everything"
 * behavior just selects every group, every visibility row, and revertPlanPosition = true.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemesterRollbackService {

    private final AdminSettingsService adminSettingsService;
    private final SemesterRoleMigrationRepository semesterRoleMigrationRepository;
    private final SemesterVisibilityMigrationRepository semesterVisibilityMigrationRepository;
    private final SemesterSwitchHistoryRepository semesterSwitchHistoryRepository;
    private final SemesterOperationService semesterOperationService;
    private final SemesterVisibilityService semesterVisibilityService;
    private final DashboardAuditLogger dashboardAuditLogger;
    private final RecapChannelPoster recapChannelPoster;
    private final DiscordModerationService moderationService;
    private final LogRoutingService logRoutingService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public enum RevertStatus { NONE, PARTIAL, FULL }

    public record MigrationGroup(
            String groupKey, String roleFromId, String roleToId, boolean keptFromRole,
            int totalMembers, int remainingMembers, boolean rolledBack) {
    }

    public record VisibilityRow(
            Long id, String categoryId, String categoryName, String direction, boolean everyoneViewChannel,
            boolean rolledBack, boolean isChannel) {
    }

    public record StepDetail(int stepIndex, String stepLabel, List<MigrationGroup> roleGroups, List<VisibilityRow> visibilityRows) {
    }

    public record MigrationDetail(List<StepDetail> steps) {
    }

    public record HistoryView(
            Long id, String migrationId, String operationType, String label, String actorId, String actorName,
            java.time.LocalDateTime createdAt, boolean canRevertPosition, boolean positionReverted, RevertStatus status,
            String rolledBackByActorName) {
    }

    public List<HistoryView> history(String guildId) {
        return semesterSwitchHistoryRepository.findByGuildIdOrderByCreatedAtDesc(guildId).stream()
                .map(h -> new HistoryView(
                        h.getId(), h.getMigrationId(), h.getOperationType(), label(h), h.getActorId(), h.getActorName(),
                        h.getCreatedAt(), SemesterSwitchHistory.TYPE_PLAN.equals(h.getOperationType()) && h.getFromPlanId() != null,
                        h.isPositionReverted(), computeStatus(guildId, h), h.getRolledBackByActorName()))
                .toList();
    }

    /** Every step of a run, each with its role/cleanup groups and per-category visibility rows. */
    public MigrationDetail migrationDetail(String guildId, String migrationId) {
        List<SemesterRoleMigration> roleRows = semesterRoleMigrationRepository.findByGuildIdAndMigrationId(guildId, migrationId);
        List<SemesterVisibilityMigration> visRows = semesterVisibilityMigrationRepository.findByGuildIdAndMigrationId(guildId, migrationId);

        Map<Integer, String> labelsByStep = new TreeMap<>();
        roleRows.forEach(r -> labelsByStep.putIfAbsent(r.getStepIndex(), r.getStepLabel()));
        visRows.forEach(v -> labelsByStep.putIfAbsent(v.getStepIndex(), v.getStepLabel()));

        List<StepDetail> steps = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : labelsByStep.entrySet()) {
            int stepIndex = entry.getKey();
            Map<String, List<SemesterRoleMigration>> grouped = roleRows.stream()
                    .filter(r -> r.getStepIndex() == stepIndex)
                    .collect(Collectors.groupingBy(this::groupKey, LinkedHashMap::new, Collectors.toList()));

            List<MigrationGroup> roleGroups = new ArrayList<>();
            for (List<SemesterRoleMigration> group : grouped.values()) {
                SemesterRoleMigration first = group.get(0);
                long remaining = group.stream().filter(r -> !r.isRolledBack()).count();
                roleGroups.add(new MigrationGroup(
                        groupKey(first), first.getRoleFromId(), first.getRoleToId(), first.isKeptFromRole(),
                        group.size(), (int) remaining, remaining == 0));
            }

            List<VisibilityRow> visibilityRows = visRows.stream()
                    .filter(v -> v.getStepIndex() == stepIndex)
                    .map(v -> new VisibilityRow(
                            v.getId(), v.getCategoryId(), v.getCategoryName(), v.getDirection(), v.isEveryoneViewChannel(),
                            v.isRolledBack(), v.isChannel()))
                    .toList();

            steps.add(new StepDetail(stepIndex, entry.getValue(), roleGroups, visibilityRows));
        }
        return new MigrationDetail(steps);
    }

    public SemesterOperationState status(String guildId) {
        return semesterOperationService.status(guildId, "rollback");
    }

    public void startRollback(
            Guild guild, String migrationId, List<String> roleGroupKeys, List<Long> visibilityIds,
            boolean revertPlanPosition, String actorId, String actorName) {
        String guildId = guild.getId();
        SemesterSwitchHistory history = semesterSwitchHistoryRepository.findByGuildIdAndMigrationId(guildId, migrationId)
                .orElseThrow(() -> InvalidRequestException.withMessage("No run found for migration \"" + migrationId + "\"."));
        if (computeStatus(guildId, history) == RevertStatus.FULL) {
            throw InvalidRequestException.withMessage("This run was already fully rolled back.");
        }
        List<String> groupKeys = roleGroupKeys == null ? List.of() : roleGroupKeys;
        List<Long> visIds = visibilityIds == null ? List.of() : visibilityIds;
        if (revertPlanPosition && (!SemesterSwitchHistory.TYPE_PLAN.equals(history.getOperationType()) || history.getFromPlanId() == null)) {
            throw InvalidRequestException.withMessage("This run has no plan position to revert.");
        }
        if (groupKeys.isEmpty() && visIds.isEmpty() && !revertPlanPosition) {
            throw InvalidRequestException.withMessage("Select at least one thing to roll back.");
        }

        String progressKey = "switchsemester_rollback_log_" + guildId;
        semesterOperationService.assertNoOtherOperationRunning(guildId, progressKey);

        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, progressKey, false, null, Map.of("migrationId", migrationId), "rollback");
        tracker.save(0, "Rollback of " + label(history) + " starting...");

        executor.submit(() -> runRollback(guild, history, groupKeys, visIds, revertPlanPosition, tracker, actorId, actorName));
    }

    private void runRollback(
            Guild guild, SemesterSwitchHistory history, List<String> roleGroupKeys, List<Long> visibilityIds,
            boolean revertPlanPosition, SemesterProgressTracker tracker, String actorId, String actorName) {
        String guildId = guild.getId();
        try {
            List<Permission> requiredPerms = new ArrayList<>();
            if (!roleGroupKeys.isEmpty()) {
                requiredPerms.add(Permission.MANAGE_ROLES);
            }
            if (!visibilityIds.isEmpty()) {
                requiredPerms.add(Permission.MANAGE_CHANNEL);
            }
            List<String> missingPerms = BotPermissionChecker.missingPermissions(guild, requiredPerms.toArray(new Permission[0]));
            if (!missingPerms.isEmpty()) {
                tracker.save(100, "[ERROR] Bot is missing required permissions: " + String.join(", ", missingPerms)
                        + ". Rollback aborted.", "failed");
                return;
            }

            int reverted = revertRoleGroups(guild, history, roleGroupKeys, tracker);
            int visReverted = revertVisibilityRows(guild, history, visibilityIds, tracker);

            if (revertPlanPosition) {
                tracker.save(90, "Restoring plan position to " + history.getFromPlanId() + "...");
                semesterOperationService.setCurrentPlan(guildId, history.getFromPlanId());
                history.markPositionReverted();
            }

            RevertStatus finalStatus = computeStatus(guildId, history);
            history.setRolledBack(finalStatus == RevertStatus.FULL);
            history.recordRollbackActor(actorId, actorName);
            semesterSwitchHistoryRepository.save(history);

            tracker.save(100, "Rollback complete: " + reverted + " role change(s), " + visReverted + " categor"
                    + (visReverted == 1 ? "y" : "ies") + " reverted" + (revertPlanPosition ? ", plan position restored." : "."), "success");

            // roleGroupKeys/visibilityIds are internal encoded re-invocation keys (stepIndex|roleId|...),
            // not human-meaningful - the counts below already say what a person needs to know.
            Map<String, Object> auditDetails = new LinkedHashMap<>();
            auditDetails.put("migrationId", history.getMigrationId());
            auditDetails.put("run", label(history));
            auditDetails.put("revertPlanPosition", revertPlanPosition);
            auditDetails.put("reverted", reverted);
            auditDetails.put("visibilityReverted", visReverted);
            auditDetails.put("status", finalStatus.name());
            dashboardAuditLogger.log(actorId, actorName, guild, "Rolled back semester run", auditDetails);

            int revertedCount = reverted;
            int visRevertedCount = visReverted;
            logRoutingService.channelIdFor(guildId, LogEventType.SEMESTER_RECAP).ifPresent(recapChannelId ->
                    recapChannelPoster.post(guild, recapChannelId,
                            "**Semester Rollback Complete** — `" + label(history) + "`\n"
                                    + revertedCount + " member role change(s), " + visRevertedCount + " categor"
                                    + (visRevertedCount == 1 ? "y" : "ies") + " reverted",
                            tracker.logsSnapshot(), "semester-rollback-report.txt"));
        } catch (Exception e) {
            tracker.save(100, "[ERROR] Fatal error: " + e.getMessage(), "failed");
            log.error("[switchsemester/rollback] Error", e);
        }
    }

    private int revertRoleGroups(Guild guild, SemesterSwitchHistory history, List<String> roleGroupKeys, SemesterProgressTracker tracker) {
        if (roleGroupKeys.isEmpty()) {
            return 0;
        }
        List<SemesterRoleMigration> activeRows = semesterRoleMigrationRepository
                .findByGuildIdAndMigrationIdAndRolledBackFalse(guild.getId(), history.getMigrationId());
        List<SemesterRoleMigration> targetRows = activeRows.stream()
                .filter(r -> roleGroupKeys.contains(groupKey(r)))
                .toList();

        int reverted = 0;
        Map<String, Integer> failureReasons = new LinkedHashMap<>();
        tracker.save(10, "Reverting " + targetRows.size() + " member role change(s)...");
        for (int i = 0; i < targetRows.size(); i++) {
            SemesterRoleMigration row = targetRows.get(i);
            Member member = guild.getMemberById(row.getDiscordId());
            if (member != null) {
                try {
                    if (row.getRoleToId() != null) {
                        Role toRole = guild.getRoleById(row.getRoleToId());
                        if (toRole != null) {
                            guild.removeRoleFromMember(member, toRole).complete();
                        }
                    }
                    if (!row.isKeptFromRole()) {
                        Role fromRole = guild.getRoleById(row.getRoleFromId());
                        if (fromRole != null) {
                            guild.addRoleToMember(member, fromRole).complete();
                        }
                    }
                    reverted++;
                } catch (Exception e) {
                    failureReasons.merge(moderationService.describeFailure(e), 1, Integer::sum);
                }
            }
            row.markRolledBack();
            semesterRoleMigrationRepository.save(row);
            if ((i + 1) % 25 == 0 || i + 1 == targetRows.size()) {
                int pct = 10 + (int) Math.round(((i + 1) / (double) Math.max(1, targetRows.size())) * 40);
                tracker.save(pct, "Reverted " + reverted + "/" + targetRows.size() + " member role change(s)...");
            }
        }
        if (!failureReasons.isEmpty()) {
            tracker.save(50, "[WARN] Some role reverts failed: " + summarizeFailures(failureReasons));
        }
        return reverted;
    }

    private int revertVisibilityRows(Guild guild, SemesterSwitchHistory history, List<Long> visibilityIds, SemesterProgressTracker tracker) {
        if (visibilityIds.isEmpty()) {
            return 0;
        }
        String guildId = guild.getId();
        int reverted = 0;
        tracker.save(55, "Reverting " + visibilityIds.size() + " categor" + (visibilityIds.size() == 1 ? "y" : "ies") + "...");
        for (Long id : visibilityIds) {
            SemesterVisibilityMigration row = semesterVisibilityMigrationRepository.findById(id).orElse(null);
            if (row == null || row.isRolledBack()
                    || !row.getGuildId().equals(guildId) || !row.getMigrationId().equals(history.getMigrationId())) {
                continue;
            }
            try {
                if (row.isChannel()) {
                    // A plan-wide additional channel - applyChannels, not apply(), since a plain
                    // channel isn't a category with children to cascade into. visible and
                    // everyoneViewChannel are independent axes here (see AdditionalChannel), so
                    // reverting flips each one back on its own, not one gating the other.
                    SwitchSemesterSettings.AdditionalChannel revertEntry = new SwitchSemesterSettings.AdditionalChannel(
                            row.getCategoryId(), row.getCategoryName(), row.isHide(), !row.isEveryoneViewChannel());
                    semesterVisibilityService.applyChannels(guild, List.of(revertEntry));
                } else if (row.isHide()) {
                    // Was hidden as the "from" semester's category - show it again with the everyone
                    // View Channel value snapshotted at run time.
                    semesterVisibilityService.apply(guild, List.of(row.getCategoryId()), true, row.isEveryoneViewChannel());
                } else {
                    // Was shown as the "to" semester's category - hide it again.
                    semesterVisibilityService.apply(guild, List.of(row.getCategoryId()), false, false);
                }
                reverted++;
            } catch (Exception e) {
                tracker.save(80, "[WARN] Error reverting " + (row.isChannel() ? "channel " : "category ")
                        + (row.getCategoryName() != null ? row.getCategoryName() : row.getCategoryId()) + ": " + e.getMessage());
            }
            row.markRolledBack();
            semesterVisibilityMigrationRepository.save(row);
        }
        return reverted;
    }

    private RevertStatus computeStatus(String guildId, SemesterSwitchHistory history) {
        String migrationId = history.getMigrationId();
        long totalRole = semesterRoleMigrationRepository.countByGuildIdAndMigrationId(guildId, migrationId);
        long doneRole = semesterRoleMigrationRepository.countByGuildIdAndMigrationIdAndRolledBackTrue(guildId, migrationId);
        long totalVis = semesterVisibilityMigrationRepository.countByGuildIdAndMigrationId(guildId, migrationId);
        long doneVis = semesterVisibilityMigrationRepository.countByGuildIdAndMigrationIdAndRolledBackTrue(guildId, migrationId);
        boolean needsPosition = SemesterSwitchHistory.TYPE_PLAN.equals(history.getOperationType()) && history.getFromPlanId() != null;

        boolean allDone = totalRole == doneRole && totalVis == doneVis && (!needsPosition || history.isPositionReverted());
        if (allDone) {
            return RevertStatus.FULL;
        }
        boolean anyDone = doneRole > 0 || doneVis > 0 || history.isPositionReverted();
        return anyDone ? RevertStatus.PARTIAL : RevertStatus.NONE;
    }

    private String label(SemesterSwitchHistory history) {
        return SemesterSwitchHistory.TYPE_SETUP.equals(history.getOperationType())
                ? "Setup " + history.getNewName()
                : history.getPlanName();
    }

    private String groupKey(SemesterRoleMigration row) {
        return row.getStepIndex() + "|" + row.getRoleFromId() + "|" + row.getRoleToId() + "|" + row.isKeptFromRole();
    }

    private String summarizeFailures(Map<String, Integer> reasons) {
        return reasons.entrySet().stream()
                .map(entry -> entry.getKey() + " (x" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
    }
}
