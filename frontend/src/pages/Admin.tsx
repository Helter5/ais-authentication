import { useEffect, useState, type ElementType, type ReactNode } from "react";
import { adminApi } from "@/lib/api";
import {
  Loader2, Save,
  Plus, Server, Shield, Trash2, TriangleAlert,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";

type BotStatus = Awaited<ReturnType<typeof adminApi.getAdminStatus>>;
type BotGuild = Awaited<ReturnType<typeof adminApi.getBotGuilds>>[number];

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

export function Admin() {
  const [status, setStatus] = useState<BotStatus | null>(null);
  const [guilds, setGuilds] = useState<BotGuild[]>([]);
  const [allowedGuildIds, setAllowedGuildIds] = useState<string[]>([]);
  const [newGuildId, setNewGuildId] = useState("");
  const [maintenance, setMaintenance] = useState(false);
  const [maintenanceDraft, setMaintenanceDraft] = useState(false);
  const [superAdminIds, setSuperAdminIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [maintenanceConfirmOpen, setMaintenanceConfirmOpen] = useState(false);
  const { toast } = useToast();

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
        setMaintenanceDraft(maintenanceData.enabled);
        setSuperAdminIds(settingsData.super_admin_users.split(',').map((s: string) => s.trim()).filter(Boolean));
      })
      .catch(() => toast("Failed to load admin data.", "error"))
      .finally(() => setLoading(false));
  }, []);

  const saveAllowedGuilds = async (ids: string[]) => {
    if (ids.length === 0) {
      toast("Keep at least one server allowed to avoid locking out the dashboard.", "error");
      return;
    }
    setBusy("guilds");
    try {
      const result = await adminApi.setAllowedGuilds(ids);
      setAllowedGuildIds(result.guildIds);
      setGuilds(current => current.map(guild => ({ ...guild, allowed: result.guildIds.includes(guild.id) })));
      toast("Allowed servers updated.");
    } catch {
      toast("Failed to update allowed servers.", "error");
    } finally {
      setBusy(null);
    }
  };

  const addGuild = () => {
    const id = newGuildId.trim();
    if (!/^\d{17,20}$/.test(id)) {
      toast("Enter a valid Discord server ID.", "error");
      return;
    }
    if (allowedGuildIds.includes(id)) {
      toast("That server is already allowed.", "error");
      return;
    }
    setNewGuildId("");
    void saveAllowedGuilds([...allowedGuildIds, id]);
  };

  const applyMaintenance = async (enabled: boolean) => {
    setSaving(true);
    try {
      if (enabled !== maintenance) {
        await adminApi.setMaintenance(enabled);
        setMaintenance(enabled);
      }
      setMaintenanceDraft(enabled);
      setMaintenanceConfirmOpen(false);
      toast("Settings saved.");
    } catch {
      toast("Failed to save settings.", "error");
    } finally {
      setSaving(false);
    }
  };

  const handleSaveSettings = () => {
    if (maintenanceDraft && !maintenance) {
      // Enabling maintenance mode blocks every command on every server immediately - confirm first.
      setMaintenanceConfirmOpen(true);
      return;
    }
    void applyMaintenance(maintenanceDraft);
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

      <div className="space-y-5 px-4 py-5 pb-24 sm:px-6">
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
                  <p className={cn("text-sm font-bold", maintenanceDraft ? "text-amber-400" : "text-emerald-400")}>
                    {maintenanceDraft ? "Commands disabled" : "Bot operational"}
                  </p>
                  <p className="mt-1 text-xs text-zinc-500">The web dashboard remains available.</p>
                </div>
                <button onClick={() => setMaintenanceDraft(v => !v)}
                  className={cn("relative h-6 w-11 rounded-full transition-colors", maintenanceDraft ? "bg-amber-500" : "bg-zinc-700")}>
                  <span className={cn("absolute left-1 top-1 h-4 w-4 rounded-full bg-white transition-transform", maintenanceDraft && "translate-x-5")} />
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

      <div className="fixed bottom-0 left-0 right-0 md:pl-64 border-t border-zinc-800 bg-zinc-950/95 backdrop-blur">
        <div className="flex items-center gap-4 px-4 sm:px-6 py-3">
          <button onClick={handleSaveSettings} disabled={saving}
            className="flex items-center gap-2 rounded bg-indigo-600 px-6 py-2.5 text-sm font-bold text-white hover:bg-indigo-500 transition-colors disabled:opacity-50">
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            Save Settings
          </button>
        </div>
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
            <Button variant="outline" onClick={() => { setMaintenanceConfirmOpen(false); setMaintenanceDraft(maintenance); }}
              className="border-zinc-700 bg-zinc-800 text-zinc-200 hover:bg-zinc-700">
              Cancel
            </Button>
            <Button onClick={() => applyMaintenance(true)} disabled={saving}
              className="bg-amber-600 text-white hover:bg-amber-500">
              {saving && <Loader2 className="h-4 w-4 animate-spin" />}
              Enable Maintenance
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
