package sk.gkanocz.aisauth.semester;

import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.RecapChannelPoster;
import sk.gkanocz.aisauth.semester.SwitchSemesterSettings.SwitchPlan;
import sk.gkanocz.aisauth.semester.SwitchSemesterSettings.SwitchPlanStep;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the synchronous validation/guard logic that runs before startPlan hands off to the
 * background executor (recap channel required, mutual-exclusion via SemesterOperationService,
 * plan/semester lookup, plan-path guard, resume matching). The async runPlan pipeline itself is
 * deliberately left uncovered here - same scoping call as SemesterOperationServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class SemesterPlanServiceTest {

    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private SemesterOperationService semesterOperationService;
    @Mock
    private SemesterSwitchHistoryRepository semesterSwitchHistoryRepository;
    @Mock
    private LogRoutingService logRoutingService;
    @Mock
    private DashboardAuditLogger dashboardAuditLogger;
    @Mock
    private RecapChannelPoster recapChannelPoster;
    @Mock
    private Guild guild;

    private SemesterPlanService service;

    @BeforeEach
    void setUp() {
        service = new SemesterPlanService(
                adminSettingsService, semesterOperationService, semesterSwitchHistoryRepository,
                logRoutingService, dashboardAuditLogger, recapChannelPoster);

        lenient().when(guild.getId()).thenReturn("guild-1");
        lenient().when(logRoutingService.channelIdFor("guild-1", LogEventType.SEMESTER_RECAP))
                .thenReturn(Optional.of("recap-channel-1"));
        lenient().when(semesterOperationService.lockFor(anyString())).thenReturn(new Object());
        lenient().when(semesterOperationService.currentPlan("guild-1")).thenReturn(null);
        lenient().when(adminSettingsService.get(eq("switchplan_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(null);
        lenient().when(adminSettingsService.get(eq(SemesterController.configsKey("guild-1")), eq(SwitchSemesterSettings.class), any()))
                .thenReturn(SwitchSemesterSettings.empty());
    }

    private SemesterDefinition semester(String name) {
        return new SemesterDefinition(name, List.of(), List.of(), List.of(), false, null);
    }

    private SemesterDefinition semester(String name, String type) {
        return new SemesterDefinition(name, List.of(), List.of(), List.of(), false, type);
    }

    private void stubSettings(SwitchSemesterSettings settings) {
        when(adminSettingsService.get(eq(SemesterController.configsKey("guild-1")), eq(SwitchSemesterSettings.class), any()))
                .thenReturn(settings);
    }

    @Test
    void startPlanRejectsWhenNoRecapChannelConfigured() {
        when(logRoutingService.channelIdFor("guild-1", LogEventType.SEMESTER_RECAP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startPlan(guild, "plan-1", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("log channel");
    }

    @Test
    void startPlanRejectsWhenAnotherOperationIsRunning() {
        doThrow(SemesterOperationInProgressException.withMessage("Another semester operation is already in progress for this guild."))
                .when(semesterOperationService).assertNoOtherOperationRunning(eq("guild-1"), eq("switchplan_log_guild-1"));

        assertThatThrownBy(() -> service.startPlan(guild, "plan-1", false, "actor-1", "actorname"))
                .isInstanceOf(SemesterOperationInProgressException.class);
    }

    @Test
    void startPlanRejectsUnknownPlanId() {
        stubSettings(new SwitchSemesterSettings(List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> service.startPlan(guild, "plan-missing", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("plan-missing");
    }

    @Test
    void startPlanRejectsWhenPlanHasNoSteps() {
        SwitchPlan emptyPlan = new SwitchPlan("plan-1", "Empty Plan", List.of(), null);
        stubSettings(new SwitchSemesterSettings(List.of(), List.of(emptyPlan), List.of()));

        assertThatThrownBy(() -> service.startPlan(guild, "plan-1", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no steps");
    }

    @Test
    void startPlanRejectsSwitchStepReferencingUnknownSemester() {
        SwitchPlan plan = new SwitchPlan("plan-1", "Switch ZS na LS",
                List.of(new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "1zs", "2ls", null, null, null)), null);
        stubSettings(new SwitchSemesterSettings(List.of(semester("1zs")), List.of(plan), List.of()));

        assertThatThrownBy(() -> service.startPlan(guild, "plan-1", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("2ls");
    }

    @Test
    void startPlanRejectsSetupStepReferencingUnknownSemester() {
        SwitchPlan plan = new SwitchPlan("plan-1", "Switch LS na ZS",
                List.of(new SwitchPlanStep(SwitchPlanStep.TYPE_SETUP, null, null, "1zs", null, null)), null);
        stubSettings(new SwitchSemesterSettings(List.of(), List.of(plan), List.of()));

        assertThatThrownBy(() -> service.startPlan(guild, "plan-1", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("1zs");
    }

    @Test
    void startPlanRejectsWhenNotNextInConfiguredPlanPath() {
        SwitchPlan planA = new SwitchPlan("plan-a", "Switch ZS na LS",
                List.of(new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "1zs", "2ls", null, null, null)), null);
        SwitchPlan planB = new SwitchPlan("plan-b", "Switch LS na ZS",
                List.of(new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "2ls", "3zs", null, null, null)), null);
        stubSettings(new SwitchSemesterSettings(
                List.of(semester("1zs"), semester("2ls"), semester("3zs")),
                List.of(planA, planB), List.of("plan-a", "plan-b")));
        when(semesterOperationService.currentPlan("guild-1")).thenReturn("plan-a");

        // "plan-a" just ran - the cycle says "plan-b" comes next, so re-running "plan-a" itself
        // out of order must be rejected instead of silently remapping members from the wrong start.
        assertThatThrownBy(() -> service.startPlan(guild, "plan-a", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not the next step");
    }

    @Test
    void startPlanRejectsSwitchStepBetweenTwoSemestersOfTheSameType() {
        SwitchPlan plan = new SwitchPlan("plan-1", "Bad Plan",
                List.of(new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "1zs", "3zs", null, null, null)), null);
        stubSettings(new SwitchSemesterSettings(
                List.of(semester("1zs", SemesterDefinition.TYPE_WINTER), semester("3zs", SemesterDefinition.TYPE_WINTER)),
                List.of(plan), List.of()));

        assertThatThrownBy(() -> service.startPlan(guild, "plan-1", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must alternate semester type");
    }

    @Test
    void startPlanRejectsStepsResultingInMixedSemesterTypes() {
        SwitchPlan plan = new SwitchPlan("plan-1", "Mixed Plan", List.of(
                new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "1zs", "2ls", null, null, null),
                new SwitchPlanStep(SwitchPlanStep.TYPE_SETUP, null, null, "3zs", null, null)), null);
        stubSettings(new SwitchSemesterSettings(
                List.of(
                        semester("1zs", SemesterDefinition.TYPE_WINTER),
                        semester("2ls", SemesterDefinition.TYPE_SUMMER),
                        semester("3zs", SemesterDefinition.TYPE_WINTER)),
                List.of(plan), List.of()));

        assertThatThrownBy(() -> service.startPlan(guild, "plan-1", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("mixes semester types");
    }

    @Test
    void startPlanAllowsAnArchivalHideAndClearRolesSetupStepOfADifferentSemesterType() {
        // "3ls" is a graduating cohort's closing semester (hide + clear roles), bundled into an
        // otherwise all-Winter plan purely because it happens at the same calendar boundary - it
        // shouldn't have to agree in type with the rest of the plan.
        SwitchPlan plan = new SwitchPlan("plan-1", "Winter", List.of(
                new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "1zs", "2ls", null, null, null),
                new SwitchPlanStep(SwitchPlanStep.TYPE_SETUP, null, null, "3zs", null, null),
                new SwitchPlanStep(SwitchPlanStep.TYPE_SETUP, null, null, "3ls", false, true)), null);
        stubSettings(new SwitchSemesterSettings(
                List.of(
                        semester("1zs", SemesterDefinition.TYPE_SUMMER),
                        semester("2ls", SemesterDefinition.TYPE_WINTER),
                        semester("3zs", SemesterDefinition.TYPE_WINTER),
                        semester("3ls", SemesterDefinition.TYPE_SUMMER)),
                List.of(plan), List.of()));

        assertThatCode(() -> service.startPlan(guild, "plan-1", false, "actor-1", "actorname"))
                .doesNotThrowAnyException();
    }

    @Test
    void startPlanResumeRejectsWhenNoMatchingFailedRun() {
        SwitchPlan plan = new SwitchPlan("plan-1", "Switch ZS na LS",
                List.of(new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "1zs", "2ls", null, null, null)), null);
        stubSettings(new SwitchSemesterSettings(List.of(semester("1zs"), semester("2ls")), List.of(plan), List.of()));

        assertThatThrownBy(() -> service.startPlan(guild, "plan-1", true, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no matching failed");
    }
}
