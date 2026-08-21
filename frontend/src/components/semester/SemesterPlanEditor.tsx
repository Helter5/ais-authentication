import { useState } from "react";
import {
  Plus, X, ArrowLeftRight, GripVertical, ChevronDown, ChevronRight, Eye, EyeOff, Hash, TriangleAlert,
} from "lucide-react";
import { cn } from "@/lib/utils";
import type { AdditionalChannel, SwitchPlan, SwitchPlanStep } from "@/lib/api";
import type { SemesterChannel, SemesterConfig } from "@/pages/SwitchSemester";
import { isStepIncomplete } from "@/lib/semesterPlanValidation";

function uid() { return Math.random().toString(36).slice(2); }

/** Reorders one item within an array by removing it from `from` and reinserting at `to`. */
function reorder<T>(list: T[], from: number, to: number): T[] {
  const next = [...list];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

function stepLabel(step: SwitchPlanStep) {
  return step.type === "switch" ? `${step.from || "…"} → ${step.to || "…"}` : `Setup ${step.semester || "…"}`;
}

/**
 * One plan-wide list of individually-targeted channels forced to a fixed @everyone View Channel
 * state once the whole plan finishes - e.g. showing "❄️pv-predmety-roles" and hiding
 * "☀️pv-predmety-roles" on the plan that results in Winter, and the reverse on the plan that
 * results in Summer. Deliberately one section per plan (not one per step) - these channels don't
 * belong to any particular step, just to "this plan finished".
 */
function PlanAdditionalChannels({ channels, entries, onChange }: {
  channels: SemesterChannel[];
  entries: AdditionalChannel[];
  onChange: (entries: AdditionalChannel[]) => void;
}) {
  const available = channels.filter(c => !entries.some(e => e.channelId === c.id));
  const update = (i: number, entry: AdditionalChannel) => onChange(entries.map((e, idx) => idx === i ? entry : e));
  const remove = (i: number) => onChange(entries.filter((_, idx) => idx !== i));
  const add = (channelId: string) => {
    const ch = channels.find(c => c.id === channelId);
    if (!ch) return;
    onChange([...entries, { channelId: ch.id, channelName: ch.name, visible: true, everyoneViewChannel: false }]);
  };

  return (
    <div className="rounded border border-zinc-800 bg-zinc-950/40 p-2.5 space-y-2">
      <div className="flex items-center gap-1.5">
        <Hash className="w-3.5 h-3.5 text-sky-400 flex-shrink-0" />
        <span className="text-[11px] font-bold uppercase tracking-wider text-zinc-400">Additional Channels</span>
        {entries.length > 0 && (
          <span className="text-[10px] text-zinc-600 ml-auto flex-shrink-0">
            {entries.length} channel{entries.length !== 1 ? "s" : ""}
          </span>
        )}
      </div>
      <p className="text-[11px] text-zinc-600">
        Individual channels (outside any category) forced to Show/Hide once this whole plan finishes running -
        e.g. this cycle's elective-subject channel shown, last cycle's hidden. Show/Hide applies to every role
        that already has an override on the channel; @everyone View Channel is independent of it - e.g. Hide +
        @everyone True is valid: every other role loses access, @everyone still sees it.
      </p>
      {entries.length > 0 && (
        <div className="space-y-1.5">
          {entries.map((entry, i) => (
            <div key={entry.channelId} className="rounded bg-zinc-900/60 border border-zinc-800 px-2 py-1.5">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs">
                <span className="text-zinc-300 font-medium truncate max-w-[45%]">{entry.channelName}</span>

                <div className="grid grid-cols-2 gap-0.5 rounded bg-zinc-950 border border-zinc-800 p-0.5 flex-shrink-0">
                  <button type="button" onClick={() => update(i, { ...entry, visible: true })}
                    className={cn("flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold transition-colors",
                      (entry.visible ?? true) ? "bg-emerald-600/80 text-white" : "text-zinc-500 hover:text-zinc-300")}>
                    <Eye className="w-3 h-3" /> Show
                  </button>
                  <button type="button" onClick={() => update(i, { ...entry, visible: false })}
                    className={cn("flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold transition-colors",
                      !(entry.visible ?? true) ? "bg-red-600/80 text-white" : "text-zinc-500 hover:text-zinc-300")}>
                    <EyeOff className="w-3 h-3" /> Hide
                  </button>
                </div>

                <div className="flex items-center gap-1.5 flex-shrink-0 pl-3 border-l border-zinc-800"
                  title="@everyone's own visibility, independent of Show/Hide above">
                  <span className="text-[11px] font-semibold text-zinc-400 whitespace-nowrap">@everyone</span>
                  <div className="grid grid-cols-2 gap-0.5 rounded bg-zinc-950 border border-zinc-800 p-0.5">
                    <button type="button" onClick={() => update(i, { ...entry, everyoneViewChannel: true })}
                      className={cn("px-2 py-0.5 rounded text-[10px] font-bold transition-colors",
                        entry.everyoneViewChannel ? "bg-emerald-600/80 text-white" : "text-zinc-500 hover:text-zinc-300")}>
                      True
                    </button>
                    <button type="button" onClick={() => update(i, { ...entry, everyoneViewChannel: false })}
                      className={cn("px-2 py-0.5 rounded text-[10px] font-bold transition-colors",
                        !entry.everyoneViewChannel ? "bg-red-600/80 text-white" : "text-zinc-500 hover:text-zinc-300")}>
                      False
                    </button>
                  </div>
                </div>

                <button type="button" onClick={() => remove(i)} className="ml-auto text-zinc-600 hover:text-red-400 transition-colors flex-shrink-0">
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
      {available.length > 0 ? (
        <select value="" onChange={e => { if (e.target.value) add(e.target.value); }}
          className="w-full px-2 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-400 outline-none focus:border-sky-500">
          <option value="">+ Add channel…</option>
          {available.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      ) : entries.length === 0 && (
        <p className="text-[11px] text-zinc-700 italic">No text channels found.</p>
      )}
    </div>
  );
}

interface StepRowProps {
  step: SwitchPlanStep;
  configs: SemesterConfig[];
  onChange: (step: SwitchPlanStep) => void;
  onRemove: () => void;
  dragging: boolean;
  onDragStart: () => void;
  onDragEnd: () => void;
  onDropHere: () => void;
}

function StepRow({ step, configs, onChange, onRemove, dragging, onDragStart, onDragEnd, onDropHere }: StepRowProps) {
  const typeOf = (name: string) => configs.find(c => c.name === name)?.semesterType ?? null;
  const fromType = step.type === "switch" ? typeOf(step.from) : null;
  const toType = step.type === "switch" ? typeOf(step.to) : null;
  const sameTypeWarning = step.type === "switch" && step.from && step.to && fromType !== null && fromType === toType;
  const incompleteWarning = isStepIncomplete(step);

  return (
    <div className="flex flex-col gap-1.5">
    <div onDragOver={e => e.preventDefault()} onDrop={onDropHere}
      className={cn("rounded bg-zinc-800/60 border border-zinc-700/60 overflow-hidden transition-opacity", dragging && "opacity-40")}>
    <div className="flex items-center gap-2 px-2.5 py-2">
      <span draggable onDragStart={onDragStart} onDragEnd={onDragEnd} title="Drag to reorder"
        className="flex-shrink-0 cursor-grab text-zinc-600 hover:text-zinc-300 transition-colors active:cursor-grabbing">
        <GripVertical className="w-4 h-4" />
      </span>
      <div className="grid grid-cols-2 gap-1 rounded bg-zinc-950 border border-zinc-800 p-0.5 flex-shrink-0">
        <button type="button"
          onClick={() => onChange({ type: "switch", from: step.type === "switch" ? step.from : "", to: step.type === "switch" ? step.to : "" })}
          className={cn("px-2 py-1 rounded text-[10px] font-bold transition-colors",
            step.type === "switch" ? "bg-rose-700 text-white" : "text-zinc-500 hover:text-zinc-300")}>
          Switch
        </button>
        <button type="button"
          onClick={() => onChange({
            type: "setup", semester: step.type === "setup" ? step.semester : "",
            visible: step.type === "setup" ? step.visible : true,
            clearRoles: step.type === "setup" ? step.clearRoles : false,
          })}
          className={cn("px-2 py-1 rounded text-[10px] font-bold transition-colors",
            step.type === "setup" ? "bg-amber-600 text-white" : "text-zinc-500 hover:text-zinc-300")}>
          Setup
        </button>
      </div>

      {step.type === "switch" ? (
        <div className="flex items-center gap-1.5 flex-1 min-w-0">
          <select value={step.from} onChange={e => onChange({ ...step, from: e.target.value })}
            className="flex-1 min-w-0 px-2 py-1 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 outline-none focus:border-rose-500">
            <option value="">From…</option>
            {configs.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
          </select>
          <ArrowLeftRight className="w-3 h-3 text-zinc-600 flex-shrink-0" />
          <select value={step.to} onChange={e => onChange({ ...step, to: e.target.value })}
            className="flex-1 min-w-0 px-2 py-1 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 outline-none focus:border-rose-500">
            <option value="">To…</option>
            {configs.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
          </select>
        </div>
      ) : (
        <select value={step.semester} onChange={e => onChange({ ...step, semester: e.target.value })}
          className="flex-1 min-w-0 px-2 py-1 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 outline-none focus:border-amber-500">
          <option value="">Semester…</option>
          {configs.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
        </select>
      )}

      <button type="button" onClick={onRemove} className="text-zinc-600 hover:text-red-400 transition-colors flex-shrink-0">
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
    {step.type === "setup" && (
      <div className="flex items-center gap-3 px-2.5 py-2 border-t border-zinc-700/60 bg-zinc-900/40">
        <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-500 flex-shrink-0">Channels</span>
        <div className="grid grid-cols-2 gap-0.5 rounded bg-zinc-950 border border-zinc-800 p-0.5 flex-shrink-0">
          <button type="button" onClick={() => onChange({ ...step, visible: true })}
            className={cn("flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold transition-colors",
              (step.visible ?? true) ? "bg-emerald-600/80 text-white" : "text-zinc-500 hover:text-zinc-300")}>
            <Eye className="w-3 h-3" /> Show
          </button>
          <button type="button" onClick={() => onChange({ ...step, visible: false })}
            className={cn("flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold transition-colors",
              !(step.visible ?? true) ? "bg-red-600/80 text-white" : "text-zinc-500 hover:text-zinc-300")}>
            <EyeOff className="w-3 h-3" /> Hide
          </button>
        </div>
        <label className="flex items-center gap-1.5 cursor-pointer select-none text-[11px] text-zinc-400 min-w-0 truncate">
          <input type="checkbox" checked={step.clearRoles ?? false}
            onChange={e => onChange({ ...step, clearRoles: e.target.checked })}
            className="accent-red-500 cursor-pointer flex-shrink-0" />
          Clear configured cleanup roles from all members
        </label>
      </div>
    )}
    </div>
    {sameTypeWarning && (
      <p className="text-[11px] text-red-400 pl-[3.75rem]">
        {fromType === "WINTER" ? "Winter" : "Summer"} → same type - a switch must alternate Winter/Summer.
      </p>
    )}
    {incompleteWarning && (
      <p className="text-[11px] text-red-400 pl-[3.75rem]">
        Incomplete - {step.type === "switch" ? "pick both a From and To semester" : "pick a semester"} before this plan can run.
      </p>
    )}
    </div>
  );
}

interface PlanCardProps {
  plan: SwitchPlan;
  index: number;
  expanded: boolean;
  configs: SemesterConfig[];
  channels: SemesterChannel[];
  onToggle: () => void;
  onChange: (plan: SwitchPlan) => void;
  onRemove: () => void;
  dragging: boolean;
  onDragStart: () => void;
  onDragEnd: () => void;
  onDropHere: () => void;
}

function PlanCard({ plan, index, expanded, configs, channels, onToggle, onChange, onRemove, dragging, onDragStart, onDragEnd, onDropHere }: PlanCardProps) {
  const [dragStepIndex, setDragStepIndex] = useState<number | null>(null);

  const updateStep = (i: number, step: SwitchPlanStep) => {
    onChange({ ...plan, steps: plan.steps.map((s, idx) => idx === i ? step : s) });
  };
  const removeStep = (i: number) => {
    onChange({ ...plan, steps: plan.steps.filter((_, idx) => idx !== i) });
  };
  const addStep = () => {
    onChange({ ...plan, steps: [...plan.steps, { type: "switch", from: "", to: "" }] });
  };
  const moveStep = (from: number, to: number) => {
    if (from === to || to < 0 || to >= plan.steps.length) return;
    onChange({ ...plan, steps: reorder(plan.steps, from, to) });
  };
  const dropStep = (targetIndex: number) => {
    if (dragStepIndex === null) return;
    moveStep(dragStepIndex, targetIndex);
    setDragStepIndex(null);
  };

  return (
    <div onDragOver={e => e.preventDefault()} onDrop={onDropHere}
      className={cn("rounded-lg border overflow-hidden transition-colors",
        expanded ? "border-indigo-500/60 bg-indigo-500/[0.04]" : "border-zinc-800 bg-zinc-900/60",
        dragging && "opacity-40")}>
      <div className={cn("flex items-center gap-2 px-3 py-2", expanded && "bg-indigo-500/[0.06]")}>
        <span draggable onDragStart={onDragStart} onDragEnd={onDragEnd} title="Drag to reorder (changes the cycle order)"
          className="flex-shrink-0 cursor-grab text-zinc-600 hover:text-zinc-300 transition-colors active:cursor-grabbing">
          <GripVertical className="w-4 h-4" />
        </span>
        <button type="button" onClick={onToggle}
          className="flex-1 min-w-0 flex items-center gap-2 text-left rounded px-1 -mx-1 py-0.5 hover:bg-white/5 transition-colors">
          {expanded ? <ChevronDown className="w-3.5 h-3.5 text-indigo-400 flex-shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-zinc-500 flex-shrink-0" />}
          <span className="text-zinc-600 font-mono text-xs w-5 flex-shrink-0">{index + 1}.</span>
          <span className={cn("text-sm font-semibold truncate", expanded ? "text-white" : "text-zinc-200")}>
            {plan.name || "Unnamed plan"}
          </span>
          <span className="text-[10px] text-zinc-600 flex-shrink-0">{plan.steps.length} step{plan.steps.length !== 1 ? "s" : ""}</span>
          {plan.steps.some(isStepIncomplete) && (
            <span title="Has an unfinished step (missing semester)" className="flex-shrink-0">
              <TriangleAlert className="w-3.5 h-3.5 text-red-400" />
            </span>
          )}
          {expanded && <span className="text-[9px] font-bold uppercase tracking-wider text-indigo-400 flex-shrink-0">editing</span>}
        </button>
        <button onClick={onRemove} className="text-zinc-600 hover:text-red-400 transition-colors flex-shrink-0">
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
      {expanded && (
        <div className="px-3 pb-3 pt-1 border-t border-zinc-800 space-y-2">
          <input value={plan.name} onChange={e => onChange({ ...plan, name: e.target.value })}
            placeholder="Plan name, e.g. Switch ZS na LS"
            className="w-full px-2 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs font-semibold text-zinc-200 outline-none focus:border-indigo-500" />
          {plan.steps.length === 0 && <p className="text-[11px] text-zinc-600 italic py-1">No steps yet.</p>}
          <div className="space-y-1.5">
            {plan.steps.map((step, i) => (
              <StepRow key={i} step={step} configs={configs} onChange={s => updateStep(i, s)} onRemove={() => removeStep(i)}
                dragging={dragStepIndex === i} onDragStart={() => setDragStepIndex(i)}
                onDragEnd={() => setDragStepIndex(null)} onDropHere={() => dropStep(i)} />
            ))}
          </div>
          <button onClick={addStep}
            className="flex items-center gap-1 px-2.5 py-1.5 rounded text-[11px] font-bold uppercase tracking-wider border border-zinc-700 text-zinc-400 hover:text-zinc-200 hover:border-zinc-500 transition-colors">
            <Plus className="w-3 h-3" /> Add Step
          </button>
          <PlanAdditionalChannels
            channels={channels}
            entries={plan.additionalChannels ?? []}
            onChange={entries => onChange({ ...plan, additionalChannels: entries })}
          />
        </div>
      )}
    </div>
  );
}

interface SemesterPlanEditorProps {
  configs: SemesterConfig[];
  channels: SemesterChannel[];
  plans: SwitchPlan[];
  onChange: (plans: SwitchPlan[]) => void;
}

export function SemesterPlanEditor({ configs, channels, plans, onChange }: SemesterPlanEditorProps) {
  // Fully controlled - edits just report the new array up to the page's one Save Changes button,
  // same draft the Semester Config editor below already uses. No separate save state/button here.
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [dragPlanIndex, setDragPlanIndex] = useState<number | null>(null);

  const toggle = (id: string) => setExpandedId(prev => prev === id ? null : id);

  const updatePlan = (i: number, plan: SwitchPlan) => {
    onChange(plans.map((p, idx) => idx === i ? plan : p));
  };
  const removePlan = (i: number) => {
    if (plans[i].id === expandedId) setExpandedId(null);
    onChange(plans.filter((_, idx) => idx !== i));
  };
  const addPlan = () => {
    const plan = { id: uid(), name: "New Plan", steps: [] };
    onChange([...plans, plan]);
    setExpandedId(plan.id);
  };
  const movePlan = (from: number, to: number) => {
    if (from === to || to < 0 || to >= plans.length) return;
    onChange(reorder(plans, from, to));
  };
  const dropPlan = (targetIndex: number) => {
    if (dragPlanIndex === null) return;
    movePlan(dragPlanIndex, targetIndex);
    setDragPlanIndex(null);
  };

  return (
    // Subtle rose wash - this editor is global (one plan path shared by every config), unlike the
    // Semester Config / Role Mappings / Semester Cleanup Roles sections which scope to whichever
    // config is selected and share their own (indigo) tint - see SwitchSemester.tsx's Section.
    <div className="rounded-lg border border-zinc-800 bg-rose-950/20 overflow-hidden">
      <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
        <ArrowLeftRight className="w-4 h-4 text-rose-400" />
        <h2 className="text-sm font-bold text-zinc-100">Switch Plans</h2>
        <span className="ml-auto text-[11px] text-zinc-600">
          {plans.length === 0 ? "None configured" : `${plans.length} plan${plans.length !== 1 ? "s" : ""}`}
        </span>
      </div>
      <div className="px-4 py-4 space-y-3">
        <p className="text-xs text-zinc-500">
          A Plan bundles the steps one calendar semester boundary actually needs - e.g. three simultaneous
          year-cohort switches, or a switch alongside a fresh cohort's Setup. Selected as one unit in Run.
          Order here is the perpetual cycle: a plan may only run when it's the one right after the last
          completed plan, wrapping back to the top after the last.
        </p>
        <div className="space-y-1.5">
          {plans.map((plan, i) => (
            <PlanCard key={plan.id} plan={plan} index={i} expanded={expandedId === plan.id}
              configs={configs} channels={channels} onToggle={() => toggle(plan.id)} onChange={p => updatePlan(i, p)} onRemove={() => removePlan(i)}
              dragging={dragPlanIndex === i} onDragStart={() => setDragPlanIndex(i)}
              onDragEnd={() => setDragPlanIndex(null)} onDropHere={() => dropPlan(i)} />
          ))}
        </div>
        <button onClick={addPlan}
          className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded text-xs font-bold uppercase tracking-wider border border-zinc-700 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200 transition-colors">
          <Plus className="w-3.5 h-3.5" /> New Plan
        </button>
      </div>
    </div>
  );
}

export { stepLabel };
