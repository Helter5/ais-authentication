import { useEffect, useState, type ElementType } from "react";
import { Link } from "react-router-dom";
import {
  Activity, AlertCircle, Clock, Copy, GraduationCap, Hash, History, Layers3, Loader2,
  MemoryStick, Mic2, RefreshCw, Server, ShieldCheck, Ticket, Users,
} from "lucide-react";
import { formatDistanceToNow } from "date-fns";
import { cn } from "@/lib/utils";
import { adminApi, apiErrorMessage, type DashboardData } from "@/lib/api";
import { useSelectedGuildId } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";
import { ModalOverlay } from "@/components/ui/modal-overlay";
import { LdapConnectionStatus } from "@/components/LdapConnectionStatus";

const CATEGORY_STYLES: Record<string, string> = {
  dashboard: "bg-indigo-500/10 text-indigo-300 border-indigo-500/20",
  commands: "bg-zinc-700/40 text-zinc-300 border-zinc-600/40",
  automod: "bg-amber-500/10 text-amber-300 border-amber-500/20",
  verification: "bg-emerald-500/10 text-emerald-300 border-emerald-500/20",
};

function Stat({ icon: Icon, label, value }: { icon: ElementType; label: string; value: string | number }) {
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

function pluralize(count: number, noun: string) {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}

function formatUptime(seconds: number) {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return [days && `${days}d`, (days || hours) && `${hours}h`, `${minutes}m`].filter(Boolean).join(" ");
}

function RemovedDetailsModal({ removedUsers, onClose }: {
  removedUsers: DashboardData["synchronization"]["removedUsers"];
  onClose: () => void;
}) {
  return (
    <ModalOverlay onClose={onClose} panelClassName="w-full max-w-lg overflow-hidden">
      <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-800">
        <h2 className="text-base font-bold text-zinc-100">Removed on last sync</h2>
        <button onClick={onClose} className="text-zinc-500 hover:text-zinc-200 transition-colors text-sm">Close</button>
      </div>
      <div className="max-h-[60vh] overflow-y-auto scrollbar-thin">
        {removedUsers.length === 0 ? (
          <p className="px-5 py-6 text-sm text-zinc-500">Nothing was removed on the last run.</p>
        ) : (
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-zinc-800">
                <th className="text-left px-5 py-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-500">Discord</th>
                <th className="text-left px-5 py-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-500">AIS ID</th>
                <th className="text-left px-5 py-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-500">Email</th>
              </tr>
            </thead>
            <tbody>
              {removedUsers.map(u => (
                <tr key={u.discordId} className="border-b border-zinc-800/50">
                  <td className="px-5 py-2">
                    <div className="text-zinc-300">{u.username ?? "Unknown (left server)"}</div>
                    <div className="font-mono text-[11px] text-zinc-600">{u.discordId}</div>
                  </td>
                  <td className="px-5 py-2 font-mono text-zinc-400">{u.aisId}</td>
                  <td className="px-5 py-2 text-zinc-500">{u.email}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </ModalOverlay>
  );
}

export function Dashboard() {
  const guildId = useSelectedGuildId();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showRemovedDetails, setShowRemovedDetails] = useState(false);
  const [botStatus, setBotStatus] = useState<Awaited<ReturnType<typeof adminApi.getAdminStatus>> | null>(null);
  const { toast } = useToast();

  // Bot Status (and the LDAP Connection widget nested in it, which fetches its own data) is
  // global (not per-guild) and super-admin-only, unlike the rest of this page - fetched once,
  // independent of guildId, and simply skipped for regular managers (getAdminStatus would 403
  // for them).
  useEffect(() => {
    let cancelled = false;
    adminApi.getAdminAccess()
      .then(access => {
        if (cancelled || !access.allowed) return;
        return adminApi.getAdminStatus().then(status => { if (!cancelled) setBotStatus(status); });
      })
      .catch(() => { /* not a super admin, or bot status unavailable - just hide the section */ });
    return () => { cancelled = true; };
  }, []);

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

        {botStatus && (
          <section className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
            <div className="mb-4 flex items-center gap-2">
              <Activity className="h-4 w-4 text-indigo-400" />
              <h2 className="text-sm font-bold uppercase tracking-widest text-zinc-300">Bot Status</h2>
            </div>
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
              <Stat icon={Clock} label="Uptime" value={formatUptime(botStatus.uptime)} />
              <Stat icon={Server} label="Servers" value={botStatus.guildCount} />
              <Stat icon={Users} label="Verified" value={botStatus.verifiedCount} />
              <Stat icon={Ticket} label="Active Codes" value={botStatus.activeCodesCount} />
              <Stat icon={MemoryStick} label="Memory" value={`${botStatus.memoryMB} MB`} />
            </div>

            <div className="mt-5 border-t border-zinc-800 pt-4">
              <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-zinc-600">LDAP Connection</p>
              <LdapConnectionStatus />
            </div>
          </section>
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
                Checks verified users against the server's Verified role whenever the bot starts up, and
                removes database records for anyone who no longer holds it.
              </p>
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Last sync</p>
              <p className="mt-1 text-sm font-semibold text-zinc-200">{formatSyncDate(data.synchronization.lastSyncAt)}</p>
            </div>
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
              <div className="flex items-center justify-between gap-2">
                <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Last run result</p>
                {!!data.synchronization.removedCount && (
                  <button onClick={() => setShowRemovedDetails(true)}
                    className="text-[11px] font-semibold text-rose-400 hover:text-rose-300 transition-colors">
                    Details
                  </button>
                )}
              </div>
              <p className="mt-1 text-sm font-semibold text-zinc-200">
                {data.synchronization.checkedCount === null ? "-" : (
                  <>
                    {pluralize(data.synchronization.checkedCount, "checked")}
                    {data.synchronization.removedCount ? (
                      <span className="text-amber-400"> · {pluralize(data.synchronization.removedCount, "removed")}</span>
                    ) : (
                      <span className="text-emerald-400"> · in sync</span>
                    )}
                  </>
                )}
              </p>
            </div>
          </div>
        </section>

        <section className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
          <div className="mb-4 flex items-center gap-2">
            <GraduationCap className="h-4 w-4 text-indigo-400" />
            <h2 className="text-sm font-bold uppercase tracking-widest text-zinc-300">Verification Config</h2>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Allowed Faculties</p>
              <p className="mt-1 text-sm font-semibold text-zinc-200">
                {data.verificationConfig.allowedFaculties.length > 0
                  ? data.verificationConfig.allowedFaculties.join(", ") : "Not set"}
              </p>
            </div>
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Required Status</p>
              <p className="mt-1 text-sm font-semibold text-zinc-200">{data.verificationConfig.requiredAccountStatus}</p>
            </div>
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Verified Role</p>
              <p className="mt-1 text-sm font-semibold text-zinc-200">
                {data.verificationConfig.verifiedRoleName ?? (data.verificationConfig.verifiedRoleId ? "Role not found" : "Not set")}
              </p>
            </div>
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Inactive Role</p>
              <p className="mt-1 text-sm font-semibold text-zinc-200">
                {data.verificationConfig.inactiveRoleName ?? (data.verificationConfig.inactiveRoleId ? "Role not found" : "Not set")}
              </p>
            </div>
            <div className="rounded-lg border border-zinc-800 bg-zinc-950/35 p-4 sm:col-span-2">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-zinc-600">Verified Members</p>
              <p className="mt-1 text-sm font-semibold text-zinc-200">
                {data.verificationConfig.verifiedCount} / {data.server.memberCount}
                {data.server.memberCount > 0 && (
                  <span className="ml-1.5 text-zinc-500">
                    ({Math.round((data.verificationConfig.verifiedCount / data.server.memberCount) * 100)}%)
                  </span>
                )}
              </p>
            </div>
          </div>
        </section>

        <section className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
          <div className="mb-4 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <History className="h-4 w-4 text-indigo-400" />
              <h2 className="text-sm font-bold uppercase tracking-widest text-zinc-300">Recent Activity</h2>
            </div>
            <Link to="/access-logs" className="text-xs font-semibold text-rose-400 hover:text-rose-300">View all logs</Link>
          </div>
          {data.recentActivity.length === 0 ? (
            <p className="text-sm text-zinc-500">No recent activity.</p>
          ) : (
            <ul className="divide-y divide-zinc-800/70">
              {data.recentActivity.map((entry, i) => (
                <li key={i} className="flex items-center gap-3 py-2.5 text-sm">
                  <span className={cn("flex-shrink-0 rounded border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider",
                    CATEGORY_STYLES[entry.category] ?? "bg-zinc-700/40 text-zinc-300 border-zinc-600/40")}>
                    {entry.category}
                  </span>
                  <span className="flex-1 truncate text-zinc-300">{entry.action}</span>
                  {entry.username && <span className="flex-shrink-0 text-zinc-500">{entry.username}</span>}
                  <span className="flex-shrink-0 text-xs text-zinc-600">
                    {formatDistanceToNow(new Date(entry.createdAt), { addSuffix: true })}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {showRemovedDetails && (
        <RemovedDetailsModal removedUsers={data.synchronization.removedUsers} onClose={() => setShowRemovedDetails(false)} />
      )}
    </div>
  );
}
