package sk.gkanocz.aisauth.semester;

import org.junit.jupiter.api.Test;
import sk.gkanocz.aisauth.semester.SwitchSemesterSettings.SwitchPlan;
import sk.gkanocz.aisauth.semester.SwitchSemesterSettings.SwitchPlanStep;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchSemesterSettingsTest {

    private final SemesterDefinition winter = new SemesterDefinition(
            "Winter 2025", List.of("cat-1"), List.of("role-1"), List.of(), true, SemesterDefinition.TYPE_WINTER);
    private final SemesterDefinition summer = new SemesterDefinition(
            "Summer 2026", List.of("cat-2"), List.of(), List.of(), false, SemesterDefinition.TYPE_SUMMER);

    private final SwitchPlan winterToSummer = new SwitchPlan("plan-w2s", "Switch ZS na LS",
            List.of(new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "Winter 2025", "Summer 2026", null, null, null)), null);
    private final SwitchPlan summerToWinter = new SwitchPlan("plan-s2w", "Switch LS na ZS",
            List.of(
                    new SwitchPlanStep(SwitchPlanStep.TYPE_SETUP, null, null, "Winter 2025", null, null),
                    new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "Summer 2026", "Winter 2025", null, null, null)), null);

    @Test
    void findIsCaseInsensitiveAndReturnsNullWhenMissing() {
        SwitchSemesterSettings settings = new SwitchSemesterSettings(List.of(winter, summer), List.of(), List.of());

        assertThat(settings.find("winter 2025")).isEqualTo(winter);
        assertThat(settings.find("WINTER 2025")).isEqualTo(winter);
        assertThat(settings.find("Autumn 2025")).isNull();
    }

    @Test
    void findPlanReturnsNullWhenMissing() {
        SwitchSemesterSettings settings = new SwitchSemesterSettings(List.of(), List.of(winterToSummer), List.of());

        assertThat(settings.findPlan("plan-w2s")).isEqualTo(winterToSummer);
        assertThat(settings.findPlan("nope")).isNull();
    }

    @Test
    void nextPlanIdWrapsAroundTheCycle() {
        SwitchSemesterSettings settings = new SwitchSemesterSettings(
                List.of(winter, summer), List.of(winterToSummer, summerToWinter),
                List.of("plan-w2s", "plan-s2w"));

        assertThat(settings.nextPlanId("plan-w2s")).isEqualTo("plan-s2w");
        // The path is a perpetual academic cycle, not a terminating sequence - the last plan wraps
        // back around to the first instead of returning null.
        assertThat(settings.nextPlanId("plan-s2w")).isEqualTo("plan-w2s");
    }

    @Test
    void nextPlanIdReturnsNullWhenPathEmptyOrCurrentUnknown() {
        SwitchSemesterSettings emptyPath = new SwitchSemesterSettings(List.of(), List.of(winterToSummer), List.of());
        assertThat(emptyPath.nextPlanId("plan-w2s")).isNull();
        assertThat(emptyPath.nextPlanId(null)).isNull();

        SwitchSemesterSettings settings = new SwitchSemesterSettings(
                List.of(), List.of(winterToSummer, summerToWinter), List.of("plan-w2s", "plan-s2w"));
        assertThat(settings.nextPlanId("plan-unknown")).isNull();
    }

    @Test
    void switchPlanStepLabelDescribesSwitchOrSetup() {
        SwitchPlanStep switchStep = new SwitchPlanStep(SwitchPlanStep.TYPE_SWITCH, "1zs", "2ls", null, null, null);
        SwitchPlanStep setupStep = new SwitchPlanStep(SwitchPlanStep.TYPE_SETUP, null, null, "1zs", null, null);

        assertThat(switchStep.label()).isEqualTo("1zs → 2ls");
        assertThat(setupStep.label()).isEqualTo("Setup 1zs");
        assertThat(switchStep.isSwitch()).isTrue();
        assertThat(setupStep.isSetup()).isTrue();
    }

    @Test
    void setupStepVisibleAndClearRolesDefaultToShowAndDontClear() {
        SwitchPlanStep unset = new SwitchPlanStep(SwitchPlanStep.TYPE_SETUP, null, null, "1zs", null, null);
        SwitchPlanStep hiddenAndClearing = new SwitchPlanStep(SwitchPlanStep.TYPE_SETUP, null, null, "1zs", false, true);

        assertThat(unset.isVisible()).isTrue();
        assertThat(unset.isClearRoles()).isFalse();
        assertThat(hiddenAndClearing.isVisible()).isFalse();
        assertThat(hiddenAndClearing.isClearRoles()).isTrue();
    }

    @Test
    void additionalChannelDefaultsToVisibleAndPlanDefaultsToEmptyList() {
        SwitchSemesterSettings.AdditionalChannel unset = new SwitchSemesterSettings.AdditionalChannel("chan-1", "chan", null, null);
        SwitchSemesterSettings.AdditionalChannel hidden = new SwitchSemesterSettings.AdditionalChannel("chan-2", "chan", false, null);
        assertThat(unset.isVisible()).isTrue();
        assertThat(hidden.isVisible()).isFalse();
        // Defaults to false, same as SemesterDefinition's own everyoneViewChannel - "Show" alone
        // shouldn't also expose the channel to @everyone unless explicitly opted into.
        assertThat(unset.isEveryoneViewChannel()).isFalse();
        assertThat(new SwitchSemesterSettings.AdditionalChannel("chan-3", "chan", true, true).isEveryoneViewChannel()).isTrue();

        // Plan-wide, not per-step - a plan saved before this field existed has none configured.
        assertThat(winterToSummer.additionalChannelsOrEmpty()).isEmpty();
    }

    @Test
    void resultingSemesterTypeFindsTheFirstConfiguredType() {
        SwitchSemesterSettings settings = new SwitchSemesterSettings(
                List.of(winter, summer), List.of(winterToSummer, summerToWinter), List.of());

        assertThat(settings.resultingSemesterType("plan-w2s")).isEqualTo(SemesterDefinition.TYPE_SUMMER);
        assertThat(settings.resultingSemesterType("plan-s2w")).isEqualTo(SemesterDefinition.TYPE_WINTER);
        assertThat(settings.resultingSemesterType("nope")).isNull();
    }

    @Test
    void emptySettingsFindReturnsNull() {
        assertThat(SwitchSemesterSettings.empty().find("anything")).isNull();
    }
}
