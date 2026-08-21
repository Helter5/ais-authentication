import { useEffect, useRef, useState } from "react";
import {
  History, ArrowLeftRight, ChevronDown, ChevronRight,
  Loader2, XCircle, RotateCcw, X, Eye, EyeOff, MapPin, ChevronLeft, Hash,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { ModalOverlay } from "@/components/ui/modal-overlay";
import { adminApi, apiErrorMessage } from "@/lib/api";
import type { SwitchHistoryEntry, MigrationGroup, VisibilityRow, StepDetail, RevertStatus, SwitchPlan } from "@/lib/api";
import { roleColor, type SemesterRole } from "@/components/semester/SemesterPickers";
import type { RunProgress } from "@/pages/SwitchSemester";
import { useToast } from "@/components/ui/toast";

const HISTORY_PAGE_SIZE = 10;

function formatWhen(iso: string) {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

function StatusBadge({ status }: { status: RevertStatus }) {
  if (status === "NONE") return null;
  if (status === "FULL") {
    return (
      <span className="text-[10px] font-semibold text-emerald-400 bg-emerald-500/10 border border-emerald-500/30 px-1.5 py-0.5 rounded flex-shrink-0">
        fully reverted
      </span>
    );
  }
  return (
    <span className="text-[10px] font-semibold text-amber-400 bg-amber-500/10 border border-amber-500/30 px-1.5 py-0.5 rounded flex-shrink-0">
      partially reverted
    </span>
  );
}

function RoleBadge({ id, roles }: { id: string | null; roles: SemesterRole[] }) {
  if (!id) return <span className="text-[11px] text-zinc-600 italic">removed</span>;
  const role = roles.find(r => r.id === id);
  if (!role) {
    return (
      <span className="inline-flex items-center gap-1 text-[11px] text-red-400 bg-red-900/20 border border-red-700/40 px-1.5 py-0.5 rounded line-through">
        deleted role
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 text-[11px] text-zinc-300 bg-zinc-800 border border-zinc-700 px-1.5 py-0.5 rounded">
      <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: roleColor(role.color) }} />
      @{role.name}
    </span>
  );
}

function GroupRow({ group, roles, selected, onToggle }: {
  group: MigrationGroup; roles: SemesterRole[]; selected: boolean; onToggle: () => void;
}) {
  return (
    <label className={cn("flex items-center gap-2 px-2 py-1.5 rounded border text-xs transition-colors",
      group.rolledBack ? "border-zinc-800 bg-zinc-900/40 opacity-40 cursor-not-allowed"
        : "border-zinc-800 hover:border-zinc-600 cursor-pointer")}>
      <input type="checkbox" disabled={group.rolledBack} checked={selected} onChange={onToggle} className="accent-indigo-500 flex-shrink-0" />
      <RoleBadge id={group.roleFromId} roles={roles} />
      <ArrowLeftRight className="w-3 h-3 text-zinc-600 flex-shrink-0" />
      <RoleBadge id={group.roleToId} roles={roles} />
      {group.keptFromRole && <span className="text-[10px] text-amber-400">(kept)</span>}
      <span className="ml-auto text-[10px] text-zinc-500 flex-shrink-0">
        {group.rolledBack ? "reverted" : `${group.remainingMembers}/${group.totalMembers} members`}
      </span>
    </label>
  );
}

function VisibilityRowItem({ row, selected, onToggle }: {
  row: VisibilityRow; selected: boolean; onToggle: () => void;
}) {
  return (
    <label className={cn("flex items-center gap-2 px-2 py-1.5 rounded border text-xs transition-colors",
      row.rolledBack ? "border-zinc-800 bg-zinc-900/40 opacity-40 cursor-not-allowed"
        : "border-zinc-800 hover:border-zinc-600 cursor-pointer")}>
      <input type="checkbox" disabled={row.rolledBack} checked={selected} onChange={onToggle} className="accent-indigo-500 flex-shrink-0" />
      {row.direction === "show"
        ? <Eye className="w-3 h-3 text-emerald-400 flex-shrink-0" />
        : <EyeOff className="w-3 h-3 text-zinc-500 flex-shrink-0" />}
      {row.isChannel && (
        <span title="Additional channel (not a category)" className="flex-shrink-0"><Hash className="w-3 h-3 text-sky-400" /></span>
      )}
      <span className="text-zinc-300 truncate">{row.categoryName ?? row.categoryId}</span>
      <span className="ml-auto text-[10px] text-zinc-500 flex-shrink-0">
        {row.rolledBack ? "reverted" : row.direction === "show" ? "was shown → will hide" : "was hidden → will show"}
      </span>
    </label>
  );
}

function StepSection({ step, roles, selectedGroups, selectedVisibility, onToggleGroup, onToggleVisibility }: {
  step: StepDetail; roles: SemesterRole[];
  selectedGroups: Set<string>; selectedVisibility: Set<number>;
  onToggleGroup: (key: string) => void; onToggleVisibility: (id: number) => void;
}) {
  const roleMappingGroups = step.roleGroups.filter(g => g.roleToId !== null);
  const cleanupGroups = step.roleGroups.filter(g => g.roleToId === null);
  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-950/40 p-3 space-y-2">
      <p className="text-[11px] font-semibold text-zinc-400">{step.stepLabel ?? `Step ${step.stepIndex + 1}`}</p>
      {roleMappingGroups.length > 0 && (
        <div>
          <p className="text-[10px] font-semibold text-zinc-600 uppercase tracking-wide mb-1">Role Mappings</p>
          <div className="space-y-1">
            {roleMappingGroups.map(g => (
              <GroupRow key={g.groupKey} group={g} roles={roles} selected={selectedGroups.has(g.groupKey)} onToggle={() => onToggleGroup(g.groupKey)} />
            ))}
          </div>
        </div>
      )}
      {cleanupGroups.length > 0 && (
        <div>
          <p className="text-[10px] font-semibold text-zinc-600 uppercase tracking-wide mb-1">Cleanup Roles</p>
          <div className="space-y-1">
            {cleanupGroups.map(g => (
              <GroupRow key={g.groupKey} group={g} roles={roles} selected={selectedGroups.has(g.groupKey)} onToggle={() => onToggleGroup(g.groupKey)} />
            ))}
          </div>
        </div>
      )}
      {step.visibilityRows.length > 0 && (
        <div>
          <p className="text-[10px] font-semibold text-zinc-600 uppercase tracking-wide mb-1">Channel Visibility</p>
          <div className="space-y-1 max-h-32 overflow-y-auto scrollbar-thin pr-1">
            {step.visibilityRows.map(v => (
              <VisibilityRowItem key={v.id} row={v} selected={selectedVisibility.has(v.id)} onToggle={() => onToggleVisibility(v.id)} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

interface RollbackSectionProps {
  guildId: string;
  entry: SwitchHistoryEntry;
  roles: SemesterRole[];
  onSettled: () => void;
}

function RollbackSection({ guildId, entry, roles, onSettled }: RollbackSectionProps) {
  const { toast } = useToast();
  const [steps, setSteps] = useState<StepDetail[]>([]);
  const [detailLoading, setDetailLoading] = useState(true);
  const [selectedGroups, setSelectedGroups] = useState<Set<string>>(new Set());
  const [selectedVisibility, setSelectedVisibility] = useState<Set<number>>(new Set());
  const [selectPosition, setSelectPosition] = useState(false);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<RunProgress | null>(null);
  const wasRunningRef = useRef(false);

  const loadDetail = () => {
    setDetailLoading(true);
    adminApi.getMigrationDetail(guildId, entry.migrationId)
      .then(d => setSteps(d.steps))
      .catch(() => setSteps([]))
      .finally(() => setDetailLoading(false));
  };

  useEffect(() => {
    loadDetail();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entry.migrationId]);

  // getRollbackProgress is guild-wide (only one rollback may run per guild at a time), not scoped
  // to this entry - without this check, expanding a DIFFERENT run right after rolling one back
  // would show the previous run's leftover progress/console here, since it's the same server-side
  // state. Only trust it once its own migrationId actually matches this entry.
  const relevantProgress = progress?.params?.migrationId === entry.migrationId ? progress : null;

  useEffect(() => {
    let cancelled = false;
    const poll = async () => {
      try {
        const p = await adminApi.getRollbackProgress(guildId);
        if (cancelled) return;
        setProgress(p);
      } catch { /* ignore */ }
    };
    poll();
    const id = setInterval(poll, relevantProgress?.running ? 1000 : 4000);
    return () => { cancelled = true; clearInterval(id); };
  }, [guildId, relevantProgress?.running]);

  useEffect(() => {
    if (relevantProgress?.running) {
      wasRunningRef.current = true;
    } else if (wasRunningRef.current) {
      wasRunningRef.current = false;
      loadDetail();
      onSettled();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [relevantProgress?.running]);

  const toggleGroup = (key: string) => {
    setSelectedGroups(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  const toggleVisibility = (id: number) => {
    setSelectedVisibility(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const activeGroups = steps.flatMap(s => s.roleGroups).filter(g => !g.rolledBack);
  const activeVisibility = steps.flatMap(s => s.visibilityRows).filter(v => !v.rolledBack);
  const nothingLeft = activeGroups.length === 0 && activeVisibility.length === 0
    && (!entry.canRevertPosition || entry.positionReverted);

  const selectedCount = selectedGroups.size + selectedVisibility.size + (selectPosition ? 1 : 0);

  const selectAll = () => {
    setSelectedGroups(new Set(activeGroups.map(g => g.groupKey)));
    setSelectedVisibility(new Set(activeVisibility.map(v => v.id)));
    setSelectPosition(entry.canRevertPosition && !entry.positionReverted);
  };

  const start = async () => {
    if (starting || progress?.running || selectedCount === 0) return;
    setStarting(true);
    setError(null);
    try {
      await adminApi.runRollback(
        guildId, entry.migrationId, Array.from(selectedGroups), Array.from(selectedVisibility), selectPosition);
      toast("Rollback started.");
      setProgress(await adminApi.getRollbackProgress(guildId));
      setSelectedGroups(new Set());
      setSelectedVisibility(new Set());
      setSelectPosition(false);
    } catch (e: unknown) {
      const msg = apiErrorMessage(e, "Failed to start rollback.");
      setError(msg);
      toast(msg, "error");
    } finally {
      setStarting(false);
    }
  };

  const isRunningThis = Boolean(relevantProgress?.running);

  return (
    <div className="space-y-3">
      {detailLoading ? (
        <div className="flex items-center gap-2 text-xs text-zinc-500 py-2"><Loader2 className="w-3.5 h-3.5 animate-spin" /> Loading changes…</div>
      ) : nothingLeft ? (
        <p className="text-xs text-zinc-600 italic py-1">Nothing left to revert — this run is fully reverted.</p>
      ) : (
        <>
          <div className="flex items-center justify-between">
            <p className="text-[11px] text-zinc-500">Select exactly what to revert, per step — nothing is bundled automatically.</p>
            <button type="button" onClick={selectAll}
              className="text-[11px] font-semibold text-indigo-400 hover:text-indigo-300 transition-colors flex-shrink-0">
              Select all
            </button>
          </div>

          <div className="space-y-2">
            {steps.map(step => (
              <StepSection key={step.stepIndex} step={step} roles={roles}
                selectedGroups={selectedGroups} selectedVisibility={selectedVisibility}
                onToggleGroup={toggleGroup} onToggleVisibility={toggleVisibility} />
            ))}
          </div>

          {entry.canRevertPosition && !entry.positionReverted && (
            <div>
              <p className="text-[10px] font-semibold text-zinc-500 uppercase tracking-wide mb-1">Plan Position</p>
              <label className="flex items-center gap-2 px-2 py-1.5 rounded border border-zinc-800 hover:border-zinc-600 text-xs cursor-pointer transition-colors">
                <input type="checkbox" checked={selectPosition} onChange={e => setSelectPosition(e.target.checked)}
                  className="accent-indigo-500 flex-shrink-0" />
                <MapPin className="w-3 h-3 text-zinc-500 flex-shrink-0" />
                <span className="text-zinc-300">Move tracked plan position back to before this run</span>
              </label>
            </div>
          )}

          <button onClick={start}
            disabled={isRunningThis || starting || selectedCount === 0}
            className={cn("w-full flex items-center justify-center gap-2 py-2 rounded text-xs font-bold uppercase tracking-wider transition-colors",
              selectedCount > 0 && !isRunningThis && !starting
                ? "bg-rose-700/80 hover:bg-rose-600 text-white"
                : "bg-zinc-800 text-zinc-600 cursor-not-allowed")}>
            {isRunningThis || starting
              ? <><Loader2 className="w-3.5 h-3.5 animate-spin" /> {isRunningThis ? "Rolling back…" : "Starting…"}</>
              : <><RotateCcw className="w-3.5 h-3.5" /> Roll back {selectedCount || ""} selected</>}
          </button>
        </>
      )}

      {error && (
        <div className="flex items-start gap-1.5 p-2 bg-red-500/10 border border-red-500/20 rounded text-red-400 text-[11px]">
          <XCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />{error}
        </div>
      )}

      {relevantProgress && relevantProgress.logs.length > 0 && (
        <div className="rounded bg-zinc-950 border border-zinc-800 max-h-32 overflow-y-auto scrollbar-thin p-2 font-mono text-[10px] space-y-0.5">
          {relevantProgress.logs.map((line, i) => (
            <div key={i} className={cn(line.includes("[ERROR]") ? "text-red-400" : line.includes("[WARN]") ? "text-yellow-300" : "text-green-400")}>
              {line}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

interface SemesterHistoryModalProps {
  open: boolean;
  onClose: () => void;
  guildId: string;
  plans: SwitchPlan[];
  roles: SemesterRole[];
  currentPlanName: string | null;
  onCurrentPlanChanged: () => void;
}

export function SemesterHistoryModal({
  open, onClose, guildId, plans, roles, currentPlanName, onCurrentPlanChanged,
}: SemesterHistoryModalProps) {
  const { toast } = useToast();
  const [history, setHistory] = useState<SwitchHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [overrideId, setOverrideId] = useState("");
  const [overrideConfirm, setOverrideConfirm] = useState(false);
  const [overrideSaving, setOverrideSaving] = useState(false);
  const [page, setPage] = useState(1);

  const loadHistory = () => {
    setLoading(true);
    adminApi.getSwitchSemesterHistory(guildId)
      .then(setHistory)
      .catch(() => setHistory([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (!open) return;
    loadHistory();
    setExpandedId(null);
    setOverrideConfirm(false);
    setOverrideId("");
    setPage(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, guildId]);

  if (!open) return null;

  const toggleExpand = (entry: SwitchHistoryEntry) => {
    if (entry.status === "FULL") return;
    setExpandedId(prev => prev === entry.migrationId ? null : entry.migrationId);
  };

  const handleSettled = () => {
    loadHistory();
    onCurrentPlanChanged();
  };

  const confirmOverride = async () => {
    if (!overrideId) return;
    setOverrideSaving(true);
    try {
      await adminApi.setCurrentPlan(guildId, overrideId);
      toast(`Current plan manually set to ${plans.find(p => p.id === overrideId)?.name ?? overrideId}.`);
      onCurrentPlanChanged();
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to set current plan."), "error");
    } finally {
      setOverrideSaving(false);
      setOverrideConfirm(false);
    }
  };

  return (
    <ModalOverlay onClose={onClose} panelClassName="w-full max-w-2xl max-h-[85vh] flex flex-col overflow-hidden">
      <div className="flex items-start gap-3 p-5 border-b border-zinc-800 flex-shrink-0">
        <div className="w-9 h-9 rounded-lg bg-indigo-500/15 border border-indigo-500/25 flex items-center justify-center flex-shrink-0">
          <History className="w-4 h-4 text-indigo-400" />
        </div>
        <div className="flex-1">
          <h2 className="text-base font-bold text-zinc-100">Switch History &amp; Rollback</h2>
          <p className="text-xs text-zinc-500 mt-0.5">
            Tracked current plan:{" "}
            <span className="font-mono text-zinc-300">{currentPlanName ?? "— not set —"}</span>
          </p>
        </div>
        <button onClick={onClose} className="text-zinc-500 hover:text-zinc-200 transition-colors">
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="p-5 overflow-y-auto scrollbar-thin space-y-5 flex-1 min-h-0">
        {/* Manual override */}
        <div className="rounded-lg border border-zinc-800 bg-zinc-900 p-3 space-y-2">
          <p className="text-xs font-semibold text-zinc-400">Manual override</p>
          <p className="text-[11px] text-zinc-600">
            Escape hatch for a desynced plan position — sets it directly, no role or channel changes. Prefer a plan run or rollback instead when either applies.
          </p>
          <div className="flex items-center gap-2">
            <select value={overrideId}
              onChange={e => { setOverrideId(e.target.value); setOverrideConfirm(false); }}
              className="flex-1 min-w-0 px-2.5 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 outline-none focus:border-amber-500">
              <option value="">Select plan…</option>
              {plans.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
            {!overrideConfirm ? (
              <button
                disabled={!overrideId}
                onClick={() => setOverrideConfirm(true)}
                className="px-2.5 py-1.5 rounded text-xs font-semibold border border-amber-500/40 text-amber-300 hover:bg-amber-500/10 transition-colors disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0">
                Override…
              </button>
            ) : (
              <div className="flex items-center gap-1.5 flex-shrink-0">
                <button disabled={overrideSaving} onClick={confirmOverride}
                  className="px-2.5 py-1.5 rounded text-xs font-bold bg-amber-600 hover:bg-amber-500 text-white transition-colors disabled:opacity-60">
                  {overrideSaving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : "Confirm"}
                </button>
                <button onClick={() => setOverrideConfirm(false)} className="px-2 py-1.5 text-xs text-zinc-500 hover:text-zinc-300">
                  Cancel
                </button>
              </div>
            )}
          </div>
        </div>

        {/* History list */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-semibold text-zinc-400">Recent runs</p>
            <p className="text-[11px] text-zinc-600">Kept for 14 days, then removed automatically.</p>
          </div>
          {loading ? (
            <div className="flex items-center gap-2 text-xs text-zinc-500 py-4"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</div>
          ) : history.length === 0 ? (
            <p className="text-xs text-zinc-600 text-center py-6">No runs recorded yet.</p>
          ) : (
            <>
              <div className="space-y-2">
                {history.slice((page - 1) * HISTORY_PAGE_SIZE, page * HISTORY_PAGE_SIZE).map(entry => {
                  const isExpanded = expandedId === entry.migrationId;
                  const isFull = entry.status === "FULL";
                  return (
                    <div key={entry.migrationId} className={cn("rounded-lg border overflow-hidden transition-colors",
                      isExpanded ? "border-indigo-500/60 bg-indigo-500/[0.04]" : "border-zinc-800 bg-zinc-900/60")}>
                      <button onClick={() => toggleExpand(entry)} disabled={isFull}
                        className={cn("w-full flex flex-col gap-1 px-3 py-2.5 text-left transition-colors",
                          isExpanded && "bg-indigo-500/[0.06]",
                          isFull ? "cursor-not-allowed opacity-60" : "hover:bg-zinc-800/40")}>
                        <div className="flex items-center gap-2">
                          {isFull
                            ? <span className="w-3.5 h-3.5 flex-shrink-0" />
                            : isExpanded
                              ? <ChevronDown className="w-3.5 h-3.5 text-indigo-400 flex-shrink-0" />
                              : <ChevronRight className="w-3.5 h-3.5 text-zinc-500 flex-shrink-0" />}
                          <span className={cn("text-[10px] font-semibold px-1.5 py-0.5 rounded flex-shrink-0",
                            entry.operationType === "plan" ? "text-indigo-300 bg-indigo-500/10" : "text-amber-300 bg-amber-500/10")}>
                            {entry.operationType === "plan" ? "Plan" : "Setup"}
                          </span>
                          <span className={cn("text-xs font-mono truncate", isExpanded ? "text-white" : "text-zinc-200")}>
                            {entry.label}
                          </span>
                          <span className="ml-auto text-xs text-zinc-400 flex-shrink-0">{formatWhen(entry.createdAt)}</span>
                          {entry.actorName && <span className="text-[10px] text-zinc-600 flex-shrink-0">by {entry.actorName}</span>}
                          <StatusBadge status={entry.status} />
                        </div>
                        {entry.rolledBackByActorName && (
                          <p className="pl-[1.375rem] text-[10px] text-zinc-600">
                            <RotateCcw className="w-2.5 h-2.5 inline mr-1 -mt-0.5" />
                            rolled back by <span className="text-zinc-400">{entry.rolledBackByActorName}</span>
                          </p>
                        )}
                      </button>
                      {isExpanded && (
                        <div className="border-t border-zinc-800 px-3 py-3">
                          <RollbackSection guildId={guildId} entry={entry} roles={roles} onSettled={handleSettled} />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              {history.length > HISTORY_PAGE_SIZE && (
                <div className="flex items-center justify-between mt-3 pt-2 border-t border-zinc-800">
                  <span className="text-[11px] text-zinc-600">
                    {(page - 1) * HISTORY_PAGE_SIZE + 1}–{Math.min(page * HISTORY_PAGE_SIZE, history.length)} of {history.length}
                  </span>
                  <div className="flex items-center gap-1.5">
                    <button onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1}
                      className="flex items-center justify-center w-7 h-7 rounded border border-zinc-700 bg-zinc-800 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200 transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                      <ChevronLeft className="w-3.5 h-3.5" />
                    </button>
                    <span className="text-[11px] text-zinc-500 px-1">
                      {page} / {Math.ceil(history.length / HISTORY_PAGE_SIZE)}
                    </span>
                    <button onClick={() => setPage(p => Math.min(Math.ceil(history.length / HISTORY_PAGE_SIZE), p + 1))}
                      disabled={page >= Math.ceil(history.length / HISTORY_PAGE_SIZE)}
                      className="flex items-center justify-center w-7 h-7 rounded border border-zinc-700 bg-zinc-800 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200 transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                      <ChevronRight className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </ModalOverlay>
  );
}
