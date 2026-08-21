import type { SwitchPlan, SwitchPlanStep } from "@/lib/api";

/**
 * A step still on its placeholder ("Select…") has an empty from/to/semester - running it hits the
 * backend's "Semester "" not configured." 400, a technical message that doesn't say which step or
 * plan caused it. Shared between the Switch Plans editor (flags it before you even try to run) and
 * the Run panel (blocks the run with an actionable message instead of that raw 400).
 */
export function isStepIncomplete(step: SwitchPlanStep): boolean {
  return step.type === "switch" ? !step.from || !step.to : !step.semester;
}

export function firstIncompleteStepIndex(plan: SwitchPlan): number | null {
  const index = plan.steps.findIndex(isStepIncomplete);
  return index === -1 ? null : index;
}

export function firstIncompleteStepLabel(plan: SwitchPlan): string | null {
  const index = firstIncompleteStepIndex(plan);
  if (index === null) return null;
  const step = plan.steps[index];
  return step.type === "switch"
    ? `Step ${index + 1} ("${plan.name}") is missing a From/To semester - finish configuring it in Switch Plans first.`
    : `Step ${index + 1} ("${plan.name}") is missing a semester - finish configuring it in Switch Plans first.`;
}
