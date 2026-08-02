import { useEffect, useState, type ElementType } from "react";
import { Link } from "react-router-dom";
import {
  AlertCircle, Copy, Hash, Layers3, Loader2,
  Mic2, RefreshCw, Server, ShieldCheck, Users,
} from "lucide-react";
import { adminApi, apiErrorMessage, type DashboardData } from "@/lib/api";
import { useSelectedGuildId } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";

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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setLoading(true);
    setError(null);
    if (!guildId) {
      setLoading(false);
      return () => { cancelled = true; };
    }
    adminApi.getDashboard(guildId)
      .then(dashboard => {
        if (cancelled) return;
        setData(dashboard);
      })
      .catch(err => {
        if (!cancelled) setError(apiErrorMessage(err, "Failed to load dashboard."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [guildId]);

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
        {error && (
          <div className="flex items-center gap-2 rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">
            <AlertCircle className="h-4 w-4" />
            {error}
          </div>
        )}

        <section className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-bold uppercase tracking-widest text-zinc-300">Server Info</h2>
            <button onClick={() => navigator.clipboard.writeText(data.server.id)
                .then(() => toast("Server ID copied."))
                .catch(() => toast("Failed to copy Server ID.", "error"))}
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
      </div>
    </div>
  );
}
