package sk.gkanocz.aisauth.semester;

import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.DiscordModerationService;
import sk.gkanocz.aisauth.discordbot.RecapChannelPoster;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the synchronous validation/guard logic that runs before startSetup/startSwitch hand off to
 * the background executor (recap channel required, mutual-exclusion between setup/switch, semester
 * lookup, transition allowlist, resume matching). The async runSetup/runSwitch pipelines themselves
 * (loadMembers, role mapping, visibility application) need heavy JDA action-chain mocking for their
 * value and are deliberately left uncovered here, same scoping call as HackedAccountTrapListener.
 */
@ExtendWith(MockitoExtension.class)
class SemesterOperationServiceTest {

    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private DashboardAuditLogger dashboardAuditLogger;
    @Mock
    private RecapChannelPoster recapChannelPoster;
    @Mock
    private DiscordModerationService moderationService;
    @Mock
    private SemesterVisibilityService semesterVisibilityService;
    @Mock
    private LogRoutingService logRoutingService;
    @Mock
    private Guild guild;

    private SemesterOperationService service;

    @BeforeEach
    void setUp() {
        service = new SemesterOperationService(
                adminSettingsService, dashboardAuditLogger, recapChannelPoster, moderationService,
                semesterVisibilityService, logRoutingService);

        lenient().when(guild.getId()).thenReturn("guild-1");
        lenient().when(logRoutingService.channelIdFor("guild-1", LogEventType.SEMESTER_RECAP))
                .thenReturn(Optional.of("recap-channel-1"));
        // Defaults for the two mutual-exclusion progress-state lookups and the semester configs
        // lookup - individual tests override whichever one they care about with a specific stub.
        lenient().when(adminSettingsService.get(eq("semester_setup_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(null);
        lenient().when(adminSettingsService.get(eq("switchsemester_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(null);
        lenient().when(adminSettingsService.get(eq(SemesterController.configsKey("guild-1")), eq(SwitchSemesterSettings.class), any()))
                .thenReturn(SwitchSemesterSettings.empty());
    }

    private SemesterDefinition semester(String name) {
        return new SemesterDefinition(name, List.of(), List.of(), List.of(), false);
    }

    private void stubConfigs(SemesterDefinition... semesters) {
        when(adminSettingsService.get(eq(SemesterController.configsKey("guild-1")), eq(SwitchSemesterSettings.class), any()))
                .thenReturn(new SwitchSemesterSettings(List.of(semesters), List.of()));
    }

    // ---- startSetup ----

    @Test
    void startSetupRejectsWhenNoRecapChannelConfigured() {
        when(logRoutingService.channelIdFor("guild-1", LogEventType.SEMESTER_RECAP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startSetup(guild, "ZS2026", true, false, false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("log channel");
    }

    @Test
    void startSetupRejectsWhenASetupIsAlreadyRunning() {
        SemesterOperationState running = new SemesterOperationState(true, 50, List.of(), "now", "running", "setup", null, List.of());
        when(adminSettingsService.get(eq("semester_setup_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(running);

        assertThatThrownBy(() -> service.startSetup(guild, "ZS2026", true, false, false, "actor-1", "actorname"))
                .isInstanceOf(SemesterOperationInProgressException.class);
    }

    @Test
    void startSetupRejectsWhenASwitchIsAlreadyRunning() {
        SemesterOperationState runningSwitch =
                new SemesterOperationState(true, 50, List.of(), "now", "running", "switch", null, List.of());
        when(adminSettingsService.get(eq("switchsemester_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(runningSwitch);

        assertThatThrownBy(() -> service.startSetup(guild, "ZS2026", true, false, false, "actor-1", "actorname"))
                .isInstanceOf(SemesterOperationInProgressException.class)
                .hasMessageContaining("switch");
    }

    @Test
    void startSetupRejectsUnknownSemesterName() {
        stubConfigs(semester("ZS2026"));

        assertThatThrownBy(() -> service.startSetup(guild, "LS2027", true, false, false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("LS2027");
    }

    @Test
    void startSetupResumeRejectsWhenNoMatchingFailedRun() {
        stubConfigs(semester("ZS2026"));

        assertThatThrownBy(() -> service.startSetup(guild, "ZS2026", true, false, true, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no matching failed");
    }

    // Deliberately no "valid request succeeds" test here: a successful call reaches
    // executor.submit(...) and starts a real background thread running against these mocks, which
    // is out of scope (see class javadoc) and would leak a non-daemon thread per test run.

    // ---- startSwitch ----

    @Test
    void startSwitchRejectsWhenASwitchIsAlreadyRunning() {
        SemesterOperationState running = new SemesterOperationState(true, 50, List.of(), "now", "running", "switch", null, List.of());
        when(adminSettingsService.get(eq("switchsemester_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(running);

        assertThatThrownBy(() -> service.startSwitch(guild, "ZS2026", "LS2027", false, "actor-1", "actorname"))
                .isInstanceOf(SemesterOperationInProgressException.class);
    }

    @Test
    void startSwitchRejectsWhenASetupIsAlreadyRunning() {
        SemesterOperationState runningSetup =
                new SemesterOperationState(true, 50, List.of(), "now", "running", "setup", null, List.of());
        when(adminSettingsService.get(eq("semester_setup_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(runningSetup);

        assertThatThrownBy(() -> service.startSwitch(guild, "ZS2026", "LS2027", false, "actor-1", "actorname"))
                .isInstanceOf(SemesterOperationInProgressException.class)
                .hasMessageContaining("setup");
    }

    @Test
    void startSwitchRejectsUnknownOldSemester() {
        stubConfigs(semester("LS2027"));

        assertThatThrownBy(() -> service.startSwitch(guild, "ZS2026", "LS2027", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("ZS2026");
    }

    @Test
    void startSwitchRejectsUnknownNewSemester() {
        stubConfigs(semester("ZS2026"));

        assertThatThrownBy(() -> service.startSwitch(guild, "ZS2026", "LS2027", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("LS2027");
    }

    @Test
    void startSwitchRejectsDisallowedTransition() {
        when(adminSettingsService.get(eq(SemesterController.configsKey("guild-1")), eq(SwitchSemesterSettings.class), any()))
                .thenReturn(new SwitchSemesterSettings(
                        List.of(semester("ZS2026"), semester("LS2027")),
                        List.of(new SwitchSemesterSettings.Transition("ZS2026", "ZS2028"))));

        assertThatThrownBy(() -> service.startSwitch(guild, "ZS2026", "LS2027", false, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not in the allowed transitions list");
    }

    @Test
    void startSwitchResumeRejectsWhenNoMatchingFailedRun() {
        stubConfigs(semester("ZS2026"), semester("LS2027"));

        assertThatThrownBy(() -> service.startSwitch(guild, "ZS2026", "LS2027", true, "actor-1", "actorname"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no matching failed");
    }

    // ---- status ----

    @Test
    void statusReturnsIdleWhenNothingStored() {
        when(adminSettingsService.get(eq("semester_setup_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(SemesterOperationState.idle());

        SemesterOperationState status = service.status("guild-1", "setup");

        assertThat(status.running()).isFalse();
    }

    @Test
    void statusUsesTheCorrectKeyPerOperationType() {
        SemesterOperationState setupState = new SemesterOperationState(true, 10, List.of(), "now", "running", "setup", null, List.of());
        when(adminSettingsService.get(eq("semester_setup_log_guild-1"), eq(SemesterOperationState.class), any()))
                .thenReturn(setupState);

        assertThat(service.status("guild-1", "setup").progress()).isEqualTo(10);
    }
}
