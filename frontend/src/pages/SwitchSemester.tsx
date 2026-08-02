import { useEffect, useRef, useState } from "react";
import { adminApi, apiErrorMessage } from "@/lib/api";
import { SemesterRunPanel } from "@/components/semester/SemesterRunPanel";
import { ConfirmSwitchModal, ConfirmSetupModal, ModeInfoModal } from "@/components/semester/SemesterModals";
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
  ChevronRight, Users, ShieldCheck,
  Eye, EyeOff, XCircle, Smile,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useSelectedGuildId, LogChannelPicker } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";

// ── Types ─────────────────────────────────────────────────────────────────────

type RoleMapping = {
  fromRoleId: string;
  toRoleIds: string[];
  conditionRoleIds?: string[];
  keepFromRole?: boolean;
};
type AllowedTransition = { from: string; to: string };

export type SemesterConfig = {
  id: string;
  name: string;
  categoryIds: string[];
  everyoneViewChannel: boolean;
  roleMappings: RoleMapping[];
  semesterRoles: string[];
};

type Category = SemesterCategory;
type Role = SemesterRole;
export type RunProgress = {
  running: boolean;
  progress: number;
  logs: string[];
  startedAt: string | null;
  status?: "running" | "success" | "partial" | "failed";
  operation?: "switch" | "setup";
  params?: {
    oldName?: string;
    newName?: string;
    semesterName?: string;
    visible?: boolean;
    everyoneViewChannel?: boolean;
    clearRoles?: boolean;
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
});

// ── Section header ─────────────────────────────────────────────────────────────

function Section({ icon: Icon, color, title, children }: {
  icon: React.ElementType; color: string; title: string; children: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-900 overflow-hidden">
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
  const [configs, setConfigs] = useState<SemesterConfig[]>([]);
  const [allowedTransitions, setAllowedTransitions] = useState<AllowedTransition[]>([]);
  const [newTFrom, setNewTFrom] = useState("");
  const [newTTo, setNewTTo] = useState("");
  const [selectedId, setSelectedId] = useState<string | "new" | null>(null);
  const [draft, setDraft] = useState<SemesterConfig | null>(null);
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
  const [runOld, setRunOld] = useState("");
  const [runNew, setRunNew] = useState("");
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
  const consoleEndRef = useRef<HTMLDivElement | null>(null);
  const consoleContainerRef = useRef<HTMLDivElement | null>(null);
  const isConsoleAtBottomRef = useRef(true);
  const [clearedStartedAt, setClearedStartedAt] = useState<string | null>(
    () => localStorage.getItem(`semester_console_cleared_${guildId}`) ?? null
  );

  useEffect(() => {
    setClearedStartedAt(localStorage.getItem(`semester_console_cleared_${guildId}`) ?? null);
  }, [guildId]);

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

  // Access check
  useEffect(() => {
    let cancelled = false;
    setAccess(null);
    setAccessReason(null);
    setCategories([]);
    setRoles([]);
    setConfigs([]);
    setAllowedTransitions([]);
    setNewTFrom("");
    setNewTTo("");
    setSelectedId(null);
    setDraft(null);
    setLoading(true);
    setSaving(false);
    setDeleteId(null);
    setNewFrom(null);
    setNewTo([]);
    setRunOld("");
    setRunNew("");
    setRunStarting(false);
    setRunError(null);
    setShowConfirm(false);
    setSsProgress(null);
    setRunMode("switch");
    setSetupSemester("");
    setSetupVisible(true);
    setSetupClearRoles(false);
    setSetupStarting(false);
    setSetupProgress(null);
    setShowSetupConfirm(false);
    setShowModeInfo(false);
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
      adminApi.getSemesterConfigs(guildId),
    ]).then(([cats, rls, settings]) => {
      if (cancelled) return;
      setCategories(cats);
      setRoles(rls);
      const s = settings as { configs?: SemesterConfig[]; logActions?: boolean; allowedTransitions?: AllowedTransition[] };
      const normalizedConfigs = (s.configs ?? []).map(config => ({
        ...config,
        everyoneViewChannel: config.everyoneViewChannel === true,
      }));
      setConfigs(normalizedConfigs);
      setAllowedTransitions(s.allowedTransitions ?? []);
      if (normalizedConfigs.length > 0) {
        const first = normalizedConfigs[0];
        setSelectedId(first.id);
        setDraft({
          ...first,
          categoryIds: [...first.categoryIds],
          roleMappings: first.roleMappings.map(m => ({ ...m, toRoleIds: [...m.toRoleIds] })),
          semesterRoles: [...(first.semesterRoles ?? [])],
        });
        setSetupSemester(first.name);
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
        const [switchRun, setupRun] = await Promise.all([
          adminApi.getSwitchSemesterProgress(guildId),
          adminApi.getSemesterSetupProgress(guildId),
        ]);
        if (cancelled) return;
        setSsProgress(switchRun);
        setSetupProgress(setupRun);
        if (isConsoleAtBottomRef.current && consoleEndRef.current) consoleEndRef.current.scrollIntoView({ behavior: "smooth" });
      } catch { /* ignore */ }
    };
    poll();
    const interval = ssProgress?.running || setupProgress?.running ? 1000 : 5000;
    const id = setInterval(poll, interval);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [guildId, access, ssProgress?.running, setupProgress?.running]);

  const saveAll = async (updated: SemesterConfig[], newTransitions = allowedTransitions) => {
    if (!guildId) return;
    setSaving(true);
    try {
      // Always logged to Access Logs - not user-configurable, so this is hardcoded rather than a draft toggle.
      await adminApi.saveSemesterConfigs(guildId, { configs: updated, logActions: true, allowedTransitions: newTransitions });
      toast("Saved.");
    } catch {
      toast("Failed to save.", "error");
    } finally {
      setSaving(false);
    }
  };

  const addTransition = async () => {
    if (!newTFrom || !newTTo || newTFrom === newTTo) return;
    const already = allowedTransitions.some(t => t.from === newTFrom && t.to === newTTo);
    if (already) return;
    const next = [...allowedTransitions, { from: newTFrom, to: newTTo }];
    setAllowedTransitions(next);
    setNewTFrom(""); setNewTTo("");
    await saveAll(configs, next);
  };

  const removeTransition = async (i: number) => {
    const next = allowedTransitions.filter((_, idx) => idx !== i);
    setAllowedTransitions(next);
    await saveAll(configs, next);
  };

  const newCfg = () => {
    const c = DEFAULT_CONFIG();
    setDraft(c);
    setSelectedId("new");
    setNewFrom(null);
    setNewTo([]);
    setNewCondition([]);
    setNewKeepFrom(false);
    setEditMappingIdx(null);
  };

  const selectCfg = (cfg: SemesterConfig) => {
    setDraft({ ...cfg, categoryIds: [...cfg.categoryIds], roleMappings: cfg.roleMappings.map(m => ({ ...m, toRoleIds: [...m.toRoleIds] })), semesterRoles: [...(cfg.semesterRoles ?? [])] });
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
    setDraft(d => d ? { ...d, [key]: value } : d);
  };

  const save = async () => {
    if (!draft) return;
    let next: SemesterConfig[];
    if (selectedId === "new") {
      next = [...configs, draft];
    } else {
      next = configs.map(c => c.id === selectedId ? draft : c);
    }
    setConfigs(next);
    if (selectedId === "new") setSelectedId(draft.id);
    await saveAll(next);
  };

  const deleteCfg = async (id: string) => {
    const next = configs.filter(c => c.id !== id);
    setConfigs(next);
    if (selectedId === id || selectedId === "new") { setSelectedId(null); setDraft(null); }
    setDeleteId(null);
    await saveAll(next);
  };

  const [editMappingIdx, setEditMappingIdx] = useState<number | null>(null);

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

  const roleName = (id: string) => roles.find(r => r.id === id)?.name ?? null;
  const roleCol = (id: string) => roleColor(roles.find(r => r.id === id)?.color);

  const handleRun = async () => {
    if (!guildId || !runOld || !runNew || runStarting) return;
    setShowConfirm(false);
    setRunError(null);
    setRunStarting(true);
    try {
      await adminApi.runSwitchSemester(guildId, runOld, runNew);
      const p = await adminApi.getSwitchSemesterProgress(guildId);
      setSsProgress(p);
      toast("Semester switch started.");
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
      const params = ssProgress?.params;
      if (!params?.oldName || !params.newName) return;
      setRunStarting(true);
      setRunOld(params.oldName);
      setRunNew(params.newName);
      try {
        await adminApi.runSwitchSemester(guildId, params.oldName, params.newName, true);
        setSsProgress(await adminApi.getSwitchSemesterProgress(guildId));
        toast("Semester switch resumed.");
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
  const newSemesterConfig = configs.find(config => config.name === runNew);
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
      </div>

      {loading ? (
        <div className="flex items-center gap-2 p-6 text-zinc-500 text-sm">
          <Loader2 className="w-4 h-4 animate-spin" /> Loading…
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
                  selectedId === "new"
                    ? "border-indigo-500/60 text-indigo-400 bg-indigo-500/10"
                    : "border-zinc-700 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200")}>
                <Plus className="w-3.5 h-3.5" /> New Semester
              </button>
            </div>
            <div className="flex-1 overflow-y-auto scrollbar-thin p-2 space-y-1">
              {configs.length === 0 && (
                <p className="text-xs text-zinc-600 text-center py-4">No configs yet</p>
              )}
              {configs.map(cfg => (
                <div key={cfg.id}
                  className={cn("group flex items-center gap-2 px-3 py-2.5 rounded cursor-pointer transition-all",
                    selectedId === cfg.id ? "bg-zinc-700 text-zinc-100" : "text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200")}
                  onClick={() => selectCfg(cfg)}>
                  <CalendarDays className="w-3.5 h-3.5 flex-shrink-0 text-indigo-400" />
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold truncate">{cfg.name}</p>
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
              ))}
            </div>
          </div>

          {/* ── Center: config editor ── */}
          <div className="flex-1 flex flex-col min-w-0 overflow-y-auto scrollbar-thin">
            {/* Transition rules only affect switches. */}
            {runMode === "switch" && <div className="p-6 pb-0">
              <div className="rounded-lg border border-zinc-800 bg-zinc-900 overflow-hidden mb-5">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <ShieldCheck className="w-4 h-4 text-amber-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Allowed Transitions</h2>
                  <span className="ml-auto text-[11px] text-zinc-600">
                    {allowedTransitions.length === 0 ? "All transitions allowed (none configured)" : `${allowedTransitions.length} rule${allowedTransitions.length !== 1 ? "s" : ""}`}
                  </span>
                </div>
                <div className="px-4 py-4 space-y-3">
                  <p className="text-xs text-zinc-500">Restrict which semester switches are permitted. If any rules exist, only listed transitions will run.</p>
                  {allowedTransitions.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                      {allowedTransitions.map((t, i) => (
                        <span key={i} className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-zinc-800 border border-zinc-700/60 text-xs font-mono">
                          <span className="text-zinc-200">{t.from}</span>
                          <ChevronRight className="w-3 h-3 text-amber-400 flex-shrink-0" />
                          <span className="text-amber-300">{t.to}</span>
                          <button onClick={() => removeTransition(i)} className="ml-1 text-zinc-600 hover:text-red-400 transition-colors">
                            <X className="w-3 h-3" />
                          </button>
                        </span>
                      ))}
                    </div>
                  )}
                  <div className="flex items-center gap-2 pt-1 border-t border-zinc-800">
                    <select value={newTFrom} onChange={e => setNewTFrom(e.target.value)}
                      className="w-36 min-w-0 px-2.5 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 outline-none focus:border-amber-500 transition-colors cursor-pointer">
                      <option value="">From…</option>
                      {configs.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
                    </select>
                    <ChevronRight className="w-3 h-3 text-zinc-600 flex-shrink-0" />
                    <select value={newTTo} onChange={e => setNewTTo(e.target.value)}
                      className="w-36 min-w-0 px-2.5 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 outline-none focus:border-amber-500 transition-colors cursor-pointer">
                      <option value="">To…</option>
                      {configs.filter(c => c.name !== newTFrom).map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
                    </select>
                    <button onClick={addTransition} disabled={!newTFrom || !newTTo || newTFrom === newTTo}
                      className="flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-bold bg-amber-600/80 hover:bg-amber-500 text-white transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
                      <Plus className="w-3 h-3" /> Add
                    </button>
                  </div>
                </div>
              </div>
            </div>}

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
                      <p className="text-[11px] text-zinc-600 mt-1">Used as the old/new semester name when running a switch.</p>
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
                      <p className="text-xs text-zinc-500 -mt-2">Applied when switching <span className="text-zinc-300 font-semibold">away from</span> this semester. Members with "From" role get it removed and receive "To" roles.</p>
                      <p className="text-xs text-zinc-600 -mt-1">Mappings run <span className="text-zinc-400 font-semibold">top to bottom</span> — each mapping sees the result of all previous ones. If a condition depends on a role that an earlier mapping assigns, use the <span className="text-zinc-400">new</span> role in the condition, not the original. Example: if <span className="text-zinc-400">@API 1.roč → @API 2.roč</span> is above a conditional mapping, use <span className="text-zinc-400">@API 2.roč</span> as the condition, not <span className="text-zinc-400">@API 1.roč</span>.</p>
                      {draft.roleMappings.length > 0 && (
                        <div className="space-y-1">
                          {draft.roleMappings.map((m, i) => {
                            const isEditing = editMappingIdx === i;
                            return (
                              <div key={i} className={cn("flex items-start gap-1.5 py-1.5 border-b border-zinc-800 last:border-0", isEditing && "opacity-40")}>
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
                                <div className="flex items-center gap-1 mt-0.5 flex-shrink-0">
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
                          ? " Members are removed from these roles when switching away from this semester."
                          : " Setup removes members from these roles only when cleanup is enabled in the run panel."}
                      </p>
                      <RoleMultiSelect
                        roles={roles}
                        selected={draft.semesterRoles ?? []}
                        onChange={ids => upd("semesterRoles", ids)}
                        placeholder="Add roles to clear for this semester…"
                      />
                    </Section>

                    <div className="flex justify-end pt-1">
                      <button onClick={save} disabled={saving}
                        className="flex items-center gap-1.5 px-4 py-2 rounded text-xs font-bold uppercase tracking-wider bg-indigo-600 hover:bg-indigo-500 text-white transition-colors disabled:opacity-50">
                        {saving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
                        Save Changes
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* ── Right: run + monitor ── */}
          <SemesterRunPanel
            configs={configs}
            runMode={runMode}
            setRunMode={setRunMode}
            anyRunActive={anyRunActive}
            runOld={runOld}
            setRunOld={setRunOld}
            runNew={runNew}
            setRunNew={setRunNew}
            runStarting={runStarting}
            ssProgress={ssProgress}
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
            onShowConfirmSwitch={() => setShowConfirm(true)}
            onShowConfirmSetup={() => setShowSetupConfirm(true)}
            onShowModeInfo={() => setShowModeInfo(true)}
            onResume={handleResume}
          />

        </div>
      )}

      <ConfirmSwitchModal
        open={showConfirm}
        runOld={runOld}
        runNew={runNew}
        newSemesterConfig={newSemesterConfig}
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
    </div>
  );
}
