import { useEffect, useRef, useState } from "react";
import { adminApi, apiErrorMessage } from "@/lib/api";
import type { SwitchPlan } from "@/lib/api";
import { SemesterRunPanel } from "@/components/semester/SemesterRunPanel";
import { ConfirmSwitchModal, ConfirmSetupModal, ModeInfoModal } from "@/components/semester/SemesterModals";
import { SemesterHistoryModal } from "@/components/semester/SemesterHistoryPanel";
import { SemesterPlanEditor } from "@/components/semester/SemesterPlanEditor";
import {
  CategoryMultiSelect,
  RoleMultiSelect,
  RoleSelect,
  roleColor,
  type SemesterCategory,
  type SemesterRole,
} from "@/components/semester/SemesterPickers";
import {
  Plus, X, Loader2, CheckCircle2,
  Trash2, CalendarDays, ArrowLeftRight,
  Users, Eye, EyeOff, XCircle, Smile, History,
  GripVertical, SlidersHorizontal, Snowflake, Sun,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useSelectedGuildId, LogChannelPicker } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";
import { firstIncompleteStepLabel } from "@/lib/semesterPlanValidation";

// ── Types ─────────────────────────────────────────────────────────────────────

type RoleMapping = {
  fromRoleId: string;
  toRoleIds: string[];
  conditionRoleIds?: string[];
  keepFromRole?: boolean;
};

export type SemesterType = "WINTER" | "SUMMER";

export type SemesterConfig = {
  id: string;
  name: string;
  categoryIds: string[];
  everyoneViewChannel: boolean;
  roleMappings: RoleMapping[];
  semesterRoles: string[];
  semesterType: SemesterType | null;
};

type Category = SemesterCategory;
type Role = SemesterRole;
export type SemesterChannel = { id: string; name: string; position: number };
export type RunProgress = {
  running: boolean;
  progress: number;
  logs: string[];
  startedAt: string | null;
  status?: "running" | "success" | "partial" | "failed";
  operation?: "plan" | "setup" | "rollback";
  params?: {
    planId?: string;
    semesterName?: string;
    visible?: boolean;
    everyoneViewChannel?: boolean;
    clearRoles?: boolean;
    migrationId?: string;
  };
  completedSteps?: string[];
};

function uid() { return Math.random().toString(36).slice(2); }

const DEFAULT_CONFIG = (): SemesterConfig => ({
  id: uid(),
  name: "New Semester",
  categoryIds: [],
  everyoneViewChannel: false,
  roleMappings: [],
  semesterRoles: [],
  semesterType: null,
});

// ── Section header ─────────────────────────────────────────────────────────────

// Subtle indigo wash (vs. Switch Plans' rose one, see SemesterPlanEditor) - these three Sections
// (Semester Config, Role Mappings, Semester Cleanup Roles) all scope to one selected config, so a
// shared background tint ties them together as a family distinct from the plan-wide, all-configs
// Switch Plans editor above them.
function Section({ icon: Icon, color, title, children }: {
  icon: React.ElementType; color: string; title: string; children: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-zinc-800 bg-indigo-950/25 overflow-hidden">
      <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
        <Icon className="w-4 h-4" style={{ color }} />
        <h2 className="text-sm font-bold text-zinc-100">{title}</h2>
      </div>
      <div className="px-4 py-4 space-y-4">{children}</div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export function SwitchSemesterModule() {
  const guildId = useSelectedGuildId();

  // Access
  const [access, setAccess] = useState<boolean | null>(null);
  const [accessReason, setAccessReason] = useState<string | null>(null);

  // Config state
  const [categories, setCategories] = useState<Category[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [channels, setChannels] = useState<SemesterChannel[]>([]);
  const [configs, setConfigs] = useState<SemesterConfig[]>([]);
  // Every semester's edits live here, keyed by id, until the one global Save Changes button
  // commits the whole list - switching which semester you're looking at (selectedId) no longer
  // throws away whatever you were mid-editing on a different one.
  const [configsDraft, setConfigsDraft] = useState<SemesterConfig[]>([]);
  const [plans, setPlans] = useState<SwitchPlan[]>([]);
  const [plansDraft, setPlansDraft] = useState<SwitchPlan[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const { toast } = useToast();

  // Role mapping add form
  const [newFrom, setNewFrom] = useState<string | null>(null);
  const [newTo, setNewTo] = useState<string[]>([]);
  const [newCondition, setNewCondition] = useState<string[]>([]);
  const [newKeepFrom, setNewKeepFrom] = useState(false);

  // Run state
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [runStarting, setRunStarting] = useState(false);
  const [runError, setRunError] = useState<string | null>(null);
  const [showConfirm, setShowConfirm] = useState(false);
  const [ssProgress, setSsProgress] = useState<RunProgress | null>(null);
  const [runMode, setRunMode] = useState<"switch" | "setup">("switch");
  const [setupSemester, setSetupSemester] = useState("");
  const [setupVisible, setSetupVisible] = useState(true);
  const [setupClearRoles, setSetupClearRoles] = useState(false);
  const [setupStarting, setSetupStarting] = useState(false);
  const [setupProgress, setSetupProgress] = useState<RunProgress | null>(null);
  const [showSetupConfirm, setShowSetupConfirm] = useState(false);
  const [showModeInfo, setShowModeInfo] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [currentPlanName, setCurrentPlanName] = useState<string | null>(null);
  const [nextPlan, setNextPlan] = useState<{
    currentPlanId: string | null; currentPlanName: string | null; currentSemesterType: SemesterType | null;
    nextPlanId: string | null; nextPlanName: string | null; nextSemesterType: SemesterType | null;
  } | null>(null);
  const consoleEndRef = useRef<HTMLDivElement | null>(null);
  const consoleContainerRef = useRef<HTMLDivElement | null>(null);
  const isConsoleAtBottomRef = useRef(true);
  // Auto-collapses a finished run's console the first time this page loads it (so a run that
  // completed hours/days ago doesn't sit there dumped open forever, requiring a manual click every
  // single visit) - but only once per mount, so a run that finishes WHILE you're watching it stays
  // visible right when it completes, and "Show output" (clearConsole(null)) isn't immediately
  // fought by this on the next poll tick.
  const autoCollapsedRef = useRef(false);
  const runModeRef = useRef<"switch" | "setup">("switch");
  useEffect(() => { runModeRef.current = runMode; }, [runMode]);
  const [clearedStartedAt, setClearedStartedAt] = useState<string | null>(
    () => localStorage.getItem(`semester_console_cleared_${guildId}`) ?? null
  );
  // Regular admins just want to run a switch - Plans/Role Mappings/config editing is Advanced-only,
  // off by default, remembered per guild so it doesn't reset every visit.
  const [advanced, setAdvanced] = useState(
    () => localStorage.getItem(`semester_advanced_${guildId}`) === "1"
  );

  useEffect(() => {
    setClearedStartedAt(localStorage.getItem(`semester_console_cleared_${guildId}`) ?? null);
    setAdvanced(localStorage.getItem(`semester_advanced_${guildId}`) === "1");
  }, [guildId]);

  const toggleAdvanced = () => {
    setAdvanced(prev => {
      const next = !prev;
      localStorage.setItem(`semester_advanced_${guildId}`, next ? "1" : "0");
      return next;
    });
  };

  const clearConsole = (startedAt: string | null) => {
    setClearedStartedAt(startedAt);
    if (startedAt) localStorage.setItem(`semester_console_cleared_${guildId}`, startedAt);
    else localStorage.removeItem(`semester_console_cleared_${guildId}`);
  };

  const handleConsoleScroll = () => {
    const el = consoleContainerRef.current;
    if (!el) return;
    isConsoleAtBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
  };

  const recheckAccess = () => {
    if (!guildId) return;
    adminApi.getSemesterAccess(guildId)
      .then(r => { setAccess(r.allowed); setAccessReason(r.reason ?? null); })
      .catch(() => setAccess(false));
  };

  const refreshCurrentPlan = () => {
    if (!guildId) return;
    adminApi.getCurrentPlan(guildId)
      .then(r => { setCurrentPlanName(r.currentPlanName); })
      .catch(() => { /* ignore */ });
    adminApi.getNextPlan(guildId)
      .then(setNextPlan)
      .catch(() => { /* ignore */ });
  };

  // Access check
  useEffect(() => {
    let cancelled = false;
    setAccess(null);
    setAccessReason(null);
    setCategories([]);
    setRoles([]);
    setChannels([]);
    setConfigs([]);
    setConfigsDraft([]);
    setPlans([]);
    setPlansDraft([]);
    setSelectedId(null);
    setLoading(true);
    setSaving(false);
    setDeleteId(null);
    setNewFrom(null);
    setNewTo([]);
    setSelectedPlanId("");
    setRunStarting(false);
    setRunError(null);
    setShowConfirm(false);
    setSsProgress(null);
    autoCollapsedRef.current = false;
    setRunMode("switch");
    setSetupSemester("");
    setSetupVisible(true);
    setSetupClearRoles(false);
    setSetupStarting(false);
    setSetupProgress(null);
    setShowSetupConfirm(false);
    setShowModeInfo(false);
    setShowHistory(false);
    setCurrentPlanName(null);
    setNextPlan(null);
    if (!guildId) {
      setAccess(false);
      setLoading(false);
      return () => { cancelled = true; };
    }
    adminApi.getSemesterAccess(guildId)
      .then(r => {
        if (cancelled) return;
        setAccess(r.allowed);
        setAccessReason(r.reason ?? null);
        if (!r.allowed) setLoading(false);
      })
      .catch(() => {
        if (!cancelled) {
          setAccess(false);
          setLoading(false);
        }
      });
    return () => { cancelled = true; };
  }, [guildId]);

  // Load configs
  useEffect(() => {
    let cancelled = false;
    if (!guildId || access === false) return () => { cancelled = true; };
    if (access === null) return () => { cancelled = true; };
    setLoading(true);
    Promise.all([
      adminApi.getDiscordCategories(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
      adminApi.getSemesterConfigs(guildId),
      adminApi.getCurrentPlan(guildId),
      adminApi.getNextPlan(guildId),
    ]).then(([cats, rls, chans, settings, current, next]) => {
      if (cancelled) return;
      setCategories(cats);
      setRoles(rls);
      setChannels(chans);
      setCurrentPlanName(current.currentPlanName);
      setNextPlan(next);
      const s = settings as { configs?: SemesterConfig[]; logActions?: boolean; plans?: SwitchPlan[]; planPath?: string[] };
      const normalizedConfigs = (s.configs ?? []).map(config => ({
        ...config,
        everyoneViewChannel: config.everyoneViewChannel === true,
        semesterType: config.semesterType === "WINTER" || config.semesterType === "SUMMER" ? config.semesterType : null,
      }));
      setConfigs(normalizedConfigs);
      setConfigsDraft(normalizedConfigs);
      setPlans(s.plans ?? []);
      setPlansDraft(s.plans ?? []);
      if (normalizedConfigs.length > 0) {
        setSelectedId(normalizedConfigs[0].id);
        setSetupSemester(normalizedConfigs[0].name);
      }
    }).catch(error => { if (!cancelled) console.error(error); }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [guildId, access]);

  // Poll progress
  useEffect(() => {
    let cancelled = false;
    if (!guildId || access === false) return () => { cancelled = true; };
    const poll = async () => {
      try {
        const [planRun, setupRun] = await Promise.all([
          adminApi.getPlanProgress(guildId),
          adminApi.getSemesterSetupProgress(guildId),
        ]);
        if (cancelled) return;
        // First poll of this page load only: if the run it finds is already finished (not one that
        // just completed while you were watching), auto-collapse its console so a run from hours or
        // days ago doesn't sit there dumped open on every visit - "Show output" still recalls it.
        if (!autoCollapsedRef.current) {
          autoCollapsedRef.current = true;
          const relevant = runModeRef.current === "switch" ? planRun : setupRun;
          if (!relevant.running && relevant.startedAt) {
            clearConsole(relevant.startedAt);
          }
        }
        // A completed plan/setup may have moved the tracked position - keep the header badge current.
        if (planRun.status !== ssProgress?.status || setupRun.status !== setupProgress?.status) {
          refreshCurrentPlan();
        }
        // Only follow new output down - re-scrolling on every idle tick (nothing new arrived) is
        // what made this fight a user trying to scroll up to read earlier lines.
        const grew = planRun.logs.length > (ssProgress?.logs.length ?? 0)
          || setupRun.logs.length > (setupProgress?.logs.length ?? 0);
        setSsProgress(planRun);
        setSetupProgress(setupRun);
        if (grew && isConsoleAtBottomRef.current && consoleEndRef.current) {
          consoleEndRef.current.scrollIntoView({ behavior: "smooth" });
        }
      } catch { /* ignore */ }
    };
    poll();
    const interval = ssProgress?.running || setupProgress?.running ? 1000 : 5000;
    const id = setInterval(poll, interval);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
    // Intentionally keyed on *.running only (not .status/refreshCurrentPlan) - those are read fresh
    // from the closure each tick via the polling interval itself, and including them would tear
    // down/restart the interval on every status change instead of just running-state changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [guildId, access, ssProgress?.running, setupProgress?.running]);

  // The Plan Path fully determines what "Select switch" should default to - no manual From/To
  // picking needed once both a tracked position and a path exist. Admin can still pick a different
  // plan from the dropdown; the backend guard is what actually enforces path order at submit time.
  useEffect(() => {
    if (runMode !== "switch" || anyRunActive) return;
    if (nextPlan?.nextPlanId) {
      setSelectedPlanId(nextPlan.nextPlanId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nextPlan, runMode]);

  const persist = async (nextConfigs: SemesterConfig[], nextPlans: SwitchPlan[], nextPlanPath: string[]) => {
    if (!guildId) return;
    setSaving(true);
    try {
      // Always logged to Access Logs - not user-configurable, so this is hardcoded rather than a draft toggle.
      await adminApi.saveSemesterConfigs(guildId, { configs: nextConfigs, logActions: true, plans: nextPlans, planPath: nextPlanPath });
      toast("Saved.");
      refreshCurrentPlan();
    } catch {
      toast("Failed to save.", "error");
    } finally {
      setSaving(false);
    }
  };

  // The config currently shown/edited in the center panel - just a lookup into configsDraft, not
  // its own piece of state, so it can never fall out of sync with what Save Changes would persist.
  const draft = configsDraft.find(c => c.id === selectedId) ?? null;

  const newCfg = () => {
    const c = DEFAULT_CONFIG();
    setConfigsDraft(prev => [...prev, c]);
    setSelectedId(c.id);
    setNewFrom(null);
    setNewTo([]);
    setNewCondition([]);
    setNewKeepFrom(false);
    setEditMappingIdx(null);
  };

  const selectCfg = (cfg: SemesterConfig) => {
    setSelectedId(cfg.id);
    if (runMode === "setup") setSetupSemester(cfg.name);
    setNewFrom(null);
    setNewTo([]);
    setNewCondition([]);
    setNewKeepFrom(false);
    setEditMappingIdx(null);
  };

  const selectSetupSemester = (name: string) => {
    setSetupSemester(name);
    const config = configs.find(c => c.name === name);
    if (config) selectCfg(config);
  };

  const upd = <K extends keyof SemesterConfig>(key: K, value: SemesterConfig[K]) => {
    setConfigsDraft(prev => prev.map(c => c.id === selectedId ? { ...c, [key]: value } : c));
  };

  // One global Save Changes button commits every draft together (every semester config being
  // edited, not just the one currently selected, plus the Switch Plans list) - Plans has no save
  // button of its own, see SemesterPlanEditor.
  const save = async () => {
    setConfigs(configsDraft);
    setPlans(plansDraft);
    const nextPlanPath = plansDraft.map(p => p.id);
    await persist(configsDraft, plansDraft, nextPlanPath);
  };

  // Discards every unsaved edit (configs and plans both) back to the last-persisted state - if
  // selectedId was only a not-yet-saved config, draft resolves to null and the empty state shows.
  const revert = () => {
    setConfigsDraft(configs);
    setPlansDraft(plans);
    toast("Changes reverted.");
  };

  const deleteCfg = async (id: string) => {
    const nextConfigs = configsDraft.filter(c => c.id !== id);
    setConfigsDraft(nextConfigs);
    setConfigs(nextConfigs);
    if (selectedId === id) setSelectedId(null);
    setDeleteId(null);
    const nextPlanPath = plansDraft.map(p => p.id);
    setPlans(plansDraft);
    await persist(nextConfigs, plansDraft, nextPlanPath);
  };

  const [editMappingIdx, setEditMappingIdx] = useState<number | null>(null);
  const [dragMappingIndex, setDragMappingIndex] = useState<number | null>(null);

  const addMapping = () => {
    if (!newFrom || newTo.length === 0 || !draft) return;
    const mapping: RoleMapping = { fromRoleId: newFrom, toRoleIds: newTo };
    if (newCondition.length > 0) mapping.conditionRoleIds = newCondition;
    if (newKeepFrom) mapping.keepFromRole = true;
    if (editMappingIdx !== null) {
      const updated = draft.roleMappings.map((m, i) => i === editMappingIdx ? mapping : m);
      upd("roleMappings", updated);
      setEditMappingIdx(null);
    } else {
      upd("roleMappings", [...draft.roleMappings, mapping]);
    }
    setNewFrom(null);
    setNewTo([]);
    setNewCondition([]);
    setNewKeepFrom(false);
  };

  const editMapping = (i: number) => {
    if (!draft) return;
    const m = draft.roleMappings[i];
    setNewFrom(m.fromRoleId);
    setNewTo([...m.toRoleIds]);
    setNewCondition(m.conditionRoleIds ? [...m.conditionRoleIds] : []);
    setNewKeepFrom(m.keepFromRole ?? false);
    setEditMappingIdx(i);
  };

  const cancelEdit = () => {
    setEditMappingIdx(null);
    setNewFrom(null);
    setNewTo([]);
    setNewCondition([]);
    setNewKeepFrom(false);
  };

  const removeMapping = (i: number) => {
    if (!draft) return;
    if (editMappingIdx === i) cancelEdit();
    else if (editMappingIdx !== null && i < editMappingIdx) setEditMappingIdx(editMappingIdx - 1);
    upd("roleMappings", draft.roleMappings.filter((_, idx) => idx !== i));
  };

  // Mappings run top-to-bottom (each sees the result of all previous ones - see the note above the
  // list), so reordering changes behavior, not just display. Move instead of delete-and-recreate.
  const moveMapping = (from: number, to: number) => {
    if (!draft || from === to || to < 0 || to >= draft.roleMappings.length) return;
    const editedMapping = editMappingIdx !== null ? draft.roleMappings[editMappingIdx] : null;
    const next = [...draft.roleMappings];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    if (editedMapping) setEditMappingIdx(next.indexOf(editedMapping));
    upd("roleMappings", next);
  };

  const dropMapping = (targetIndex: number) => {
    if (dragMappingIndex === null) return;
    moveMapping(dragMappingIndex, targetIndex);
    setDragMappingIndex(null);
  };

  const roleName = (id: string) => roles.find(r => r.id === id)?.name ?? null;
  const roleCol = (id: string) => roleColor(roles.find(r => r.id === id)?.color);

  const openConfirmSwitch = () => {
    if (incompleteStepWarning) {
      toast(incompleteStepWarning, "error");
      return;
    }
    setShowConfirm(true);
  };

  const handleRun = async () => {
    if (!guildId || !selectedPlanId || runStarting) return;
    if (incompleteStepWarning) {
      setShowConfirm(false);
      toast(incompleteStepWarning, "error");
      return;
    }
    setShowConfirm(false);
    setRunError(null);
    setRunStarting(true);
    try {
      await adminApi.runPlan(guildId, selectedPlanId);
      const p = await adminApi.getPlanProgress(guildId);
      setSsProgress(p);
      toast("Plan started.");
    } catch (e: unknown) {
      const msg = apiErrorMessage(e, "Failed to start.");
      setRunError(msg);
      toast(msg, "error");
    } finally {
      setRunStarting(false);
    }
  };

  const handleSetup = async () => {
    if (!guildId || !setupSemester || setupStarting) return;
    setShowSetupConfirm(false);
    setRunError(null);
    setSetupStarting(true);
    try {
      await adminApi.runSemesterSetup(guildId, setupSemester, setupVisible, setupClearRoles);
      const p = await adminApi.getSemesterSetupProgress(guildId);
      setSetupProgress(p);
      toast("Semester setup started.");
    } catch (e: unknown) {
      const msg = apiErrorMessage(e, "Failed to start.");
      setRunError(msg);
      toast(msg, "error");
    } finally {
      setSetupStarting(false);
    }
  };

  const handleResume = async () => {
    if (!guildId || anyRunActive) return;
    setRunError(null);
    if (runMode === "switch") {
      const planId = ssProgress?.params?.planId;
      if (!planId) return;
      setRunStarting(true);
      setSelectedPlanId(planId);
      try {
        await adminApi.runPlan(guildId, planId, true);
        setSsProgress(await adminApi.getPlanProgress(guildId));
        toast("Plan resumed.");
      } catch (e: unknown) {
        const msg = apiErrorMessage(e, "Failed to resume.");
        setRunError(msg);
        toast(msg, "error");
      } finally {
        setRunStarting(false);
      }
      return;
    }

    const params = setupProgress?.params;
    if (!params?.semesterName || params.visible === undefined || params.clearRoles === undefined) return;
    setSetupStarting(true);
    setSetupSemester(params.semesterName);
    setSetupVisible(params.visible);
    setSetupClearRoles(params.clearRoles);
    try {
      await adminApi.runSemesterSetup(guildId, params.semesterName, params.visible, params.clearRoles, true);
      setSetupProgress(await adminApi.getSemesterSetupProgress(guildId));
      toast("Semester setup resumed.");
    } catch (e: unknown) {
      const msg = apiErrorMessage(e, "Failed to resume.");
      setRunError(msg);
      toast(msg, "error");
    } finally {
      setSetupStarting(false);
    }
  };

  const activeProgress = runMode === "switch" ? ssProgress : setupProgress;
  const setupConfig = configs.find(config => config.name === setupSemester);
  const selectedPlan = plans.find(p => p.id === selectedPlanId);
  const incompleteStepWarning = selectedPlan ? firstIncompleteStepLabel(selectedPlan) : null;
  const configsDirty = JSON.stringify(configsDraft) !== JSON.stringify(configs);
  const plansDirty = JSON.stringify(plansDraft) !== JSON.stringify(plans);
  const anyRunActive = Boolean(ssProgress?.running || setupProgress?.running);
  const isConsoleCleared = Boolean(activeProgress?.startedAt && activeProgress.startedAt === clearedStartedAt);
  const consoleLogs = isConsoleCleared ? [] : (activeProgress?.logs ?? []);
  const canResume = Boolean(
    !anyRunActive
    && activeProgress?.params
    && (activeProgress.status === "partial" || activeProgress.status === "failed")
  );

  // ── Early returns ──

  if (!guildId) {
    return (
      <div className="flex flex-col gap-6 md:pl-64 px-4 sm:px-6 pt-6">
        <div className="text-zinc-500 text-sm">No server selected.</div>
      </div>
    );
  }

  if (access === null) {
    return (
      <div className="flex flex-col gap-6 md:pl-64 px-4 sm:px-6 pt-6">
        <div className="flex items-center gap-2 text-zinc-500 text-sm"><Loader2 className="w-4 h-4 animate-spin" /> Checking access…</div>
      </div>
    );
  }

  if (access === false) {
    const isNoChannel = accessReason === 'no_channel';
    return (
      <div className="flex flex-col gap-6 md:pl-64 px-4 sm:px-6 pt-6">
        <div className={cn("flex items-start gap-3 p-4 rounded-lg text-sm border",
          isNoChannel ? "bg-amber-500/10 border-amber-500/20 text-amber-300" : "bg-red-500/10 border-red-500/20 text-red-400")}>
          {isNoChannel
            ? <CalendarDays className="w-4 h-4 shrink-0 mt-0.5" />
            : <XCircle className="w-4 h-4 shrink-0 mt-0.5" />}
          <div className="space-y-1">
            {isNoChannel ? (
              <>
                <p className="font-semibold">Semester log channel not configured.</p>
                <p className="text-amber-400/80 text-xs">A log channel is required before running a semester switch - set one under Log Channels below.</p>
              </>
            ) : (
              <p>Access denied. Admin or manager role required.</p>
            )}
          </div>
        </div>
        {isNoChannel && (
          <div className="rounded-lg border border-zinc-800 bg-zinc-900 p-4">
            <LogChannelPicker guildId={guildId} eventTypes={["SEMESTER_RECAP"]} title="Log Channels" onSaved={recheckAccess} />
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      {/* Header */}
      <div className="flex items-center gap-3 px-6 py-4 border-b border-zinc-800 flex-shrink-0">
        <CalendarDays className="w-5 h-5 text-indigo-400" />
        <h1 className="text-xl font-bold text-zinc-100 tracking-tight">Semester Management</h1>
        {anyRunActive && (
          <span className="flex items-center gap-1.5 text-[11px] font-semibold px-2.5 py-1 rounded-full border text-emerald-300 bg-emerald-500/10 border-emerald-500/30">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" /> Running
          </span>
        )}
        <button onClick={() => setShowHistory(true)}
          className="ml-auto flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-semibold border border-zinc-700 text-zinc-400 hover:text-zinc-200 hover:border-zinc-500 transition-colors">
          <History className="w-3.5 h-3.5" /> History &amp; Rollback
        </button>
        <button onClick={toggleAdvanced}
          className={cn("flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-semibold border transition-colors",
            advanced
              ? "border-indigo-500/50 text-indigo-300 bg-indigo-500/10 hover:bg-indigo-500/20"
              : "border-zinc-700 text-zinc-400 hover:text-zinc-200 hover:border-zinc-500")}>
          <SlidersHorizontal className="w-3.5 h-3.5" /> {advanced ? "Simple Mode" : "Advanced"}
        </button>
      </div>

      {loading ? (
        <div className="flex items-center gap-2 p-6 text-zinc-500 text-sm">
          <Loader2 className="w-4 h-4 animate-spin" /> Loading…
        </div>
      ) : !advanced ? (
        <div className="flex-1 flex items-start justify-center overflow-y-auto scrollbar-thin p-6">
          <div className="w-full max-w-md">
            <SemesterRunPanel
              standalone
              plans={plans}
              runMode={runMode}
              setRunMode={setRunMode}
              anyRunActive={anyRunActive}
              nextPlan={nextPlan}
              selectedPlanId={selectedPlanId}
              setSelectedPlanId={setSelectedPlanId}
              runStarting={runStarting}
              ssProgress={ssProgress}
              configs={configs}
              setupSemester={setupSemester}
              selectSetupSemester={selectSetupSemester}
              setupVisible={setupVisible}
              setSetupVisible={setSetupVisible}
              setupClearRoles={setupClearRoles}
              setSetupClearRoles={setSetupClearRoles}
              setupStarting={setupStarting}
              setupProgress={setupProgress}
              activeProgress={activeProgress}
              canResume={canResume}
              runError={runError}
              consoleLogs={consoleLogs}
              isConsoleCleared={isConsoleCleared}
              consoleEndRef={consoleEndRef}
              consoleContainerRef={consoleContainerRef}
              handleConsoleScroll={handleConsoleScroll}
              clearConsole={clearConsole}
              onShowConfirmSwitch={openConfirmSwitch}
              onShowConfirmSetup={() => setShowSetupConfirm(true)}
              onShowModeInfo={() => setShowModeInfo(true)}
              onResume={handleResume}
            />
          </div>
        </div>
      ) : (
        <div className="flex flex-1 min-h-0 overflow-hidden" style={{ height: "calc(100vh - 57px)" }}>

          {/* ── Left: config list ── */}
          <div className="w-52 flex-shrink-0 border-r border-zinc-800 flex flex-col">
            <div className="p-3 border-b border-zinc-800">
              <LogChannelPicker guildId={guildId} eventTypes={["SEMESTER_RECAP"]} title="Log Channel" combined />
            </div>
            <div className="p-3 border-b border-zinc-800">
              <button onClick={newCfg}
                className={cn("w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded text-xs font-bold uppercase tracking-wider border transition-all",
                  draft && !configs.some(c => c.id === draft.id)
                    ? "border-indigo-500/60 text-indigo-400 bg-indigo-500/10"
                    : "border-zinc-700 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200")}>
                <Plus className="w-3.5 h-3.5" /> New Semester
              </button>
            </div>
            <div className="flex-1 overflow-y-auto scrollbar-thin p-2 space-y-1">
              {configsDraft.length === 0 && (
                <p className="text-xs text-zinc-600 text-center py-4">No configs yet</p>
              )}
              {configsDraft.map(cfg => {
                const persisted = configs.find(c => c.id === cfg.id);
                const unsaved = !persisted || JSON.stringify(persisted) !== JSON.stringify(cfg);
                return (
                <div key={cfg.id}
                  className={cn("group flex items-center gap-2 px-3 py-2.5 rounded cursor-pointer transition-all border-l-2",
                    cfg.semesterType === "WINTER" ? "border-sky-500/70" : cfg.semesterType === "SUMMER" ? "border-amber-500/70" : "border-transparent",
                    selectedId === cfg.id ? "bg-zinc-700 text-zinc-100" : "text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200")}
                  onClick={() => selectCfg(cfg)}>
                  {cfg.semesterType === "WINTER" ? (
                    <span title="Winter" className="flex-shrink-0"><Snowflake className="w-3.5 h-3.5 text-sky-400" /></span>
                  ) : cfg.semesterType === "SUMMER" ? (
                    <span title="Summer" className="flex-shrink-0"><Sun className="w-3.5 h-3.5 text-amber-400" /></span>
                  ) : (
                    <CalendarDays className="w-3.5 h-3.5 flex-shrink-0 text-indigo-400" />
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold truncate flex items-center gap-1.5">
                      {cfg.name}
                      {unsaved && <span className="w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0" title="Unsaved changes" />}
                    </p>
                    <p className="text-[10px] text-zinc-600 mt-0.5">
                      {cfg.categoryIds.length} cat · {cfg.roleMappings.length} map
                    </p>
                  </div>
                  {deleteId === cfg.id ? (
                    <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
                      <button onClick={() => deleteCfg(cfg.id)} className="text-red-400 hover:text-red-300 text-[10px] font-bold">Yes</button>
                      <button onClick={() => setDeleteId(null)} className="text-zinc-500 hover:text-zinc-300 text-[10px] font-bold">No</button>
                    </div>
                  ) : (
                    <button onClick={e => { e.stopPropagation(); setDeleteId(cfg.id); }}
                      className="opacity-0 group-hover:opacity-100 transition-opacity text-zinc-600 hover:text-red-400">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
                );
              })}
            </div>
          </div>

          {/* ── Center: config editor ── */}
          <div className="flex-1 flex flex-col min-w-0 overflow-y-auto scrollbar-thin">
            {/* Plans only affect switches - they decide what "Select switch" offers and enforce order. */}
            {runMode === "switch" && (
              <div className="p-6 pb-0">
                <div className="mb-5">
                  <SemesterPlanEditor configs={configsDraft} channels={channels} plans={plansDraft} onChange={setPlansDraft} />
                </div>
              </div>
            )}

            {selectedId === null || !draft ? (
              <div className="flex-1 flex items-center justify-center text-zinc-600">
                <div className="text-center space-y-2">
                  <Smile className="w-10 h-10 mx-auto opacity-30" />
                  <p className="text-sm">Select a config or create a new one</p>
                </div>
              </div>
            ) : (
              <div className="p-6 flex flex-col gap-5 h-full">
                <div className={cn("grid gap-5 items-start", runMode === "switch" ? "grid-cols-2" : "grid-cols-1 max-w-3xl")}>
                  {/* Left: Semester Config */}
                  <Section icon={CalendarDays} color="#818cf8" title="Semester Config">
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-1.5">Semester Name</p>
                      <input value={draft.name} onChange={e => upd("name", e.target.value)}
                        placeholder="e.g. ZS2025, LS2025"
                        className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors" />
                      <p className="text-[11px] text-zinc-600 mt-1">Referenced by name from Plan steps and the Setup picker.</p>
                    </div>
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-1.5">Semester Type</p>
                      <div className="grid grid-cols-2 gap-2">
                        <button type="button" onClick={() => upd("semesterType", "WINTER")}
                          className={cn("py-2 rounded text-xs font-semibold border transition-all",
                            draft.semesterType === "WINTER"
                              ? "bg-sky-600/20 border-sky-500/50 text-sky-300"
                              : "bg-zinc-800 border-zinc-700 text-zinc-500 hover:text-zinc-300")}>
                          Winter
                        </button>
                        <button type="button" onClick={() => upd("semesterType", "SUMMER")}
                          className={cn("py-2 rounded text-xs font-semibold border transition-all",
                            draft.semesterType === "SUMMER"
                              ? "bg-amber-600/20 border-amber-500/50 text-amber-300"
                              : "bg-zinc-800 border-zinc-700 text-zinc-500 hover:text-zinc-300")}>
                          Summer
                        </button>
                      </div>
                      <p className="text-[11px] text-zinc-600 mt-1">
                        Used to block a switch between two same-type semesters in Switch Plans. Unset skips that check.
                      </p>
                    </div>
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-1.5">Categories</p>
                      <CategoryMultiSelect categories={categories} selected={draft.categoryIds} onChange={ids => upd("categoryIds", ids)} />
                      <p className="text-[11px] text-zinc-600 mt-1">Visible when active as new semester, hidden when active as old semester.</p>
                    </div>
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-1.5">@everyone View Channel</p>
                      <div className="grid grid-cols-2 gap-2">
                        <button type="button" onClick={() => upd("everyoneViewChannel", true)}
                          className={cn("flex items-center justify-center gap-1.5 py-2 rounded text-xs font-semibold border transition-all",
                            draft.everyoneViewChannel
                              ? "bg-emerald-600/20 border-emerald-500/50 text-emerald-300"
                              : "bg-zinc-800 border-zinc-700 text-zinc-500 hover:text-zinc-300")}>
                          <Eye className="w-3.5 h-3.5" /> True
                        </button>
                        <button type="button" onClick={() => upd("everyoneViewChannel", false)}
                          className={cn("flex items-center justify-center gap-1.5 py-2 rounded text-xs font-semibold border transition-all",
                            !draft.everyoneViewChannel
                              ? "bg-red-600/20 border-red-500/50 text-red-300"
                              : "bg-zinc-800 border-zinc-700 text-zinc-500 hover:text-zinc-300")}>
                          <EyeOff className="w-3.5 h-3.5" /> False
                        </button>
                      </div>
                      <p className="text-[11px] text-zinc-600 mt-1">
                        Applied when this semester is shown. Hiding a semester always sets @everyone to false.
                      </p>
                    </div>
                  </Section>

                  {/* Right: switch mappings and shared cleanup roles */}
                  <div className="space-y-5">
                    {runMode === "switch" && <Section icon={ArrowLeftRight} color="#f43f5e" title="Role Mappings">
                      <p className="text-xs text-zinc-500 -mt-2">Applied when a Plan step switches <span className="text-zinc-300 font-semibold">away from</span> this semester. Members with "From" role get it removed and receive "To" roles.</p>
                      <p className="text-xs text-zinc-600 -mt-1">Mappings run <span className="text-zinc-400 font-semibold">top to bottom</span> — each mapping sees the result of all previous ones. If a condition depends on a role that an earlier mapping assigns, use the <span className="text-zinc-400">new</span> role in the condition, not the original. Example: if <span className="text-zinc-400">@API 1.roč → @API 2.roč</span> is above a conditional mapping, use <span className="text-zinc-400">@API 2.roč</span> as the condition, not <span className="text-zinc-400">@API 1.roč</span>.</p>
                      {draft.roleMappings.length > 0 && (
                        <div className="space-y-1">
                          {draft.roleMappings.map((m, i) => {
                            const isEditing = editMappingIdx === i;
                            return (
                              <div key={i}
                                onDragOver={e => e.preventDefault()}
                                onDrop={() => dropMapping(i)}
                                className={cn("flex items-start gap-1.5 py-1.5 border-b border-zinc-800 last:border-0",
                                  isEditing && "opacity-40", dragMappingIndex === i && "opacity-40")}>
                                <span
                                  draggable
                                  onDragStart={() => setDragMappingIndex(i)}
                                  onDragEnd={() => setDragMappingIndex(null)}
                                  title="Drag to reorder"
                                  className="mt-0.5 flex-shrink-0 cursor-grab text-zinc-600 hover:text-zinc-300 transition-colors active:cursor-grabbing">
                                  <GripVertical className="w-3.5 h-3.5" />
                                </span>
                                <div className="flex flex-col gap-1 min-w-0 flex-1">
                                  <div className="flex flex-wrap items-center gap-1">
                                    {(() => {
                                      const name = roleName(m.fromRoleId);
                                      return name ? (
                                        <span className="inline-flex items-center gap-1 text-xs text-zinc-300 bg-zinc-800 border border-zinc-700 px-2 py-0.5 rounded">
                                          <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: roleCol(m.fromRoleId) }} />
                                          @{name}
                                        </span>
                                      ) : (
                                        <span className="inline-flex items-center gap-1 text-xs text-red-400 bg-red-900/20 border border-red-700/40 px-2 py-0.5 rounded line-through">
                                          deleted role
                                        </span>
                                      );
                                    })()}
                                    {m.keepFromRole && (
                                      <span className="text-[10px] text-amber-400 bg-amber-400/10 border border-amber-400/20 px-1.5 py-0.5 rounded font-semibold">keep</span>
                                    )}
                                    {m.conditionRoleIds && m.conditionRoleIds.length > 0 && (
                                      <>
                                        <span className="text-[10px] text-zinc-500 font-medium">if has</span>
                                        {m.conditionRoleIds.map(id => {
                                          const name = roleName(id);
                                          return name ? (
                                            <span key={id} className="inline-flex items-center gap-1 text-xs text-blue-300 bg-blue-900/30 border border-blue-700/40 px-2 py-0.5 rounded">
                                              <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: roleCol(id) }} />
                                              @{name}
                                            </span>
                                          ) : (
                                            <span key={id} className="inline-flex items-center gap-1 text-xs text-red-400 bg-red-900/20 border border-red-700/40 px-2 py-0.5 rounded line-through">
                                              deleted role
                                            </span>
                                          );
                                        })}
                                      </>
                                    )}
                                    <ArrowLeftRight className="w-3 h-3 text-zinc-600 flex-shrink-0" />
                                    {m.toRoleIds.map(id => {
                                      const name = roleName(id);
                                      return name ? (
                                        <span key={id} className="inline-flex items-center gap-1 text-xs text-zinc-300 bg-zinc-800 border border-zinc-700 px-2 py-0.5 rounded">
                                          <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: roleCol(id) }} />
                                          @{name}
                                        </span>
                                      ) : (
                                        <span key={id} className="inline-flex items-center gap-1 text-xs text-red-400 bg-red-900/20 border border-red-700/40 px-2 py-0.5 rounded line-through">
                                          deleted role
                                        </span>
                                      );
                                    })}
                                  </div>
                                </div>
                                <div className="flex items-center gap-1.5 mt-0.5 flex-shrink-0">
                                  {!isEditing && (
                                    <button onClick={() => editMapping(i)} className="text-zinc-600 hover:text-indigo-400 transition-colors" title="Edit">
                                      <svg className="w-3 h-3" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M11.5 2.5l2 2L5 13H3v-2L11.5 2.5z"/></svg>
                                    </button>
                                  )}
                                  <button onClick={() => removeMapping(i)} className="text-zinc-600 hover:text-red-400 transition-colors">
                                    <X className="w-3 h-3" />
                                  </button>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                      <div className="space-y-2 pt-1 border-t border-zinc-800">
                        <p className="text-[11px] font-semibold text-zinc-500 uppercase tracking-wide">
                          {editMappingIdx !== null ? "Edit Mapping" : "Add Mapping"}
                        </p>
                        <div className="grid grid-cols-2 gap-2">
                          <div>
                            <p className="text-[11px] text-zinc-500 mb-1">From role</p>
                            <RoleSelect roles={roles} value={newFrom} onChange={setNewFrom} placeholder="From role…" />
                          </div>
                          <div>
                            <p className="text-[11px] text-zinc-500 mb-1">To roles</p>
                            <RoleMultiSelect roles={roles} selected={newTo} onChange={setNewTo} placeholder="To roles…" />
                          </div>
                          <div>
                            <p className="text-[11px] text-zinc-500 mb-1">Condition roles <span className="text-zinc-600">(optional — member must also have)</span></p>
                            <RoleMultiSelect roles={roles} selected={newCondition} onChange={setNewCondition} placeholder="Condition roles…" />
                          </div>
                          <div className="flex flex-col justify-end">
                            <label className="flex items-center gap-2 cursor-pointer select-none py-2">
                              <input
                                type="checkbox"
                                checked={newKeepFrom}
                                onChange={e => setNewKeepFrom(e.target.checked)}
                                className="w-3.5 h-3.5 accent-amber-400 cursor-pointer"
                              />
                              <span className="text-[11px] text-zinc-400">Keep from role <span className="text-zinc-600">(don't remove it)</span></span>
                            </label>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <button onClick={addMapping} disabled={!newFrom || newTo.length === 0}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider bg-rose-700/80 hover:bg-rose-600 text-white transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
                            <Plus className="w-3 h-3" /> {editMappingIdx !== null ? "Save Changes" : "Add Mapping"}
                          </button>
                          {editMappingIdx !== null && (
                            <button onClick={cancelEdit} className="flex items-center gap-1 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider border border-zinc-700 text-zinc-400 hover:text-zinc-200 hover:border-zinc-500 transition-colors">
                              Cancel
                            </button>
                          )}
                        </div>
                      </div>
                    </Section>}

                    <Section icon={Users} color="#a78bfa" title="Semester Cleanup Roles">
                      <p className="text-xs text-zinc-500 -mt-2">
                        Subject and carry-over roles belonging to this semester, for example <span className="text-zinc-300">MAT2I</span>.
                        {runMode === "switch"
                          ? " Members are removed from these roles when a Plan step switches away from this semester."
                          : " Setup removes members from these roles only when cleanup is enabled in the run panel."}
                      </p>
                      <RoleMultiSelect
                        roles={roles}
                        selected={draft.semesterRoles ?? []}
                        onChange={ids => upd("semesterRoles", ids)}
                        placeholder="Add roles to clear for this semester…"
                      />
                    </Section>
                  </div>
                </div>
              </div>
            )}

            {/* One global Save Changes button for every semester config draft (not just the one
                selected) and the Switch Plans draft above - always reachable even with no semester
                selected. */}
            <div className="flex items-center justify-end gap-2 px-6 pb-6 flex-shrink-0">
              {(configsDirty || plansDirty) && <span className="text-[11px] text-amber-400">unsaved changes</span>}
              <button onClick={revert} disabled={saving || (!configsDirty && !plansDirty)}
                className="flex items-center gap-1.5 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider border border-zinc-700 text-zinc-400 hover:text-zinc-200 hover:border-zinc-500 transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
                <X className="w-3.5 h-3.5" />
                Revert Changes
              </button>
              <button onClick={save} disabled={saving || (!configsDirty && !plansDirty)}
                className="flex items-center gap-1.5 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider bg-indigo-600 hover:bg-indigo-500 text-white transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
                {saving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
                Save Changes
              </button>
            </div>
          </div>

          {/* ── Right: run + monitor ── */}
          <SemesterRunPanel
            plans={plans}
            runMode={runMode}
            setRunMode={setRunMode}
            anyRunActive={anyRunActive}
            nextPlan={nextPlan}
            selectedPlanId={selectedPlanId}
            setSelectedPlanId={setSelectedPlanId}
            runStarting={runStarting}
            ssProgress={ssProgress}
            configs={configs}
            setupSemester={setupSemester}
            selectSetupSemester={selectSetupSemester}
            setupVisible={setupVisible}
            setSetupVisible={setSetupVisible}
            setupClearRoles={setupClearRoles}
            setSetupClearRoles={setSetupClearRoles}
            setupStarting={setupStarting}
            setupProgress={setupProgress}
            activeProgress={activeProgress}
            canResume={canResume}
            runError={runError}
            consoleLogs={consoleLogs}
            isConsoleCleared={isConsoleCleared}
            consoleEndRef={consoleEndRef}
            consoleContainerRef={consoleContainerRef}
            handleConsoleScroll={handleConsoleScroll}
            clearConsole={clearConsole}
            onShowConfirmSwitch={openConfirmSwitch}
            onShowConfirmSetup={() => setShowSetupConfirm(true)}
            onShowModeInfo={() => setShowModeInfo(true)}
            onResume={handleResume}
          />

        </div>
      )}

      <ConfirmSwitchModal
        open={showConfirm}
        plan={selectedPlan}
        onClose={() => setShowConfirm(false)}
        onRun={handleRun}
      />
      <ConfirmSetupModal
        open={showSetupConfirm}
        setupSemester={setupSemester}
        setupVisible={setupVisible}
        setupClearRoles={setupClearRoles}
        setupConfig={setupConfig}
        onClose={() => setShowSetupConfirm(false)}
        onRun={handleSetup}
      />
      <ModeInfoModal
        open={showModeInfo}
        runMode={runMode}
        onClose={() => setShowModeInfo(false)}
      />
      <SemesterHistoryModal
        open={showHistory}
        onClose={() => setShowHistory(false)}
        guildId={guildId}
        plans={plans}
        roles={roles}
        currentPlanName={currentPlanName}
        onCurrentPlanChanged={refreshCurrentPlan}
      />
    </div>
  );
}
