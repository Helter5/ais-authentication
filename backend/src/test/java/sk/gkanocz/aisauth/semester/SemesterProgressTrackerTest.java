package sk.gkanocz.aisauth.semester;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SemesterProgressTrackerTest {

    @Mock
    private AdminSettingsService adminSettingsService;

    @Test
    void freshTrackerStartsWithNoLogsOrCompletedSteps() {
        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, "progress-key", false, null, Map.of(), "setup");

        assertThat(tracker.logsSnapshot()).isEmpty();
        assertThat(tracker.completedCount()).isZero();
        assertThat(tracker.hasCompleted("step1")).isFalse();
    }

    @Test
    void resumeCopiesLogsAndCompletedStepsFromPreviousState() {
        SemesterOperationState previous = new SemesterOperationState(
                true, 40, List.of("[10:00:00] did step1"), "2024-01-01T00:00:00Z", "running",
                "setup", Map.of(), List.of("step1"));

        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, "progress-key", true, previous, Map.of(), "setup");

        assertThat(tracker.logsSnapshot()).containsExactly("[10:00:00] did step1");
        assertThat(tracker.hasCompleted("step1")).isTrue();
        assertThat(tracker.completedCount()).isEqualTo(1);
    }

    @Test
    void resumeWithoutPreviousStateBehavesLikeAFreshTracker() {
        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, "progress-key", true, null, Map.of(), "setup");

        assertThat(tracker.logsSnapshot()).isEmpty();
        assertThat(tracker.completedCount()).isZero();
    }

    @Test
    void completeStepMarksItAsCompleted() {
        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, "progress-key", false, null, Map.of(), "setup");

        tracker.completeStep("step1");

        assertThat(tracker.hasCompleted("step1")).isTrue();
        assertThat(tracker.completedCount()).isEqualTo(1);
    }

    @Test
    void saveWithProgressBelow100SavesRunningStatus() {
        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, "progress-key", false, null, Map.of("k", "v"), "setup");

        tracker.save(40, "halfway there");

        ArgumentCaptor<SemesterOperationState> captor = ArgumentCaptor.forClass(SemesterOperationState.class);
        verify(adminSettingsService).set(org.mockito.ArgumentMatchers.eq("progress-key"), captor.capture());
        SemesterOperationState state = captor.getValue();
        assertThat(state.running()).isTrue();
        assertThat(state.progress()).isEqualTo(40);
        assertThat(state.status()).isEqualTo("running");
        assertThat(state.operation()).isEqualTo("setup");
        assertThat(state.params()).isEqualTo(Map.of("k", "v"));
        assertThat(state.logs()).hasSize(1);
        assertThat(state.logs().get(0)).contains("halfway there").startsWith("[");
    }

    @Test
    void saveWithProgressAt100SavesSuccessStatusAndRunningFalse() {
        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, "progress-key", false, null, Map.of(), "setup");

        tracker.save(100, "done");

        ArgumentCaptor<SemesterOperationState> captor = ArgumentCaptor.forClass(SemesterOperationState.class);
        verify(adminSettingsService).set(org.mockito.ArgumentMatchers.eq("progress-key"), captor.capture());
        SemesterOperationState state = captor.getValue();
        assertThat(state.running()).isFalse();
        assertThat(state.status()).isEqualTo("success");
    }

    @Test
    void saveWithNullMessageDoesNotAppendALogLine() {
        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, "progress-key", false, null, Map.of(), "setup");

        tracker.save(50, null);

        ArgumentCaptor<SemesterOperationState> captor = ArgumentCaptor.forClass(SemesterOperationState.class);
        verify(adminSettingsService).set(org.mockito.ArgumentMatchers.eq("progress-key"), captor.capture());
        assertThat(captor.getValue().logs()).isEmpty();
    }

    @Test
    void saveWithExplicitStatusOverridesTheDefault() {
        SemesterProgressTracker tracker = new SemesterProgressTracker(
                adminSettingsService, "progress-key", false, null, Map.of(), "setup");

        tracker.save(50, "failed hard", "error");

        ArgumentCaptor<SemesterOperationState> captor = ArgumentCaptor.forClass(SemesterOperationState.class);
        verify(adminSettingsService).set(org.mockito.ArgumentMatchers.eq("progress-key"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("error");
    }
}
