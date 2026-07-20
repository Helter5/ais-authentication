import {
  ArrowLeftRight, Settings2, Info, Eye, EyeOff, ChevronRight,
  Loader2, Play, XCircle, Terminal, X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import type { SemesterConfig, RunProgress } from "@/pages/SwitchSemester";

function logColor(line: string) {
  if (line.includes("[ERROR]") || /\bERROR\b/.test(line)) return "text-red-400";
  if (line.includes("[WARN]") || line.includes("WARNING:")) return "text-yellow-300";
  if (/\] (Hidden|Shown|Roles:|Semester roles cleared|Semester switch complete)/.test(line)) return "text-emerald-400";
  return "text-green-400";
}

interface SemesterRunPanelProps {
  configs: SemesterConfig[];
  runMode: "switch" | "setup";
  setRunMode: (m: "switch" | "setup") => void;
  anyRunActive: boolean;
  runOld: string;
  setRunOld: (v: string) => void;
  runNew: string;
  setRunNew: (v: string) => void;
  runStarting: boolean;
  ssProgress: RunProgress | null;
  setupSemester: string;
  selectSetupSemester: (name: string) => void;
  setupVisible: boolean;
  setSetupVisible: (v: boolean) => void;
  setupClearRoles: boolean;
  setSetupClearRoles: (v: boolean) => void;
  setupStarting: boolean;
  setupProgress: RunProgress | null;
  activeProgress: RunProgress | null;
  canResume: boolean;
  runError: string | null;
  consoleLogs: string[];
  isConsoleCleared: boolean;
  consoleEndRef: React.RefObject<HTMLDivElement | null>;
  consoleContainerRef: React.RefObject<HTMLDivElement | null>;
  handleConsoleScroll: () => void;
  clearConsole: (startedAt: string | null) => void;
  onShowConfirmSwitch: () => void;
  onShowConfirmSetup: () => void;
  onShowModeInfo: () => void;
  onResume: () => void;
}

export function SemesterRunPanel({
  configs, runMode, setRunMode, anyRunActive,
  runOld, setRunOld, runNew, setRunNew, runStarting, ssProgress,
  setupSemester, selectSetupSemester, setupVisible, setSetupVisible,
  setupClearRoles, setSetupClearRoles, setupStarting, setupProgress,
  activeProgress, canResume, runError,
  consoleLogs, isConsoleCleared, consoleEndRef, consoleContainerRef,
  handleConsoleScroll, clearConsole,
  onShowConfirmSwitch, onShowConfirmSetup, onShowModeInfo, onResume,
}: SemesterRunPanelProps) {
  return (
    <div className="w-80 flex-shrink-0 border-l border-zinc-800 flex flex-col">
      {/* Run controls */}
      <div className="p-4 border-b border-zinc-800 space-y-3">
        <div className="grid grid-cols-2 gap-1 rounded-lg bg-zinc-900 border border-zinc-800 p-1">
          <button onClick={() => setRunMode("switch")} disabled={anyRunActive}
            className={cn("flex items-center justify-center gap-1.5 py-2 rounded-md text-xs font-semibold transition-colors",
              runMode === "switch" ? "bg-indigo-600 text-white" : "text-zinc-500 hover:text-zinc-300")}>
            <ArrowLeftRight className="w-3.5 h-3.5" /> Switch
          </button>
          <button onClick={() => setRunMode("setup")} disabled={anyRunActive}
            className={cn("flex items-center justify-center gap-1.5 py-2 rounded-md text-xs font-semibold transition-colors",
              runMode === "setup" ? "bg-amber-600 text-white" : "text-zinc-500 hover:text-zinc-300")}>
            <Settings2 className="w-3.5 h-3.5" /> Setup
          </button>
        </div>

        <button onClick={onShowModeInfo}
          className="w-full flex items-center justify-center gap-1.5 py-1 text-[11px] font-medium text-zinc-500 hover:text-zinc-300 transition-colors">
          <Info className="w-3.5 h-3.5" />
          How {runMode === "switch" ? "Switch" : "Setup"} works
        </button>

        {runMode === "switch" ? (
          <div className="space-y-2">
            <p className="text-[11px] leading-relaxed text-zinc-500">
              Hides the old semester, shows the new one, applies mappings from the old semester, then clears its cleanup roles.
            </p>
            <div>
              <p className="text-[11px] text-zinc-500 mb-1">From (current semester)</p>
              <select value={runOld} onChange={e => setRunOld(e.target.value)} disabled={anyRunActive}
                className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors disabled:opacity-50 cursor-pointer">
                <option value="">Select semester…</option>
                {configs.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
              </select>
            </div>
            <div className="flex items-center justify-center">
              <ChevronRight className="w-4 h-4 text-zinc-600 rotate-90" />
            </div>
            <div>
              <p className="text-[11px] text-zinc-500 mb-1">To (next semester)</p>
              <select value={runNew} onChange={e => setRunNew(e.target.value)} disabled={anyRunActive}
                className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors disabled:opacity-50 cursor-pointer">
                <option value="">Select semester…</option>
                {configs.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
              </select>
            </div>
            <button onClick={onShowConfirmSwitch}
              disabled={!runOld || !runNew || runOld === runNew || anyRunActive || runStarting}
              className={cn("w-full flex items-center justify-center gap-2 py-2.5 rounded text-sm font-semibold transition-all",
                runOld && runNew && runOld !== runNew && !anyRunActive && !runStarting
                  ? "bg-indigo-600 hover:bg-indigo-500 text-white"
                  : "bg-zinc-800 text-zinc-600 cursor-not-allowed")}>
              {runStarting || ssProgress?.running
                ? <><Loader2 className="w-4 h-4 animate-spin" />{ssProgress?.running ? "Running…" : "Starting…"}</>
                : <><Play className="w-4 h-4" /> Run Switch</>}
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-[11px] leading-relaxed text-zinc-500">
              Initializes one semester without advancing students. Use it for a fresh cycle or a manual visibility reset.
            </p>
            <div>
              <p className="text-[11px] text-zinc-500 mb-1">Semester</p>
              <select value={setupSemester} onChange={e => selectSetupSemester(e.target.value)} disabled={anyRunActive}
                className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-amber-500 transition-colors disabled:opacity-50 cursor-pointer">
                <option value="">Select semester…</option>
                {configs.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
              </select>
            </div>
            <div>
              <p className="text-[11px] text-zinc-500 mb-1.5">Channel visibility</p>
              <div className="grid grid-cols-2 gap-2">
                <button onClick={() => setSetupVisible(true)} disabled={anyRunActive}
                  className={cn("flex items-center justify-center gap-1.5 py-2 rounded text-xs font-semibold border transition-all",
                    setupVisible ? "bg-emerald-600/20 border-emerald-500/50 text-emerald-300" : "bg-zinc-800 border-zinc-700 text-zinc-500")}>
                  <Eye className="w-3.5 h-3.5" /> Show
                </button>
                <button onClick={() => setSetupVisible(false)} disabled={anyRunActive}
                  className={cn("flex items-center justify-center gap-1.5 py-2 rounded text-xs font-semibold border transition-all",
                    !setupVisible ? "bg-red-600/20 border-red-500/50 text-red-300" : "bg-zinc-800 border-zinc-700 text-zinc-500")}>
                  <EyeOff className="w-3.5 h-3.5" /> Hide
                </button>
              </div>
            </div>
            <label className="flex items-start gap-2 cursor-pointer">
              <input type="checkbox" checked={setupClearRoles} disabled={anyRunActive}
                onChange={e => setSetupClearRoles(e.target.checked)} className="mt-0.5 accent-red-500" />
              <span className="text-xs text-zinc-400 leading-relaxed">
                <span className="font-semibold text-red-300">Clear configured cleanup roles</span> from all members.
              </span>
            </label>
            <button onClick={onShowConfirmSetup}
              disabled={!setupSemester || anyRunActive || setupStarting}
              className={cn("w-full flex items-center justify-center gap-2 py-2.5 rounded text-sm font-semibold transition-all",
                setupSemester && !anyRunActive && !setupStarting
                  ? "bg-amber-600 hover:bg-amber-500 text-white"
                  : "bg-zinc-800 text-zinc-600 cursor-not-allowed")}>
              {setupStarting || setupProgress?.running
                ? <><Loader2 className="w-4 h-4 animate-spin" />{setupProgress?.running ? "Running…" : "Starting…"}</>
                : <><Play className="w-4 h-4" /> Run Setup</>}
            </button>
          </div>
        )}

        {activeProgress && activeProgress.progress > 0 && (
          <div>
            <div className="flex justify-between text-[10px] text-zinc-500 mb-1">
              <span className="capitalize">{activeProgress.status ?? "Progress"}</span><span>{activeProgress.progress}%</span>
            </div>
            <div className="h-1.5 bg-zinc-800 rounded-full overflow-hidden">
              <div className={cn("h-full rounded-full transition-all duration-500",
                activeProgress.running ? (runMode === "switch" ? "bg-indigo-500" : "bg-amber-500")
                  : activeProgress.status === "failed" ? "bg-red-500"
                    : activeProgress.status === "partial" ? "bg-yellow-500" : "bg-emerald-500")}
                style={{ width: `${activeProgress.progress}%` }} />
            </div>
          </div>
        )}

        {canResume && (
          <button onClick={onResume}
            disabled={runStarting || setupStarting}
            className={cn(
              "w-full flex items-center justify-center gap-2 py-2.5 rounded text-sm font-semibold border transition-all",
              runMode === "switch"
                ? "bg-indigo-500/10 border-indigo-500/40 text-indigo-300 hover:bg-indigo-500/20"
                : "bg-amber-500/10 border-amber-500/40 text-amber-300 hover:bg-amber-500/20"
            )}>
            {runStarting || setupStarting
              ? <><Loader2 className="w-4 h-4 animate-spin" /> Resuming…</>
              : <><Play className="w-4 h-4" /> Resume unfinished steps</>}
          </button>
        )}

        {runError && (
          <div className="flex items-start gap-1.5 p-2.5 bg-red-500/10 border border-red-500/20 rounded text-red-400 text-xs">
            <XCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />{runError}
          </div>
        )}
      </div>

      {/* Console */}
      <div className={cn("bg-zinc-950 flex flex-col min-h-0", consoleLogs.length > 0 && "flex-1")}>
        <div className="flex items-center gap-2 px-3 py-2 border-b border-zinc-800 bg-zinc-900/60">
          <Terminal className="w-3.5 h-3.5 text-zinc-500" />
          <span className="text-xs font-mono text-zinc-500">live output</span>
          {!anyRunActive && !isConsoleCleared && activeProgress && activeProgress.logs.length > 0 && (
            <button
              onClick={() => clearConsole(activeProgress.startedAt ?? null)}
              className="ml-auto text-zinc-600 hover:text-zinc-400 transition-colors"
              title="Clear console"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
        {consoleLogs.length === 0 ? (
          <p className="px-3 py-2 text-xs font-mono text-zinc-700 italic">No output yet.</p>
        ) : (
          <div ref={consoleContainerRef} onScroll={handleConsoleScroll} className="flex-1 overflow-y-auto scrollbar-thin p-3 space-y-0.5 font-mono text-xs min-h-0">
            {consoleLogs.map((line, i) => (
              <div key={i} className={cn("leading-5", logColor(line))}>{line}</div>
            ))}
            <div ref={consoleEndRef} />
          </div>
        )}
      </div>
    </div>
  );
}
