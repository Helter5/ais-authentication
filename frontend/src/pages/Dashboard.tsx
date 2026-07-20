import { useEffect, useMemo, useState, type ElementType } from "react";
import { Link } from "react-router-dom";
import {
  AlertCircle, CheckCircle2, Clock3, Copy, Hash, Layers3, Loader2,
  Mic2, RefreshCw, Save, Server, ShieldCheck, Users,
} from "lucide-react";
import { adminApi, type DashboardData } from "@/lib/api";
import { cn } from "@/lib/utils";
import { useSelectedGuildId } from "@/components/modules/shared";
const TIMEZONES = [
  "UTC",
  "Europe/Bratislava",
  "Europe/Prague",
  "Europe/Vienna",
  "Europe/Berlin",
  "Europe/London",
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Los_Angeles",
  "Asia/Tokyo",
  "Australia/Sydney",
];

function Stat({ icon: Icon, label, value }: { icon: ElementType; label: string; value: number }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-500/10">
        <Icon className="h-4 w-4 text-indigo-400" />
      </div>
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">{label}</p>
        <p className="text-lg font-bold text-zinc-100">{value}</p>
      </div>
    </div>
  );
}

function formatSyncDate(value: string | null) {
  if (!value) return "Not run yet";
  return new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export function Dashboard() {
  const guildId = useSelectedGuildId();
  const [data, setData] = useState<DashboardData | null>(null);
  const [nickname, setNickname] = useState("");
  const [timezone, setTimezone] = useState("UTC");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setLoading(true);
    setMessage(null);
    if (!guildId) {
      setLoading(false);
      return () => { cancelled = true; };
    }
    adminApi.getDashboard(guildId)
      .then(dashboard => {
        if (cancelled) return;
        setData(dashboard);
        setNickname(dashboard.settings.nickname);
        setTimezone(dashboard.settings.timezone);
      })
      .catch(error => {
        if (!cancelled) setMessage({ type: "error", text: error?.response?.data?.error ?? "Failed to load dashboard." });
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [guildId]);

  const timezoneOptions = useMemo(
    () => timezone && !TIMEZONES.includes(timezone) ? [timezone, ...TIMEZONES] : TIMEZONES,
    [timezone],
  );

  const detectTimezone = () => {
    const detected = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    setTimezone(detected);
    setMessage({ type: "success", text: `Detected ${detected}. Save to apply it.` });
  };

  const save = async () => {
    if (!guildId) return;
    setSaving(true);
    setMessage(null);
    try {
      const result = await adminApi.updateDashboardSettings(guildId, { nickname, timezone });
      setNickname(result.settings.nickname);
      setMessage({ type: "success", text: "Bot settings saved." });
    } catch (error: unknown) {
      const apiError = error as { response?: { data?: { error?: string } } };
      setMessage({ type: "error", text: apiError.response?.data?.error ?? "Failed to save bot settings." });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="flex min-h-[70vh] items-center justify-center md:pl-64"><Loader2 className="h-7 w-7 animate-spin text-indigo-400" /></div>;
  }

  if (!guildId || !data) {
    return (
      <div className="min-h-screen px-4 py-8 md:pl-64">
        <div className="rounded-lg border border-zinc-800 bg-zinc-900 p-5 text-sm text-zinc-400">
          No server selected. <Link to="/select-server" className="text-indigo-400 hover:text-indigo-300">Choose a server</Link>.
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen md:pl-64">
      <div className="flex items-center gap-4 border-b border-zinc-800 px-4 pb-5 pt-5 sm:px-6">
        {data.server.icon
          ? <img src={data.server.icon} alt="" className="h-14 w-14 rounded-2xl object-cover" />
          : <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-700"><Server className="h-6 w-6 text-white" /></div>}
        <div>
          <h1 className="text-2xl font-bold text-zinc-100">{data.server.name}</h1>
          <p className="text-sm text-zinc-500">Server dashboard</p>
        </div>
      </div>

      <div className="space-y-5 px-4 py-5 sm:px-6">
        {message && (
          <div className={cn(
            "flex items-center gap-2 rounded-lg border px-4 py-3 text-sm",
            message.type === "success"
              ? "border-emerald-500/20 bg-emerald-500/10 text-emerald-400"
              : "border-red-500/20 bg-red-500/10 text-red-400",
          )}>
            {message.type === "success" ? <CheckCircle2 className="h-4 w-4" /> : <AlertCircle className="h-4 w-4" />}
            {message.text}
          </div>
        )}

        <section className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-bold uppercase tracking-widest text-zinc-300">Server Info</h2>
            <button onClick={() => navigator.clipboard.writeText(data.server.id)}
              className="flex items-center gap-1.5 text-xs font-semibold text-rose-400 hover:text-rose-300">
              <Copy className="h-3.5 w-3.5" /> Copy Server ID
            </button>
          </div>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
            <Stat icon={Users} label="Members" value={data.server.memberCount} />
            <Stat icon={Layers3} label="Categories" value={data.server.categoryCount} />
            <Stat icon={Hash} label="Text Channels" value={data.server.textChannelCount} />
            <Stat icon={Mic2} label="Voice Channels" value={data.server.voiceChannelCount} />
            <Stat icon={ShieldCheck} label="Roles" value={data.server.roleCount} />
          </div>
        </section>

        <section className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
          <div className="mb-4 flex items-center gap-2">
            <RefreshCw className="h-4 w-4 text-indigo-400" />
            <div>
              <h2 className="text-sm font-bold uppercase tracking-widest text-zinc-300">Database Sync</h2>
              <p className="mt-1 text-xs text-zinc-500">
                Automatically verifies Discord membership and role records every six months.
              </p>
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Last successful sync</p>
              <p className="mt-1 text-sm font-semibold text-zinc-200">{formatSyncDate(data.synchronization.lastSync)}</p>
            </div>
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Next sync</p>
              <p className="mt-1 text-sm font-semibold text-zinc-200">{formatSyncDate(data.synchronization.nextSync)}</p>
            </div>
          </div>
        </section>

        <section className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
          <div className="mb-5">
            <h2 className="text-sm font-bold uppercase tracking-widest text-zinc-300">Bot Settings</h2>
            <p className="mt-1 text-xs text-zinc-500">Settings for the bot in this Discord server.</p>
          </div>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <div className="space-y-2">
              <label className="text-xs font-semibold text-zinc-300">Nickname</label>
              <input value={nickname} onChange={event => setNickname(event.target.value)} maxLength={32}
                placeholder="Discord Auth"
                className="w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2.5 text-sm text-zinc-200 outline-none focus:border-indigo-500" />
              <p className="text-[11px] text-zinc-600">The bot nickname shown in this server.</p>
            </div>

            <div className="space-y-2">
              <label className="text-xs font-semibold text-zinc-300">Timezone</label>
              <div className="flex gap-2">
                <select value={timezone} onChange={event => setTimezone(event.target.value)}
                  className="min-w-0 flex-1 rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2.5 text-sm text-zinc-200 outline-none focus:border-indigo-500">
                  {timezoneOptions.map(zone => <option key={zone} value={zone}>{zone}</option>)}
                </select>
                <button onClick={detectTimezone}
                  className="flex items-center gap-1.5 rounded-lg bg-zinc-800 px-3 py-2 text-xs font-semibold text-zinc-300 hover:bg-zinc-700">
                  <Clock3 className="h-4 w-4" /> Detect
                </button>
              </div>
              <p className="text-[11px] text-zinc-600">Used for dashboard dates and future scheduled actions.</p>
            </div>

          </div>

          <button onClick={save} disabled={saving}
            className="mt-5 flex items-center gap-2 rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-500 disabled:opacity-50">
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            Save Settings
          </button>
        </section>
      </div>
    </div>
  );
}
