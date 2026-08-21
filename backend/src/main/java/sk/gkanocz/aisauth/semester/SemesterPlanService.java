package sk.gkanocz.aisauth.semester;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.stereotype.Service;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.RecapChannelPoster;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs a named {@link SwitchSemesterSettings.SwitchPlan} - a bundle of switch/setup steps executed
 * together as one admin-triggered action (e.g. "Switch ZS na LS" moving three year-cohorts at once,
 * or "Switch LS na ZS" bundling a fresh cohort's Setup alongside two switches). One tracker/history
 * entry/migrationId per plan run; each step gets its own tracker-key namespace and its own
 * migration-row stepIndex/stepLabel so a multi-step run can be resumed or rolled back per step - see
 * {@link SemesterOperationService#executeSwitchStep} / {@code executeSetupStep} for the actual work,
 * and {@link SemesterRollbackService} for reverting it afterward.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemesterPlanService {

    private final AdminSettingsService adminSettingsService;
    private final SemesterOperationService semesterOperationService;
    private final SemesterSwitchHistoryRepository semesterSwitchHistoryRepository;
    private final LogRoutingService logRoutingService;
    private final DashboardAuditLogger dashboardAuditLogger;
    private final RecapChannelPoster recapChannelPoster;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void startPlan(Guild guild, String planId, boolean resume, String actorId, String actorName) {
        String guildId = guild.getId();
        if (logRoutingService.channelIdFor(guildId, LogEventType.SEMESTER_RECAP).isEmpty()) {
            throw InvalidRequestException.withMessage("Semester log channel not configured.");
        }

        String progressKey = "switchplan_log_" + guildId;
        SemesterProgressTracker tracker;
        SwitchSemesterSettings settings;
        SwitchSemesterSettings.SwitchPlan plan;
        String migrationId;
        String fromPlanId;

        synchronized (semesterOperationService.lockFor(guildId)) {
            semesterOperationService.assertNoOtherOperationRunning(guildId, progressKey);
            SemesterOperationState previous = adminSettingsService.get(progressKey, SemesterOperationState.class, null);

            settings = adminSettingsService.get(
                    SemesterController.configsKey(guildId), SwitchSemesterSettings.class, SwitchSemesterSettings.empty());
            plan = settings.findPlan(planId);
            if (plan == null) {
                throw InvalidRequestException.withMessage("Plan \"" + planId + "\" not configured.");
            }
            if (plan.stepsOrEmpty().isEmpty()) {
                throw InvalidRequestException.withMessage("Plan \"" + plan.name() + "\" has no steps configured.");
            }
            // Every step's *resulting* semester (a switch step's "to", a setup step's "semester")
            // should agree on one WINTER/SUMMER type - that's what makes "the guild is currently in
            // Winter" a meaningful, single fact once this plan finishes. Skipped for any pair/step
            // still missing a configured type, so rollout doesn't retroactively break existing plans.
            String planType = null;
            for (SwitchSemesterSettings.SwitchPlanStep step : plan.stepsOrEmpty()) {
                String resultType;
                if (step.isSwitch()) {
                    SemesterDefinition fromSem = settings.find(step.from());
                    SemesterDefinition toSem = settings.find(step.to());
                    if (fromSem == null) {
                        throw InvalidRequestException.withMessage("Semester \"" + step.from() + "\" not configured.");
                    }
                    if (toSem == null) {
                        throw InvalidRequestException.withMessage("Semester \"" + step.to() + "\" not configured.");
                    }
                    if (fromSem.semesterType() != null && fromSem.semesterType().equals(toSem.semesterType())) {
                        throw InvalidRequestException.withMessage(
                                "Switch step \"" + step.label() + "\" would switch " + fromSem.semesterType()
                                        + " to " + toSem.semesterType() + " - a switch must alternate semester type.");
                    }
                    resultType = toSem.semesterType();
                } else if (step.isSetup()) {
                    SemesterDefinition sem = settings.find(step.semester());
                    if (sem == null) {
                        throw InvalidRequestException.withMessage("Semester \"" + step.semester() + "\" not configured.");
                    }
                    // A hide+clearRoles setup step archives an outgoing cohort (e.g. graduates
                    // finishing their last semester) rather than advancing the guild into a new
                    // semester state - it doesn't represent "the guild is now in X", so its own
                    // config's type (which may legitimately be the opposite of the rest of the plan,
                    // e.g. a "ls" cohort's closing semester inside an otherwise all-Winter plan)
                    // shouldn't have to agree with everything else in the plan.
                    boolean isArchival = !step.isVisible() && step.isClearRoles();
                    resultType = isArchival ? null : sem.semesterType();
                } else {
                    throw InvalidRequestException.withMessage("Unknown plan step type \"" + step.type() + "\".");
                }
                if (resultType != null) {
                    if (planType == null) {
                        planType = resultType;
                    } else if (!planType.equals(resultType)) {
                        throw InvalidRequestException.withMessage(
                                "Plan \"" + plan.name() + "\" mixes semester types across its steps - all steps must result in the same type.");
                    }
                }
            }

            fromPlanId = semesterOperationService.currentPlan(guildId);
            if (!resume) {
                // Blocks the actual bug this guard exists for: running a plan out of its configured
                // cycle order. A resume re-runs an already-guarded plan, so it's exempt; a guild that
                // has never completed a plan has no tracked position yet, so it's exempt too.
                String expectedNext = settings.nextPlanId(fromPlanId);
                if (fromPlanId != null && (expectedNext == null || !expectedNext.equals(planId))) {
                    throw InvalidRequestException.withMessage(
                            "\"" + plan.name() + "\" is not the next step in the configured plan path.");
                }
            }

            migrationId = resume && previous != null && previous.params() != null && previous.params().get("migrationId") != null
                    ? String.valueOf(previous.params().get("migrationId"))
                    : UUID.randomUUID().toString();

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("planId", planId);
            params.put("migrationId", migrationId);

            if (resume) {
                boolean canResume = previous != null && List.of("partial", "failed").contains(previous.status())
                        && params.equals(previous.params());
                if (!canResume) {
                    throw InvalidRequestException.withMessage("There is no matching failed plan run to resume.");
                }
            } else {
                semesterSwitchHistoryRepository.save(SemesterSwitchHistory.forPlan(
                        guildId, migrationId, planId, plan.name(), fromPlanId, actorId, actorName, LocalDateTime.now()));
            }

            tracker = new SemesterProgressTracker(adminSettingsService, progressKey, resume, previous, params, "plan");
            tracker.save(0, resume
                    ? "Resuming plan: " + plan.name() + " (" + tracker.completedCount() + " step"
                            + (tracker.completedCount() == 1 ? "" : "s") + " already complete)"
                    : "Starting plan: " + plan.name() + " (" + plan.stepsOrEmpty().size() + " step"
                            + (plan.stepsOrEmpty().size() == 1 ? "" : "s") + ")");
        }

        if (!resume) {
            // A genuinely new plan run (not resuming a failed/partial one) marks the semester
            // boundary that /pridatpredmet's auto-grant-vs-hold-for-approval counter resets against.
            semesterOperationService.resetSubjectRoleSemester(guildId);
        }

        String finalMigrationId = migrationId;
        SwitchSemesterSettings finalSettings = settings;
        SwitchSemesterSettings.SwitchPlan finalPlan = plan;
        executor.submit(() -> runPlan(guild, finalPlan, finalSettings, finalMigrationId, tracker, actorId, actorName));
    }

    private void runPlan(
            Guild guild, SwitchSemesterSettings.SwitchPlan plan, SwitchSemesterSettings settings, String migrationId,
            SemesterProgressTracker tracker, String actorId, String actorName) {
        String guildId = guild.getId();
        try {
            List<SwitchSemesterSettings.SwitchPlanStep> steps = plan.stepsOrEmpty();
            List<SwitchSemesterSettings.AdditionalChannel> additionalChannels = plan.additionalChannelsOrEmpty();
            List<String> incompleteLabels = new ArrayList<>();
            // Steps fill 0-90 (or the full 0-100 when there's no plan-wide additional channels to
            // apply afterward) - the additional-channels slice always comes last, once, after every
            // step, since it belongs to the plan as a whole rather than to any one step.
            int stepsSlice = additionalChannels.isEmpty() ? 100 : 90;

            for (int i = 0; i < steps.size(); i++) {
                SwitchSemesterSettings.SwitchPlanStep step = steps.get(i);
                String stepPrefix = "step" + i + ":";
                String label = step.label();
                int rangeStart = (int) (stepsSlice * i / (double) steps.size());
                int rangeEnd = (int) (stepsSlice * (i + 1) / (double) steps.size());

                if (tracker.hasCompleted(stepPrefix + "stepDone")) {
                    tracker.save(rangeEnd, "Step " + (i + 1) + "/" + steps.size() + " (" + label + ") already completed — skipped");
                    continue;
                }
                tracker.save(rangeStart, "Step " + (i + 1) + "/" + steps.size() + ": " + label + " starting...");

                boolean ok;
                if (step.isSwitch()) {
                    SemesterDefinition oldSem = settings.find(step.from());
                    SemesterDefinition newSem = settings.find(step.to());
                    ok = semesterOperationService.executeSwitchStep(
                            guild, oldSem, newSem, step.from(), step.to(), newSem.isEveryoneViewChannel(),
                            tracker, stepPrefix, migrationId, i, label, rangeStart, rangeEnd);
                } else {
                    SemesterDefinition sem = settings.find(step.semester());
                    boolean visible = step.isVisible();
                    // Same rule the manual Setup run panel already applies - hiding always uses
                    // false regardless of what the semester's own everyoneViewChannel says.
                    boolean everyoneViewChannel = visible && sem.isEveryoneViewChannel();
                    ok = semesterOperationService.executeSetupStep(
                            guild, sem, step.semester(), visible, everyoneViewChannel, step.isClearRoles(),
                            tracker, stepPrefix, migrationId, i, label, rangeStart, rangeEnd);
                }

                if (ok) {
                    tracker.completeStep(stepPrefix + "stepDone");
                } else {
                    incompleteLabels.add(label);
                }
            }

            if (!additionalChannels.isEmpty()) {
                boolean channelsOk = semesterOperationService.applyAdditionalChannels(
                        guild, additionalChannels, tracker, "planChannels:", migrationId, steps.size(), stepsSlice, 100);
                if (!channelsOk) {
                    incompleteLabels.add("Additional channels");
                }
            }

            String finalStatus = incompleteLabels.isEmpty() ? "success" : "partial";
            tracker.save(100, incompleteLabels.isEmpty()
                    ? "Plan \"" + plan.name() + "\" complete."
                    : "[WARN] Plan finished with unfinished step(s): " + String.join(", ", incompleteLabels)
                            + ". Use Resume to retry them.", finalStatus);

            // Only a fully-completed plan moves the tracked position - a partial one still has
            // unfinished steps that Resume needs to retry, and the startPlan guard must keep
            // rejecting a fresh plan attempt until it does.
            if ("success".equals(finalStatus)) {
                semesterOperationService.setCurrentPlan(guildId, plan.id());
            }

            dashboardAuditLogger.log(actorId, actorName, guild, "Ran semester plan", Map.of(
                    "planId", plan.id(), "planName", plan.name(), "steps", steps.size(),
                    "status", finalStatus, "incompleteSteps", incompleteLabels));

            logRoutingService.channelIdFor(guildId, LogEventType.SEMESTER_RECAP).ifPresent(recapChannelId ->
                    recapChannelPoster.post(guild, recapChannelId,
                            "**Plan " + ("success".equals(finalStatus) ? "Complete" : "Finished With Errors") + "** — `"
                                    + plan.name() + "` (" + steps.size() + " step" + (steps.size() == 1 ? "" : "s") + ")",
                            tracker.logsSnapshot(), "semester-plan-report.txt"));
        } catch (Exception e) {
            tracker.save(100, "[ERROR] Fatal error: " + e.getMessage(), "failed");
            log.error("[switchplan/run] Error", e);
        }
    }
}
