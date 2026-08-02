import { ArrowLeftRight, ChevronRight, Settings2, Play, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { ModalOverlay } from "@/components/ui/modal-overlay";
import type { SemesterConfig, RunProgress } from "@/pages/SwitchSemester";

interface ConfirmSwitchModalProps {
  open: boolean;
  runOld: string;
  runNew: string;
  newSemesterConfig: SemesterConfig | undefined;
  onClose: () => void;
  onRun: () => void;
}

export function ConfirmSwitchModal({ open, runOld, runNew, newSemesterConfig, onClose, onRun }: ConfirmSwitchModalProps) {
  if (!open) return null;
  return (
    <ModalOverlay onClose={onClose} panelClassName="w-full max-w-md p-6 space-y-5">
      <div className="flex items-start gap-3">
        <div className="w-9 h-9 rounded-lg bg-indigo-500/15 border border-indigo-500/25 flex items-center justify-center flex-shrink-0">
          <ArrowLeftRight className="w-4 h-4 text-indigo-400" />
        </div>
        <div>
          <h2 className="text-base font-bold text-zinc-100">Confirm Semester Switch</h2>
          <p className="text-xs text-zinc-500 mt-0.5">This will modify channel visibility and swap roles for all members.</p>
        </div>
      </div>

      <div className="bg-zinc-800/60 border border-zinc-700 rounded-lg p-4 flex items-center justify-between gap-3">
        <div className="text-center flex-1">
          <p className="text-[10px] text-zinc-500 uppercase tracking-wider mb-1">Hiding</p>
          <p className="text-sm font-bold text-zinc-200 font-mono">{runOld}</p>
        </div>
        <ChevronRight className="w-5 h-5 text-indigo-400 flex-shrink-0" />
        <div className="text-center flex-1">
          <p className="text-[10px] text-zinc-500 uppercase tracking-wider mb-1">Showing</p>
          <p className="text-sm font-bold text-indigo-300 font-mono">{runNew}</p>
        </div>
      </div>

      <p className="text-xs text-zinc-500">
        Role mappings and cleanup roles configured for <span className="text-zinc-300 font-semibold">{runOld}</span> will be applied to all matching members.
        This cannot be undone automatically.
      </p>
      <div className="flex justify-between gap-4 rounded border border-zinc-800 bg-zinc-950/40 px-3 py-2 text-xs">
        <span className="text-zinc-500">@everyone View Channel on {runNew}</span>
        <span className={newSemesterConfig?.everyoneViewChannel ? "text-emerald-300" : "text-red-300"}>
          {newSemesterConfig?.everyoneViewChannel ? "True" : "False"}
        </span>
      </div>

      <div className="flex gap-3 pt-1">
        <button onClick={onClose}
          className="flex-1 py-2.5 rounded-lg text-sm font-semibold bg-zinc-800 hover:bg-zinc-700 text-zinc-300 transition-colors">
          Cancel
        </button>
        <button onClick={onRun}
          className="flex-1 py-2.5 rounded-lg text-sm font-semibold bg-indigo-600 hover:bg-indigo-500 text-white transition-colors flex items-center justify-center gap-2">
          <Play className="w-4 h-4" /> Run Switch
        </button>
      </div>
    </ModalOverlay>
  );
}

interface ConfirmSetupModalProps {
  open: boolean;
  setupSemester: string;
  setupVisible: boolean;
  setupClearRoles: boolean;
  setupConfig: SemesterConfig | undefined;
  onClose: () => void;
  onRun: () => void;
}

export function ConfirmSetupModal({ open, setupSemester, setupVisible, setupClearRoles, setupConfig, onClose, onRun }: ConfirmSetupModalProps) {
  if (!open) return null;
  return (
    <ModalOverlay onClose={onClose} panelClassName="w-full max-w-md p-6 space-y-5">
      <div className="flex items-start gap-3">
        <div className="w-9 h-9 rounded-lg bg-amber-500/15 border border-amber-500/25 flex items-center justify-center flex-shrink-0">
          <Settings2 className="w-4 h-4 text-amber-400" />
        </div>
        <div>
          <h2 className="text-base font-bold text-zinc-100">Confirm Semester Setup</h2>
          <p className="text-xs text-zinc-500 mt-0.5">This changes one semester without applying transition role mappings.</p>
        </div>
      </div>

      <div className="bg-zinc-800/60 border border-zinc-700 rounded-lg p-4 space-y-2 text-xs">
        <div className="flex justify-between gap-4">
          <span className="text-zinc-500">Semester</span>
          <span className="font-semibold text-zinc-200">{setupSemester}</span>
        </div>
        <div className="flex justify-between gap-4">
          <span className="text-zinc-500">Channels</span>
          <span className={setupVisible ? "text-emerald-300" : "text-red-300"}>{setupVisible ? "Show" : "Hide"}</span>
        </div>
        <div className="flex justify-between gap-4">
          <span className="text-zinc-500">@everyone View Channel</span>
          <span className={setupVisible && setupConfig?.everyoneViewChannel ? "text-emerald-300" : "text-red-300"}>
            {setupVisible && setupConfig?.everyoneViewChannel ? "True" : "False"}
          </span>
        </div>
        <div className="flex justify-between gap-4">
          <span className="text-zinc-500">Cleanup roles</span>
          <span className={setupClearRoles ? "text-red-300" : "text-zinc-400"}>{setupClearRoles ? "Clear members" : "Keep unchanged"}</span>
        </div>
      </div>

      <div className="flex gap-3 pt-1">
        <button onClick={onClose}
          className="flex-1 py-2.5 rounded-lg text-sm font-semibold bg-zinc-800 hover:bg-zinc-700 text-zinc-300 transition-colors">
          Cancel
        </button>
        <button onClick={onRun}
          className="flex-1 py-2.5 rounded-lg text-sm font-semibold bg-amber-600 hover:bg-amber-500 text-white transition-colors flex items-center justify-center gap-2">
          <Play className="w-4 h-4" /> Run Setup
        </button>
      </div>
    </ModalOverlay>
  );
}

interface ModeInfoModalProps {
  open: boolean;
  runMode: "switch" | "setup";
  onClose: () => void;
}

export function ModeInfoModal({ open, runMode, onClose }: ModeInfoModalProps) {
  if (!open) return null;
  return (
    <ModalOverlay onClose={onClose} panelClassName="w-full max-w-lg overflow-hidden">
      <div className="flex items-start gap-3 p-5 border-b border-zinc-800">
          <div className={cn("w-9 h-9 rounded-lg border flex items-center justify-center flex-shrink-0",
            runMode === "switch"
              ? "bg-indigo-500/15 border-indigo-500/25"
              : "bg-amber-500/15 border-amber-500/25")}>
            {runMode === "switch"
              ? <ArrowLeftRight className="w-4 h-4 text-indigo-400" />
              : <Settings2 className="w-4 h-4 text-amber-400" />}
          </div>
          <div className="flex-1">
            <h2 className="text-base font-bold text-zinc-100">
              How Semester {runMode === "switch" ? "Switch" : "Setup"} works
            </h2>
            <p className="text-xs text-zinc-500 mt-1">
              {runMode === "switch"
                ? "Use this when students advance from one configured semester to another."
                : "Use this to initialize or reset one semester without advancing students."}
            </p>
          </div>
          <button onClick={onClose}
            className="text-zinc-500 hover:text-zinc-200 transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          {runMode === "switch" ? (
            <>
              <ol className="space-y-2 text-sm text-zinc-300">
                <li><span className="text-indigo-400 font-bold mr-2">1.</span>Hides categories configured for the current semester.</li>
                <li><span className="text-indigo-400 font-bold mr-2">2.</span>Shows categories configured for the next semester and applies its saved <span className="font-mono text-zinc-100">@everyone View Channel</span> value.</li>
                <li><span className="text-indigo-400 font-bold mr-2">3.</span>Applies Role Mappings from the current semester, such as <span className="font-mono text-zinc-100">@year1 → @year2</span>.</li>
                <li><span className="text-indigo-400 font-bold mr-2">4.</span>Removes the current semester's Cleanup Roles from their members.</li>
              </ol>
              <div className="rounded-lg border border-indigo-500/20 bg-indigo-500/10 p-3 text-xs text-indigo-200">
                Allowed Transitions can restrict which semester combinations may be executed.
              </div>
            </>
          ) : (
            <>
              <ol className="space-y-2 text-sm text-zinc-300">
                <li><span className="text-amber-400 font-bold mr-2">1.</span>Selects one semester configuration.</li>
                <li><span className="text-amber-400 font-bold mr-2">2.</span>Shows or hides that semester's configured categories. Show uses its saved <span className="font-mono text-zinc-100">@everyone View Channel</span> value; Hide always uses false.</li>
                <li><span className="text-amber-400 font-bold mr-2">3.</span>Optionally removes its configured Cleanup Roles from all members.</li>
              </ol>
              <div className="rounded-lg border border-amber-500/20 bg-amber-500/10 p-3 text-xs text-amber-200">
                Setup never applies Role Mappings and does not advance year roles. It only affects the selected semester.
              </div>
            </>
          )}
        </div>
    </ModalOverlay>
  );
}

export type { RunProgress };
