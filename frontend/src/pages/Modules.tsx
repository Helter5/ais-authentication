import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AlertCircle, Bell, SlidersHorizontal } from "lucide-react";
import { adminApi, apiErrorMessage, type HackedAccountTrapSettings } from "@/lib/api";
import { useSelectedGuildId, Toggle, LogChannelModal } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";

/** Modules that log to a Discord channel - the "Log Channel" button on the card only shows for these. */
const MODULE_LOG_EVENT_TYPES: Record<string, string[]> = {
  "Hacked Account Trap": ["HACKED_ACCOUNT_TRAP_TRIGGERED"],
};

function ModuleCard({ name, description, href, enabled, onToggle, toggling, loading, error, guildId }: {
  name: string;
  description: string;
  href: string;
  enabled?: boolean;
  onToggle?: (enabled: boolean) => void;
  toggling?: boolean;
  loading?: boolean;
  error?: string | null;
  guildId: string | null;
}) {
  const [showLogModal, setShowLogModal] = useState(false);
  const logEventTypes = MODULE_LOG_EVENT_TYPES[name];

  return (
    <div className="flex flex-col rounded-lg border border-zinc-700/60 bg-zinc-800/50 hover:border-zinc-600 transition-colors">
      <div className="flex items-start justify-between px-4 pt-4 pb-2">
        <p className="font-bold text-zinc-100 text-sm">{name}</p>
        {loading
          ? <div className="w-9 h-5 rounded-full bg-zinc-700 animate-pulse flex-shrink-0" />
          : onToggle !== undefined && enabled !== undefined && (
            <Toggle enabled={enabled} onChange={onToggle} disabled={toggling} />
          )}
      </div>
      <p className="text-xs text-zinc-500 leading-relaxed px-4 flex-1">{description}</p>
      {error && (
        <p className="mx-4 mt-3 flex items-start gap-1.5 rounded border border-amber-500/20 bg-amber-500/10 px-2.5 py-2 text-xs leading-relaxed text-amber-300">
          <AlertCircle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
          {error}
        </p>
      )}
      <div className="flex items-center gap-3 px-4 pb-4 pt-3">
        <Link
          to={href}
          className="flex items-center gap-1.5 text-xs font-bold text-rose-400 hover:text-rose-300 transition-colors uppercase tracking-wider"
        >
          <SlidersHorizontal className="w-3.5 h-3.5" /> Settings
        </Link>
        {logEventTypes && (
          <button
            onClick={() => setShowLogModal(true)}
            disabled={!guildId}
            className="flex items-center gap-1.5 text-xs font-bold text-zinc-400 hover:text-zinc-200 transition-colors uppercase tracking-wider disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Bell className="w-3.5 h-3.5" /> Log Channel
          </button>
        )}
      </div>

      {showLogModal && guildId && logEventTypes && (
        <LogChannelModal
          title={`Log Channel (${name})`}
          eventTypes={logEventTypes}
          guildId={guildId}
          onClose={() => setShowLogModal(false)}
        />
      )}
    </div>
  );
}

export function Modules() {
  const guildId = useSelectedGuildId();
  const [trapSettings, setTrapSettings] = useState<HackedAccountTrapSettings | null>(null);
  const [spamLogChannelId, setSpamLogChannelId] = useState<string | null>(null);
  const [trapToggling, setTrapToggling] = useState(false);
  const [trapError, setTrapError] = useState<string | null>(null);
  const [adEnabled, setAdEnabled] = useState<boolean | null>(null);
  const [adToggling, setAdToggling] = useState(false);
  const [adError, setAdError] = useState<string | null>(null);
  const [rmEnabled, setRmEnabled] = useState<boolean | null>(null);
  const [rmToggling, setRmToggling] = useState(false);
  const [rmError, setRmError] = useState<string | null>(null);
  const [amEnabled, setAmEnabled] = useState<boolean | null>(null);
  const [amToggling, setAmToggling] = useState(false);
  const [amError, setAmError] = useState<string | null>(null);
  const [tcEnabled, setTcEnabled] = useState<boolean | null>(null);
  const [tcToggling, setTcToggling] = useState(false);
  const [tcError, setTcError] = useState<string | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    let cancelled = false;
    setTrapSettings(null);
    setSpamLogChannelId(null);
    setAdEnabled(null);
    setTrapError(null);
    setAdError(null);
    setRmEnabled(null);
    setRmError(null);
    setAmEnabled(null);
    setAmError(null);
    setTcEnabled(null);
    setTcError(null);
    if (!guildId) return () => { cancelled = true; };
    adminApi.getHackedAccountTrap(guildId)
      .then(data => { if (!cancelled) setTrapSettings(data); })
      .catch(error => { if (!cancelled) console.error(error); });
    adminApi.getLogChannels(guildId)
      .then(r => {
        if (cancelled) return;
        setSpamLogChannelId(r.eventTypes.find(e => e.eventType === "HACKED_ACCOUNT_TRAP_TRIGGERED")?.channelId ?? null);
      })
      .catch(error => { if (!cancelled) console.error(error); });
    adminApi.getAutoDeleteEnabled(guildId)
      .then(r => { if (!cancelled) setAdEnabled(r.enabled); })
      .catch(error => { if (!cancelled) console.error(error); });
    adminApi.getRoleMenuEnabled(guildId)
      .then(r => { if (!cancelled) setRmEnabled(r.enabled); })
      .catch(error => { if (!cancelled) console.error(error); });
    adminApi.getAutoMentionEnabled(guildId)
      .then(r => { if (!cancelled) setAmEnabled(r.enabled); })
      .catch(error => { if (!cancelled) console.error(error); });
    adminApi.getThesisCounterEnabled(guildId)
      .then(r => { if (!cancelled) setTcEnabled(r.enabled); })
      .catch(error => { if (!cancelled) console.error(error); });
    return () => { cancelled = true; };
  }, [guildId]);

  const toggleTrap = async (enabled: boolean) => {
    if (!trapSettings) return;
    setTrapError(null);
    if (enabled && !trapSettings.trapChannelId) {
      setTrapError("Choose and save a trap channel in Settings before enabling this module.");
      return;
    }
    if (enabled && !spamLogChannelId) {
      setTrapError("Set a Log Channel below before enabling this module.");
      return;
    }
    setTrapToggling(true);
    setTrapSettings(prev => prev ? { ...prev, enabled } : prev);
    try {
      const next = await adminApi.saveHackedAccountTrap(guildId, { ...trapSettings, enabled });
      setTrapSettings(next);
      toast(enabled ? "Hacked Account Trap enabled." : "Hacked Account Trap disabled.");
    } catch (err: unknown) {
      setTrapSettings(prev => prev ? { ...prev, enabled: !enabled } : prev);
      setTrapError(apiErrorMessage(err, "Failed to change the module state."));
    } finally {
      setTrapToggling(false);
    }
  };

  const toggleAD = async (enabled: boolean) => {
    setAdError(null);
    setAdToggling(true);
    setAdEnabled(enabled);
    try {
      await adminApi.setAutoDeleteEnabled(guildId, enabled);
      toast(enabled ? "Auto Delete enabled." : "Auto Delete disabled.");
    } catch (err: unknown) {
      setAdEnabled(prev => prev !== null ? !prev : prev);
      const message = apiErrorMessage(err, "Failed to change the module state.");
      setAdError(message);
      toast(message, "error");
    } finally {
      setAdToggling(false);
    }
  };

  const toggleRM = async (enabled: boolean) => {
    setRmError(null);
    setRmToggling(true);
    setRmEnabled(enabled);
    try {
      await adminApi.setRoleMenuEnabled(guildId, enabled);
      toast(enabled ? "Role Menu enabled." : "Role Menu disabled.");
    } catch (err: unknown) {
      setRmEnabled(prev => prev !== null ? !prev : prev);
      setRmError(apiErrorMessage(err, "Failed to change the module state."));
    } finally {
      setRmToggling(false);
    }
  };

  const toggleAM = async (enabled: boolean) => {
    setAmError(null);
    setAmToggling(true);
    setAmEnabled(enabled);
    try {
      await adminApi.setAutoMentionEnabled(guildId, enabled);
      toast(enabled ? "Auto-Mentions enabled." : "Auto-Mentions disabled.");
    } catch (err: unknown) {
      setAmEnabled(prev => prev !== null ? !prev : prev);
      setAmError(apiErrorMessage(err, "Failed to change the module state."));
    } finally {
      setAmToggling(false);
    }
  };

  const toggleTC = async (enabled: boolean) => {
    setTcError(null);
    setTcToggling(true);
    setTcEnabled(enabled);
    try {
      await adminApi.setThesisCounterEnabled(guildId, enabled);
      toast(enabled ? "Thesis Countdown enabled." : "Thesis Countdown disabled.");
    } catch (err: unknown) {
      setTcEnabled(prev => prev !== null ? !prev : prev);
      setTcError(apiErrorMessage(err, "Failed to change the module state."));
    } finally {
      setTcToggling(false);
    }
  };

  return (
    <div className="flex flex-col gap-6 md:pl-64">
      <div className="px-4 sm:px-6 pt-5">
        <h1 className="text-2xl font-bold text-zinc-100 tracking-tight">Modules</h1>
      </div>
      <div className="px-4 sm:px-6 pb-8">
        {!guildId ? (
          <div className="flex items-center gap-2 text-zinc-500 text-sm p-4 bg-zinc-800/40 border border-zinc-700 rounded-lg">
            <AlertCircle className="w-4 h-4" /> No server selected. <Link to="/select-server" className="text-indigo-400 hover:text-indigo-300 underline ml-1">Pick one</Link>.
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            <ModuleCard
              name="Hacked Account Trap"
              description="Automatically moderates accounts that post in a designated trap channel."
              href="/modules/hacked-account-trap"
              enabled={trapSettings?.enabled ?? false}
              onToggle={toggleTrap}
              toggling={trapToggling}
              loading={trapSettings === null}
              error={trapError}
              guildId={guildId}
            />
            <ModuleCard
              name="Auto Delete"
              description="Automatically delete messages in configured channels after a set delay."
              href="/modules/autodelete"
              enabled={adEnabled ?? false}
              onToggle={toggleAD}
              toggling={adToggling}
              loading={adEnabled === null}
              error={adError}
              guildId={guildId}
            />
            <ModuleCard
              name="Role Menu"
              description="Let members self-assign roles via buttons or a dropdown menu, no moderator needed."
              href="/modules/rolemenu"
              enabled={rmEnabled ?? false}
              onToggle={toggleRM}
              toggling={rmToggling}
              loading={rmEnabled === null}
              error={rmError}
              guildId={guildId}
            />
            <ModuleCard
              name="Auto-Mentions"
              description="Automatically mention a role whenever a message is posted in a configured channel."
              href="/modules/automentions"
              enabled={amEnabled ?? false}
              onToggle={toggleAM}
              toggling={amToggling}
              loading={amEnabled === null}
              error={amError}
              guildId={guildId}
            />
            <ModuleCard
              name="Thesis Countdown"
              description="Renames a room's channel name daily to count down the days left until a BP/DP defense date."
              href="/modules/thesiscounter"
              enabled={tcEnabled ?? false}
              onToggle={toggleTC}
              toggling={tcToggling}
              loading={tcEnabled === null}
              error={tcError}
              guildId={guildId}
            />
          </div>
        )}
      </div>
    </div>
  );
}
