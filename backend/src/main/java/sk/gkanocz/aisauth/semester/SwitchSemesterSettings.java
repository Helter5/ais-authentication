package sk.gkanocz.aisauth.semester;

import java.util.List;

/** Mirrors the "configs"/"allowedTransitions" shape stored under cmd_settings_{guildId}_switchsemester. */
public record SwitchSemesterSettings(List<SemesterDefinition> configs, List<Transition> allowedTransitions) {

    public static SwitchSemesterSettings empty() {
        return new SwitchSemesterSettings(List.of(), List.of());
    }

    public SemesterDefinition find(String name) {
        return (configs == null ? List.<SemesterDefinition>of() : configs).stream()
                .filter(s -> s.name() != null && s.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public boolean transitionAllowed(String from, String to) {
        List<Transition> transitions = allowedTransitions == null ? List.of() : allowedTransitions;
        if (transitions.isEmpty()) {
            return true;
        }
        return transitions.stream().anyMatch(t -> t.from().equalsIgnoreCase(from) && t.to().equalsIgnoreCase(to));
    }

    public record Transition(String from, String to) {
    }
}
