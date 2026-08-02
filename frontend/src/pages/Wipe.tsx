import { useEffect, useRef, useState } from "react";
import { adminApi, apiErrorMessage } from "@/lib/api";
import { cn } from "@/lib/utils";
import { AlertTriangle, CheckCircle2, Loader2, Terminal, Trash2, Users, XCircle, Settings, XSquare } from "lucide-react";
import { useSelectedGuildId, MultiPicker, LogChannelPicker } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";

type LogEntry = { time: string; msg: string; level: "info" | "warn" | "error" | "success" };
type WipeStats = { total: number; processed: number; inactive: number; errors: number };
type WipeState = {
  running: boolean;
  done: boolean;
  logs: LogEntry[];
  stats: WipeStats | null;
  startedAt: string | null;
  completedAt: string | null;
};

function StatBadge({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className={cn("flex flex-col items-center px-4 py-3 rounded-lg border", color)}>
      <span className="text-xl font-bold font-mono">{value}</span>
      <span className="text-xs text-zinc-500 mt-0.5">{label}</span>
    </div>
  );
}

function LogLine({ entry }: { entry: LogEntry }) {
  const color =
    entry.level === "error" ? "text-red-400" :
    entry.level === "warn"  ? "text-yellow-300" :
    entry.level === "success" ? "text-emerald-400" :
    "text-green-400";
  return (
    <div className="flex gap-2 font-mono text-xs leading-5">
      <span className="text-green-700 shrink-0">[{entry.time}]</span>
      <span className={color}>{entry.msg}</span>
    </div>
  );
}

export function Wipe() {
  const guildId = useSelectedGuildId();
  const [access, setAccess] = useState<boolean | null>(null);
  const [accessReason, setAccessReason] = useState<string | null>(null);
  const [state, setState] = useState<WipeState | null>(null);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [removeAllRoles, setRemoveAllRoles] = useState(false);
  const [keepRoleIds, setKeepRoleIds] = useState<string[]>([]);
  const [roles, setRoles] = useState<{ id: string; name: string }[]>([]);
  const { toast } = useToast();
  const [clearedStartedAt, setClearedStartedAt] = useState<string | null>(
    () => localStorage.getItem(`wipe_console_cleared_${guildId}`) ?? null
  );
  const logEndRef = useRef<HTMLDivElement | null>(null);
  const logContainerRef = useRef<HTMLDivElement | null>(null);
  const isAtBottomRef = useRef(true);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const handleLogScroll = () => {
    const el = logContainerRef.current;
    if (!el) return;
    isAtBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
  };

  const recheckAccess = () => {
    if (!guildId) return;
    adminApi.getWipeAccess(guildId)
      .then(r => { setAccess(r.allowed); setAccessReason(r.reason ?? null); })
      .catch(() => setAccess(false));
  };

  // Check access
  useEffect(() => {
    let cancelled = false;
    setAccess(null);
    setAccessReason(null);
    setState(null);
    setStarting(false);
    setError(null);
    setConfirmed(false);
    setRemoveAllRoles(false);
    setKeepRoleIds([]);
    setRoles([]);
    setClearedStartedAt(localStorage.getItem(`wipe_console_cleared_${guildId}`) ?? null);
    if (!guildId) {
      setAccess(false);
      return () => { cancelled = true; };
    }
    adminApi.getWipeAccess(guildId)
      .then(r => {
        if (cancelled) return;
        setAccess(r.allowed);
        setAccessReason(r.reason ?? null);
      })
      .catch(() => {
        if (!cancelled) setAccess(false);
      });
    adminApi.getDiscordRoles(guildId)
      .then(r => { if (!cancelled) setRoles(r); })
      .catch(() => { if (!cancelled) setRoles([]); });
    adminApi.getWipeSettings(guildId)
      .then(s => {
        if (cancelled) return;
        setRemoveAllRoles(s.removeAllRoles ?? false);
        setKeepRoleIds(s.keepRoleIds ?? []);
      })
      .catch(() => { /* keep defaults */ });
    return () => { cancelled = true; };
  }, [guildId]);

  // Poll status
  useEffect(() => {
    let cancelled = false;
    if (!guildId || access === false) return () => { cancelled = true; };

    const poll = async () => {
      try {
        const s = await adminApi.getWipeStatus(guildId);
        if (cancelled) return;
        setState(s);
        if (isAtBottomRef.current && logEndRef.current) logEndRef.current.scrollIntoView({ behavior: "smooth" });
      } catch { /* ignore */ }
    };

    poll();
    const interval = state?.running ? 1000 : 5000;
    pollRef.current = setInterval(poll, interval);
    return () => {
      cancelled = true;
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [guildId, access, state?.running]);

  const handleStart = async () => {
    if (!guildId || starting) return;
    setError(null);
    setStarting(true);
    setConfirmed(false);
    try {
      await adminApi.startWipe(guildId, removeAllRoles, removeAllRoles ? keepRoleIds : []);
      // immediately poll
      const s = await adminApi.getWipeStatus(guildId);
      setState(s);
      toast("Wipe started.");
    } catch (e: unknown) {
      const msg = apiErrorMessage(e, "Failed to start wipe.");
      setError(msg);
      toast(msg, "error");
    } finally {
      setStarting(false);
    }
  };

  const isCleared = Boolean(state?.startedAt && state.startedAt === clearedStartedAt);
  const visibleLogs = isCleared ? [] : (state?.logs ?? []);
  const stats = state?.stats ?? { total: 0, processed: 0, inactive: 0, errors: 0 };
  const progress = stats.total > 0 ? Math.round((stats.processed / stats.total) * 100) : 0;

  if (!guildId) {
    return (
      <div className="flex flex-col gap-6 md:pl-64 px-4 sm:px-6 pt-6">
        <div className="text-zinc-500 text-sm">No server selected. Go to the dashboard and select a server first.</div>
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
          {isNoChannel ? <Settings className="w-4 h-4 shrink-0 mt-0.5" /> : <XCircle className="w-4 h-4 shrink-0 mt-0.5" />}
          <div className="space-y-1">
            {isNoChannel ? (
              <>
                <p className="font-semibold">Wipe log channel not configured.</p>
                <p className="text-amber-400/80 text-xs">A log channel is required before running a wipe - set one under Log Channels below.</p>
              </>
            ) : (
              <p>Access denied. You need to be an admin or have a manager role.</p>
            )}
          </div>
        </div>
        {isNoChannel && (
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4">
            <LogChannelPicker
              guildId={guildId}
              eventTypes={["WIPE_INACTIVE_USER_REMOVED", "WIPE_RECAP"]}
              title="Log Channel"
              onSaved={recheckAccess}
              combined
            />
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6 md:pl-64">
      <div className="px-4 sm:px-6 pt-5">
        <h1 className="text-2xl font-bold text-zinc-100 tracking-tight flex items-center gap-2">
          <Trash2 className="w-5 h-5 text-red-400" />
          Wipe
        </h1>
        <p className="text-sm text-zinc-500 mt-0.5">Check all verified users against LDAP and remove inactive ones.</p>
      </div>

      <div className="px-4 sm:px-6 pb-8 flex flex-col lg:flex-row gap-4 h-full">
        {/* Left — controls */}
        <div className="lg:w-80 shrink-0 flex flex-col gap-4">
          {/* Warning */}
          <div className="bg-red-500/8 border border-red-500/20 rounded-xl p-4 flex gap-3">
            <AlertTriangle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
            <div className="text-xs text-red-300 leading-relaxed space-y-1">
              <p className="font-semibold text-red-200">Destructive operation</p>
              <p>Every verified user is checked against LDAP. Those no longer active FEI students get the <strong>Inactive</strong> role and are removed from the database.</p>
              <p>This cannot be undone.</p>
            </div>
          </div>

          {/* Log Channels */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4">
            <LogChannelPicker guildId={guildId} eventTypes={["WIPE_INACTIVE_USER_REMOVED", "WIPE_RECAP"]} title="Log Channel" combined />
          </div>

          {/* Stats */}
          {state?.stats && (
            <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 space-y-3">
              <p className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">Progress</p>
              <div className="grid grid-cols-2 gap-2">
                <StatBadge label="Total" value={stats.total} color="bg-zinc-800/60 border-zinc-700 text-zinc-200" />
                <StatBadge label="Checked" value={stats.processed} color="bg-zinc-800/60 border-zinc-700 text-zinc-200" />
                <StatBadge label="Inactive" value={stats.inactive} color="bg-yellow-500/10 border-yellow-500/25 text-yellow-200" />
                <StatBadge label="Errors" value={stats.errors} color={stats.errors > 0 ? "bg-red-500/10 border-red-500/25 text-red-200" : "bg-zinc-800/60 border-zinc-700 text-zinc-200"} />
              </div>
              {stats.total > 0 && (
                <div>
                  <div className="flex justify-between text-xs text-zinc-500 mb-1">
                    <span>Progress</span><span>{progress}%</span>
                  </div>
                  <div className="h-2 bg-zinc-800 rounded-full overflow-hidden">
                    <div
                      className={cn("h-full rounded-full transition-all duration-300", state.done ? "bg-emerald-500" : "bg-indigo-500")}
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Status */}
          {state?.done && (
            <div className="flex items-center gap-2 p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-lg text-emerald-400 text-xs">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              Wipe complete — {stats.inactive} inactive users removed.
            </div>
          )}

          {error && (
            <div className="flex items-start gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-xs">
              <XCircle className="w-4 h-4 shrink-0 mt-0.5" />
              {error}
            </div>
          )}

          {/* Confirm + Start */}
          {!state?.running && (
            <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 space-y-3">
              <label className="flex items-start gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={removeAllRoles}
                  onChange={e => setRemoveAllRoles(e.target.checked)}
                  className="mt-0.5 accent-orange-500"
                />
                <span className="text-xs text-zinc-400">
                  <span className="text-orange-300 font-semibold">Remove all roles</span> — strip every role and keep only Inactive (default: only remove Verified role).
                </span>
              </label>
              {removeAllRoles && (
                <div className="pl-6 space-y-1.5">
                  <span className="text-xs text-zinc-500">Keep roles — these will not be removed</span>
                  <MultiPicker options={roles} selected={keepRoleIds} onChange={setKeepRoleIds} placeholder="Select role(s) to keep" />
                </div>
              )}
              <div className="border-t border-zinc-800" />
              <label className="flex items-start gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={confirmed}
                  onChange={e => setConfirmed(e.target.checked)}
                  className="mt-0.5 accent-red-500"
                />
                <span className="text-xs text-zinc-400">I understand this will remove inactive users from the database and reassign their Discord roles.</span>
              </label>
              <button
                onClick={handleStart}
                disabled={!confirmed || starting}
                className={cn(
                  "w-full flex items-center justify-center gap-2 py-2.5 rounded-lg text-sm font-semibold transition-all",
                  confirmed && !starting
                    ? "bg-red-600 hover:bg-red-500 text-white"
                    : "bg-zinc-800 text-zinc-600 cursor-not-allowed"
                )}
              >
                {starting ? (
                  <><Loader2 className="w-4 h-4 animate-spin" /> Starting…</>
                ) : (
                  <><Users className="w-4 h-4" /> Start Wipe</>
                )}
              </button>
            </div>
          )}

          {state?.running && (
            <div className="flex items-center gap-2 p-3 bg-indigo-500/10 border border-indigo-500/20 rounded-lg text-indigo-300 text-xs">
              <Loader2 className="w-4 h-4 animate-spin shrink-0" />
              Wipe in progress… do not close this page.
            </div>
          )}
        </div>

        {/* Right — live console */}
        <div className="flex-1 flex flex-col h-[600px] lg:h-[calc(100vh-180px)]">
          <div className="flex-1 bg-zinc-950 border border-zinc-800 rounded-xl overflow-hidden flex flex-col">
            {/* Terminal header */}
            <div className="flex items-center gap-2 px-4 py-2.5 border-b border-zinc-800 bg-zinc-900/60">
              <Terminal className="w-3.5 h-3.5 text-zinc-500" />
              <span className="text-xs font-mono text-zinc-500">wipe — live output</span>
              {state?.running && (
                <span className="flex items-center gap-1.5 text-[11px] text-emerald-400">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                  running
                </span>
              )}
              {state?.done && (
                <span className="text-[11px] text-zinc-500">
                  completed {state.completedAt ? new Date(state.completedAt).toLocaleTimeString() : ""}
                </span>
              )}
              {(state?.logs?.length ?? 0) > 0 && (
                <button
                  onClick={() => {
                    const val = state?.startedAt ?? null;
                    setClearedStartedAt(val);
                    if (val) localStorage.setItem(`wipe_console_cleared_${guildId}`, val);
                  }}
                  className="ml-auto flex items-center gap-1 text-[11px] text-zinc-600 hover:text-zinc-400 transition-colors"
                  title="Clear console"
                >
                  <XSquare className="w-3.5 h-3.5" /> Clear
                </button>
              )}
            </div>

            {/* Log stream */}
            <div ref={logContainerRef} onScroll={handleLogScroll} className="flex-1 overflow-y-auto scrollbar-thin p-4 space-y-0.5 font-mono text-xs min-h-0">
              {visibleLogs.length === 0 ? (
                <div className="h-full flex items-center justify-center">
                  <span className="text-zinc-700 italic">No output yet. Start a wipe to see live progress.</span>
                </div>
              ) : (
                visibleLogs.map((entry, i) => <LogLine key={i} entry={entry} />)
              )}
              <div ref={logEndRef} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
