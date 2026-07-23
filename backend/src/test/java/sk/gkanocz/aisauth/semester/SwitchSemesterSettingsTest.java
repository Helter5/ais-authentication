package sk.gkanocz.aisauth.semester;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchSemesterSettingsTest {

    private final SemesterDefinition winter = new SemesterDefinition(
            "Winter 2025", List.of("cat-1"), List.of("role-1"), List.of(), true);
    private final SemesterDefinition summer = new SemesterDefinition(
            "Summer 2026", List.of("cat-2"), List.of(), List.of(), false);

    @Test
    void findIsCaseInsensitiveAndReturnsNullWhenMissing() {
        SwitchSemesterSettings settings = new SwitchSemesterSettings(List.of(winter, summer), List.of());

        assertThat(settings.find("winter 2025")).isEqualTo(winter);
        assertThat(settings.find("WINTER 2025")).isEqualTo(winter);
        assertThat(settings.find("Autumn 2025")).isNull();
    }

    @Test
    void emptyTransitionsListMeansAnyTransitionIsAllowed() {
        SwitchSemesterSettings settings = new SwitchSemesterSettings(List.of(winter, summer), List.of());

        assertThat(settings.transitionAllowed("Winter 2025", "Summer 2026")).isTrue();
        assertThat(settings.transitionAllowed("Summer 2026", "Winter 2025")).isTrue();
    }

    @Test
    void nonEmptyTransitionsListRestrictsToConfiguredPairs() {
        SwitchSemesterSettings settings = new SwitchSemesterSettings(
                List.of(winter, summer),
                List.of(new SwitchSemesterSettings.Transition("Winter 2025", "Summer 2026")));

        assertThat(settings.transitionAllowed("Winter 2025", "Summer 2026")).isTrue();
        assertThat(settings.transitionAllowed("winter 2025", "summer 2026")).isTrue();
        assertThat(settings.transitionAllowed("Summer 2026", "Winter 2025")).isFalse();
    }

    @Test
    void emptySettingsFindReturnsNull() {
        assertThat(SwitchSemesterSettings.empty().find("anything")).isNull();
    }
}
