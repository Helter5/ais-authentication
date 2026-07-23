package sk.gkanocz.aisauth.semester;

import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Java port of the old bot's createSemesterOrchestrator: tracks logs/progress/completed steps for one run. */
class SemesterProgressTracker {

    private final AdminSettingsService adminSettingsService;
    private final String progressKey;
    private final List<String> logs;
    private final Set<String> completedSteps;
    private final String startedAt;
    private final String operation;
    private final Map<String, Object> params;

    SemesterProgressTracker(
            AdminSettingsService adminSettingsService, String progressKey, boolean resume,
            SemesterOperationState previous, Map<String, Object> params, String operation) {
        this.adminSettingsService = adminSettingsService;
        this.progressKey = progressKey;
        this.logs = resume && previous != null ? new ArrayList<>(previous.logs()) : new ArrayList<>();
        this.completedSteps = resume && previous != null
                ? new LinkedHashSet<>(previous.completedSteps()) : new LinkedHashSet<>();
        this.startedAt = resume && previous != null && previous.startedAt() != null
                ? previous.startedAt() : Instant.now().toString();
        this.params = params;
        this.operation = operation;
    }

    boolean hasCompleted(String step) {
        return completedSteps.contains(step);
    }

    void completeStep(String step) {
        completedSteps.add(step);
    }

    int completedCount() {
        return completedSteps.size();
    }

    List<String> logsSnapshot() {
        return List.copyOf(logs);
    }

    void save(int progress, String msg) {
        save(progress, msg, progress < 100 ? "running" : "success");
    }

    void save(int progress, String msg, String status) {
        if (msg != null) {
            logs.add("[" + LocalTime.now().withNano(0) + "] " + msg);
        }
        adminSettingsService.set(progressKey, new SemesterOperationState(
                progress < 100, progress, List.copyOf(logs), startedAt, status, operation, params, List.copyOf(completedSteps)));
    }
}
