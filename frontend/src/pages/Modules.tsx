import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AlertCircle, SlidersHorizontal } from "lucide-react";
import { adminApi, type HackedAccountTrapSettings } from "@/lib/api";
import { useSelectedGuildId, Toggle } from "@/components/modules/shared";

function ModuleCard({ name, description, href, enabled, onToggle, toggling, loading, error }: {
  name: string;
  description: string;
  href: string;
  enabled?: boolean;
  onToggle?: (enabled: boolean) => void;
  toggling?: boolean;
  loading?: boolean;
  error?: string | null;
}) {
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
      <div className="px-4 pb-4 pt-3">
        <Link
          to={href}
          className="flex items-center gap-1.5 text-xs font-bold text-rose-400 hover:text-rose-300 transition-colors uppercase tracking-wider"
        >
          <SlidersHorizontal className="w-3.5 h-3.5" /> Settings
        </Link>
      </div>
    </div>
  );
}

export function Modules() {
  const guildId = useSelectedGuildId();
  const [trapSettings, setTrapSettings] = useState<HackedAccountTrapSettings | null>(null);
  const [spamLogChannelId, setSpamLogChannelId] = useState<string | null>(null);
  const [rrEnabled, setRrEnabled] = useState<boolean | null>(null);
  const [trapToggling, setTrapToggling] = useState(false);
  const [rrToggling, setRrToggling] = useState(false);
  const [trapError, setTrapError] = useState<string | null>(null);
  const [rrError, setRrError] = useState<string | null>(null);
  const [adEnabled, setAdEnabled] = useState<boolean | null>(null);
  const [adToggling, setAdToggling] = useState(false);
  const [adError, setAdError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setTrapSettings(null);
    setSpamLogChannelId(null);
    setRrEnabled(null);
    setAdEnabled(null);
    setTrapError(null);
    setRrError(null);
    setAdError(null);
    if (!guildId) return () => { cancelled = true; };
    adminApi.getHackedAccountTrap(guildId)
      .then(data => { if (!cancelled) setTrapSettings(data); })
      .catch(error => { if (!cancelled) console.error(error); });
    adminApi.getSettings(guildId)
      .then(s => { if (!cancelled) setSpamLogChannelId(s.spam_log_channel_id); })
      .catch(error => { if (!cancelled) console.error(error); });
    adminApi.getReactionRolesEnabled(guildId)
      .then(r => { if (!cancelled) setRrEnabled(r.enabled); })
      .catch(error => { if (!cancelled) console.error(error); });
    adminApi.getAutoDeleteEnabled(guildId)
      .then(r => { if (!cancelled) setAdEnabled(r.enabled); })
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
      setTrapError("Set an Automod Log channel in Settings → Log Channels before enabling this module.");
      return;
    }
    setTrapToggling(true);
    setTrapSettings(prev => prev ? { ...prev, enabled } : prev);
    try {
      const next = await adminApi.saveHackedAccountTrap(guildId, { ...trapSettings, enabled });
      setTrapSettings(next);
    } catch (err: unknown) {
      setTrapSettings(prev => prev ? { ...prev, enabled: !enabled } : prev);
      const apiError = err as { response?: { data?: { error?: string } } };
      setTrapError(apiError.response?.data?.error ?? "Failed to change the module state.");
    } finally {
      setTrapToggling(false);
    }
  };

  const toggleRR = async (enabled: boolean) => {
    setRrError(null);
    setRrToggling(true);
    setRrEnabled(enabled);
    try {
      await adminApi.setReactionRolesEnabled(guildId, enabled);
    } catch (err: unknown) {
      setRrEnabled(prev => prev !== null ? !prev : prev);
      const apiError = err as { response?: { data?: { error?: string } } };
      setRrError(apiError.response?.data?.error ?? "Failed to change the module state.");
    } finally {
      setRrToggling(false);
    }
  };

  const toggleAD = async (enabled: boolean) => {
    setAdError(null);
    setAdToggling(true);
    setAdEnabled(enabled);
    try {
      await adminApi.setAutoDeleteEnabled(guildId, enabled);
    } catch (err: unknown) {
      setAdEnabled(prev => prev !== null ? !prev : prev);
      const apiError = err as { response?: { data?: { error?: string } } };
      setAdError(apiError.response?.data?.error ?? "Failed to change the module state.");
    } finally {
      setAdToggling(false);
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
            />
            <ModuleCard
              name="Reaction Roles"
              description="Let members self-assign roles by reacting to a message with emojis."
              href="/modules/reaction-roles"
              enabled={rrEnabled ?? false}
              onToggle={toggleRR}
              toggling={rrToggling}
              loading={rrEnabled === null}
              error={rrError}
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
            />
          </div>
        )}
      </div>
    </div>
  );
}
