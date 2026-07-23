package sk.gkanocz.aisauth.semester;

import java.util.List;
import java.util.Map;

/** Persisted (not in-memory) progress for a semester setup/switch run, so it survives a restart and supports resume. */
public record SemesterOperationState(
        boolean running,
        int progress,
        List<String> logs,
        String startedAt,
        String status,
        String operation,
        Map<String, Object> params,
        List<String> completedSteps) {

    public static SemesterOperationState idle() {
        return new SemesterOperationState(false, 0, List.of(), null, null, null, Map.of(), List.of());
    }
}
