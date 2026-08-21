package sk.gkanocz.aisauth.semester;

import java.util.List;

/** Mirrors the "configs"/"plans"/"planPath" shape stored under cmd_settings_{guildId}_switchsemester. */
public record SwitchSemesterSettings(List<SemesterDefinition> configs, List<SwitchPlan> plans, List<String> planPath) {

    public static SwitchSemesterSettings empty() {
        return new SwitchSemesterSettings(List.of(), List.of(), List.of());
    }

    public SemesterDefinition find(String name) {
        return (configs == null ? List.<SemesterDefinition>of() : configs).stream()
                .filter(s -> s.name() != null && s.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public List<SwitchPlan> plansOrEmpty() {
        return plans == null ? List.of() : plans;
    }

    public SwitchPlan findPlan(String planId) {
        return plansOrEmpty().stream().filter(p -> p.id().equals(planId)).findFirst().orElse(null);
    }

    public List<String> planPathOrEmpty() {
        return planPath == null ? List.of() : planPath;
    }

    /**
     * The plan that must run right after {@code currentPlanId} - the plan-path is a perpetual cycle
     * (each academic year alternates the same handful of named plans), not a terminating sequence,
     * so the last entry wraps back around to the first. Null when the path isn't configured yet, or
     * {@code currentPlanId} isn't a step in it.
     */
    public String nextPlanId(String currentPlanId) {
        List<String> path = planPathOrEmpty();
        if (currentPlanId == null || path.isEmpty()) {
            return null;
        }
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i).equals(currentPlanId)) {
                return path.get((i + 1) % path.size());
            }
        }
        return null;
    }

    /**
     * The semester type (WINTER/SUMMER) a plan run leaves the guild in - every step's resulting
     * semester (a switch step's "to", a setup step's "semester") is expected to agree, so this is
     * just the first one found with a type actually configured. Null if the plan/its semesters
     * don't exist or none of them has a type set yet.
     */
    public String resultingSemesterType(String planId) {
        SwitchPlan plan = findPlan(planId);
        if (plan == null) {
            return null;
        }
        for (SwitchPlanStep step : plan.stepsOrEmpty()) {
            SemesterDefinition sem = step.isSwitch() ? find(step.to()) : step.isSetup() ? find(step.semester()) : null;
            if (sem != null && sem.semesterType() != null) {
                return sem.semesterType();
            }
        }
        return null;
    }

    /**
     * A named bundle of steps executed together as one admin-triggered "Plan" run - e.g. "Switch ZS
     * na LS" bundling every year-cohort's simultaneous switch for that calendar boundary, possibly
     * alongside a Setup step for a semester with no incoming switch (a fresh cohort's first
     * semester). Steps run in list order, each seeing the results of every previous one - same
     * top-to-bottom convention {@link SemesterDefinition.RoleMapping} already uses.
     *
     * <p>{@code additionalChannels} is one plan-wide list of individually-targeted channels (not
     * whole categories) forced to a specific @everyone View Channel state once, after every step has
     * run - e.g. showing "❄️pv-predmety-roles" and hiding "☀️pv-predmety-roles" on the plan that
     * results in Winter, and the reverse on the plan that results in Summer. Deliberately one list
     * per plan (not one per step) - these channels don't belong to any particular step, just to "the
     * plan finished".
     */
    public record SwitchPlan(String id, String name, List<SwitchPlanStep> steps, List<AdditionalChannel> additionalChannels) {
        public List<SwitchPlanStep> stepsOrEmpty() {
            return steps == null ? List.of() : steps;
        }

        public List<AdditionalChannel> additionalChannelsOrEmpty() {
            return additionalChannels == null ? List.of() : additionalChannels;
        }
    }

    /**
     * {@code visible}/{@code clearRoles} only apply to a {@code setup} step - the same two choices
     * the manual Setup run panel already exposes (show/hide channels, optionally clear configured
     * cleanup roles from all members), just remembered per plan step instead of picked fresh every
     * run. Null {@code visible} defaults to "show" and null {@code clearRoles} defaults to "don't
     * clear", matching what a step saved before these existed already did.
     */
    public record SwitchPlanStep(String type, String from, String to, String semester, Boolean visible, Boolean clearRoles) {
        public static final String TYPE_SWITCH = "switch";
        public static final String TYPE_SETUP = "setup";

        public boolean isSwitch() {
            return TYPE_SWITCH.equals(type);
        }

        public boolean isSetup() {
            return TYPE_SETUP.equals(type);
        }

        public boolean isVisible() {
            return visible == null || visible;
        }

        public boolean isClearRoles() {
            return Boolean.TRUE.equals(clearRoles);
        }

        public String label() {
            return isSwitch() ? (from + " → " + to) : ("Setup " + semester);
        }
    }

    /**
     * One individually-targeted channel a plan forces to a fixed View Channel state. Unlike
     * {@link SemesterDefinition}'s category-level visible/everyoneViewChannel split (where hiding
     * always forces @everyone hidden too), the two flags here are fully independent: {@code visible}
     * is applied to every existing role override on the channel unconditionally (show for all, or
     * hide for all), and {@code everyoneViewChannel} sets @everyone's own state exactly as configured
     * regardless of {@code visible} - e.g. Hide + @everyone=True is valid and meaningful (every other
     * role hidden, @everyone still sees it). Defaults to false, so "Show" alone doesn't accidentally
     * expose it guild-wide.
     */
    public record AdditionalChannel(String channelId, String channelName, Boolean visible, Boolean everyoneViewChannel) {
        public boolean isVisible() {
            return visible == null || visible;
        }

        public boolean isEveryoneViewChannel() {
            return Boolean.TRUE.equals(everyoneViewChannel);
        }
    }
}
