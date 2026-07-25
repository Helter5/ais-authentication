import { useEffect, useState, type ElementType, type ReactNode } from "react";
import { adminApi } from "@/lib/api";
import {
  AlertCircle, CheckCircle2, Clock, Loader2, MemoryStick,
  Plus, Server, Shield, Ticket, Trash2, TriangleAlert, Users,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

type BotStatus = Awaited<ReturnType<typeof adminApi.getAdminStatus>>;
type BotGuild = Awaited<ReturnType<typeof adminApi.getBotGuilds>>[number];
type Notice = { type: "success" | "error"; message: string };

function Panel({ icon: Icon, title, description, children }: {
  icon: ElementType;
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-lg border border-zinc-800 bg-zinc-900">
      <div className="border-b border-zinc-800 px-5 py-4">
        <div className="flex items-center gap-2">
          <Icon className="h-4 w-4 text-indigo-400" />
          <h2 className="text-sm font-bold text-zinc-100">{title}</h2>
        </div>
        <p className="mt-1 text-xs text-zinc-500">{description}</p>
      </div>
      <div className="p-5">{children}</div>
    </section>
  );
}

function Metric({ icon: Icon, label, value }: { icon: ElementType; label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-900 p-4">
      <div className="flex items-center gap-2 text-zinc-500">
        <Icon className="h-4 w-4" />
        <span className="text-xs font-semibold uppercase tracking-wider">{label}</span>
      </div>
      <p className="mt-2 text-2xl font-bold text-zinc-100">{value}</p>
    </div>
  );
}

function formatUptime(seconds: number) {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return [days && `${days}d`, (days || hours) && `${hours}h`, `${minutes}m`].filter(Boolean).join(" ");
}

export function Admin() {
  const [status, setStatus] = useState<BotStatus | null>(null);
  const [guilds, setGuilds] = useState<BotGuild[]>([]);
  const [allowedGuildIds, setAllowedGuildIds] = useState<string[]>([]);
  const [newGuildId, setNewGuildId] = useState("");
  const [maintenance, setMaintenance] = useState(false);
  const [superAdminIds, setSuperAdminIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [maintenanceConfirmOpen, setMaintenanceConfirmOpen] = useState(false);

  const showNotice = (next: Notice) => {
    setNotice(next);
    window.setTimeout(() => setNotice(null), 3500);
  };

  useEffect(() => {
    Promise.all([
      adminApi.getAdminStatus(),
      adminApi.getBotGuilds(),
      adminApi.getAllowedGuilds(),
      adminApi.getMaintenance(),
      adminApi.getAdminSettings(),
    ])
      .then(([statusData, guildData, allowedData, maintenanceData, settingsData]) => {
        setStatus(statusData);
        setGuilds(guildData);
        setAllowedGuildIds(allowedData.guildIds);
        setMaintenance(maintenanceData.enabled);
        setSuperAdminIds(settingsData.super_admin_users.split(',').map((s: string) => s.trim()).filter(Boolean));
      })
      .catch(() => showNotice({ type: "error", message: "Failed to load admin data." }))
      .finally(() => setLoading(false));
  }, []);

  const saveAllowedGuilds = async (ids: string[]) => {
    if (ids.length === 0) {
      showNotice({ type: "error", message: "Keep at least one server allowed to avoid locking out the dashboard." });
      return;
    }
    setBusy("guilds");
    try {
      const result = await adminApi.setAllowedGuilds(ids);
      setAllowedGuildIds(result.guildIds);
      setGuilds(current => current.map(guild => ({ ...guild, allowed: result.guildIds.includes(guild.id) })));
      showNotice({ type: "success", message: "Allowed servers updated." });
    } catch {
      showNotice({ type: "error", message: "Failed to update allowed servers." });
    } finally {
      setBusy(null);
    }
  };

  const addGuild = () => {
    const id = newGuildId.trim();
    if (!/^\d{17,20}$/.test(id)) {
      showNotice({ type: "error", message: "Enter a valid Discord server ID." });
      return;
    }
    if (allowedGuildIds.includes(id)) {
      showNotice({ type: "error", message: "That server is already allowed." });
      return;
    }
    setNewGuildId("");
    void saveAllowedGuilds([...allowedGuildIds, id]);
  };

  const updateMaintenance = async (enabled: boolean) => {
    setBusy("maintenance");
    try {
      await adminApi.setMaintenance(enabled);
      setMaintenance(enabled);
      setMaintenanceConfirmOpen(false);
      showNotice({ type: "success", message: `Maintenance mode ${enabled ? "enabled" : "disabled"}.` });
    } catch {
      showNotice({ type: "error", message: "Failed to update maintenance mode." });
    } finally {
      setBusy(null);
    }
  };

  const toggleMaintenance = () => {
    if (maintenance) {
      void updateMaintenance(false);
    } else {
      setMaintenanceConfirmOpen(true);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center md:pl-64">
        <Loader2 className="h-7 w-7 animate-spin text-indigo-400" />
      </div>
    );
  }

  const unknownAllowedIds = allowedGuildIds.filter(id => !guilds.some(guild => guild.id === id));

  return (
    <div className="min-h-screen md:pl-64">
      <div className="border-b border-zinc-800 px-4 pb-4 pt-5 sm:px-6">
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Admin Control</h1>
        <p className="mt-1 text-sm text-zinc-500">Global bot operations and access controls.</p>
      </div>

      <div className="space-y-5 px-4 py-5 sm:px-6">
        {notice && (
          <div className={cn(
            "flex items-center gap-2 rounded-lg border px-4 py-3 text-sm",
            notice.type === "success"
              ? "border-emerald-500/20 bg-emerald-500/10 text-emerald-400"
              : "border-red-500/20 bg-red-500/10 text-red-400",
          )}>
            {notice.type === "success" ? <CheckCircle2 className="h-4 w-4" /> : <AlertCircle className="h-4 w-4" />}
            {notice.message}
          </div>
        )}

        {status && (
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-6">
            <Metric icon={Clock} label="Uptime" value={formatUptime(status.uptime)} />
            <Metric icon={Server} label="Servers" value={status.guildCount} />
            <Metric icon={Users} label="Verified" value={status.verifiedCount} />
            <Metric icon={Ticket} label="Active Codes" value={status.activeCodesCount} />
            <Metric icon={TriangleAlert} label="Warnings" value={status.totalWarns} />
            <Metric icon={MemoryStick} label="Memory" value={`${status.memoryMB} MB`} />
          </div>
        )}

        <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
          <Panel icon={Server} title="Allowed Servers" description="Only these Discord servers can use the bot and dashboard.">
            <div className="mb-4 flex gap-2">
              <input
                value={newGuildId}
                onChange={event => setNewGuildId(event.target.value)}
                onKeyDown={event => { if (event.key === "Enter") addGuild(); }}
                placeholder="Discord server ID"
                className="min-w-0 flex-1 rounded border border-zinc-700 bg-zinc-800 px-3 py-2 font-mono text-sm text-zinc-200 outline-none focus:border-indigo-500"
              />
              <button onClick={addGuild} disabled={busy === "guilds"}
                className="flex items-center gap-1.5 rounded bg-indigo-600 px-3 py-2 text-xs font-bold text-white hover:bg-indigo-500 disabled:opacity-50">
                <Plus className="h-4 w-4" /> Add
              </button>
            </div>

            <div className="space-y-2">
              {guilds.map(guild => {
                const allowed = allowedGuildIds.includes(guild.id);
                return (
                  <div key={guild.id} className="flex items-center gap-3 rounded border border-zinc-800 bg-zinc-950/40 p-3">
                    {guild.icon
                      ? <img src={guild.icon} alt="" className="h-9 w-9 rounded-full" />
                      : <div className="flex h-9 w-9 items-center justify-center rounded-full bg-zinc-800"><Server className="h-4 w-4 text-zinc-500" /></div>}
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-zinc-200">{guild.name}</p>
                      <p className="font-mono text-[11px] text-zinc-600">{guild.id}</p>
                      <p className="text-[11px] text-zinc-500">{guild.memberCount} members, {guild.verifiedCount} verified</p>
                    </div>
                    <button
                      onClick={() => saveAllowedGuilds(allowed ? allowedGuildIds.filter(id => id !== guild.id) : [...allowedGuildIds, guild.id])}
                      disabled={busy === "guilds"}
                      className={cn("rounded px-3 py-1.5 text-xs font-bold disabled:opacity-50",
                        allowed ? "bg-emerald-500/15 text-emerald-400 hover:bg-red-500/15 hover:text-red-400" : "bg-zinc-800 text-zinc-400 hover:text-zinc-200")}>
                      {allowed ? "Allowed" : "Allow"}
                    </button>
                  </div>
                );
              })}
              {unknownAllowedIds.map(id => (
                <div key={id} className="flex items-center gap-3 rounded border border-zinc-800 bg-zinc-950/40 p-3">
                  <Server className="h-5 w-5 text-zinc-600" />
                  <div className="min-w-0 flex-1">
                    <p className="text-xs text-zinc-400">Bot is not connected</p>
                    <p className="font-mono text-[11px] text-zinc-600">{id}</p>
                  </div>
                  <button onClick={() => saveAllowedGuilds(allowedGuildIds.filter(item => item !== id))}
                    disabled={busy === "guilds"} className="rounded p-2 text-zinc-500 hover:bg-red-500/10 hover:text-red-400 disabled:opacity-50">
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
          </Panel>

          <div className="space-y-5">
            <Panel icon={TriangleAlert} title="Maintenance Mode" description="Immediately block every slash command on every allowed server.">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className={cn("text-sm font-bold", maintenance ? "text-amber-400" : "text-emerald-400")}>
                    {maintenance ? "Commands disabled" : "Bot operational"}
                  </p>
                  <p className="mt-1 text-xs text-zinc-500">The web dashboard remains available.</p>
                </div>
                <button onClick={toggleMaintenance} disabled={busy === "maintenance"}
                  className={cn("relative h-6 w-11 rounded-full transition-colors disabled:opacity-50", maintenance ? "bg-amber-500" : "bg-zinc-700")}>
                  <span className={cn("absolute left-1 top-1 h-4 w-4 rounded-full bg-white transition-transform", maintenance && "translate-x-5")} />
                </button>
              </div>
            </Panel>

          </div>
        </div>

        <Panel icon={Shield} title="Super Admin" description="Configured via SUPER_ADMIN_IDS in .env — cannot be changed from the dashboard.">
          <div className="space-y-2">
            {superAdminIds.length === 0 ? (
              <p className="text-xs text-zinc-600">No SUPER_ADMIN_IDS set in environment.</p>
            ) : superAdminIds.map(id => (
              <div key={id} className="flex items-center gap-3 rounded border border-zinc-800 bg-zinc-950/50 px-3 py-2.5 opacity-60 cursor-not-allowed select-none">
                <Shield className="h-3.5 w-3.5 text-zinc-500 flex-shrink-0" />
                <span className="font-mono text-sm text-zinc-400">{id}</span>
                <span className="ml-auto text-[10px] font-bold uppercase tracking-wider text-zinc-600 border border-zinc-700 rounded px-1.5 py-0.5">env only</span>
              </div>
            ))}
            <p className="text-[11px] text-zinc-600 pt-1">Edit <span className="font-mono text-zinc-500">SUPER_ADMIN_IDS</span> in <span className="font-mono text-zinc-500">.env</span> and restart the bot to change super admins.</p>
          </div>
        </Panel>

        {status && <p className="text-right text-[11px] text-zinc-600">Runtime: {status.nodeVersion}</p>}
      </div>

      <Dialog open={maintenanceConfirmOpen} onOpenChange={setMaintenanceConfirmOpen}>
        <DialogContent className="max-w-md border-amber-500/30 bg-zinc-900 text-zinc-100">
          <DialogHeader>
            <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-full bg-amber-500/10">
              <TriangleAlert className="h-5 w-5 text-amber-400" />
            </div>
            <DialogTitle>Enable maintenance mode?</DialogTitle>
            <DialogDescription className="leading-relaxed">
              Every slash command will be disabled immediately across all allowed servers. The web dashboard will remain available.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="mt-2">
            <Button variant="outline" onClick={() => setMaintenanceConfirmOpen(false)}
              className="border-zinc-700 bg-zinc-800 text-zinc-200 hover:bg-zinc-700">
              Cancel
            </Button>
            <Button onClick={() => updateMaintenance(true)} disabled={busy === "maintenance"}
              className="bg-amber-600 text-white hover:bg-amber-500">
              {busy === "maintenance" && <Loader2 className="h-4 w-4 animate-spin" />}
              Enable Maintenance
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
