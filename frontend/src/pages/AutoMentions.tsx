import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { adminApi, apiErrorMessage } from "@/lib/api";
import { ArrowLeft, Plus, X, Loader2, AlertCircle, Hash, AtSign, Bell } from "lucide-react";
import { cn } from "@/lib/utils";
import { useToast } from "@/components/ui/toast";
import { useSelectedGuildId, Toggle, ChannelPicker, RoleSelect } from "@/components/modules/shared";

type AutoMention = { channel_id: string; role_id: string; enabled: boolean };
type DiscordRole = { id: string; name: string; color: string };
type DiscordChannel = { id: string; name: string };

export function AutoMentionsModule() {
  const guildId = useSelectedGuildId();
  const [enabled, setEnabled] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [mentions, setMentions] = useState<AutoMention[]>([]);
  const [roles, setRoles] = useState<DiscordRole[]>([]);
  const [channels, setChannels] = useState<DiscordChannel[]>([]);
  const [loading, setLoading] = useState(true);
  const [newChannel, setNewChannel] = useState<string | null>(null);
  const [newRole, setNewRole] = useState("");
  const [adding, setAdding] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    let cancelled = false;
    setLoading(Boolean(guildId));
    setEnabled(false);
    setMentions([]);
    setRoles([]);
    setChannels([]);
    setNewChannel(null);
    setNewRole("");
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getAutoMentionEnabled(guildId),
      adminApi.getAutoMentions(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
    ]).then(([e, m, r, c]) => {
      if (cancelled) return;
      setEnabled(e.enabled);
      setMentions(m);
      setRoles(r);
      setChannels(c);
    }).catch(error => { if (!cancelled) console.error(error); }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [guildId]);

  const toggleEnabled = async (value: boolean) => {
    if (!guildId) return;
    setToggling(true);
    setEnabled(value);
    try {
      await adminApi.setAutoMentionEnabled(guildId, value);
      toast(value ? "Auto-Mentions enabled." : "Auto-Mentions disabled.");
    } catch (e: unknown) {
      setEnabled(!value);
      toast(apiErrorMessage(e, "Failed to change module state."), "error");
    } finally {
      setToggling(false);
    }
  };

  const add = async () => {
    if (!guildId || !newChannel || !newRole) return;
    setAdding(true);
    try {
      await adminApi.addAutoMention(guildId, newChannel, newRole);
      setMentions(await adminApi.getAutoMentions(guildId));
      setNewChannel(null);
      setNewRole("");
      toast("Auto-mention added.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to add auto-mention."), "error");
    } finally {
      setAdding(false);
    }
  };

  const toggleMention = async (channelId: string) => {
    if (!guildId) return;
    try {
      const res = await adminApi.toggleAutoMention(guildId, channelId);
      setMentions(m => m.map(x => x.channel_id === channelId ? { ...x, enabled: res.enabled } : x));
      toast(res.enabled ? "Auto-mention turned on." : "Auto-mention turned off.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to update auto-mention."), "error");
    }
  };

  const remove = async (channelId: string) => {
    if (!guildId) return;
    try {
      await adminApi.removeAutoMention(guildId, channelId);
      setMentions(m => m.filter(x => x.channel_id !== channelId));
      toast("Auto-mention removed.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to remove auto-mention."), "error");
    }
  };

  const channelName = (id: string) => channels.find(c => c.id === id)?.name ?? id;
  const roleName = (id: string) => roles.find(r => r.id === id)?.name ?? id;
  const roleColor = (id: string) => { const c = roles.find(r => r.id === id)?.color; return c === "#000000" ? "#6b7280" : c ?? "#6b7280"; };
  const usedChannelIds = mentions.map(m => m.channel_id);
  const availableChannels = channels.filter(c => !usedChannelIds.includes(c.id));

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      <div className="flex items-center justify-between border-b border-zinc-800 px-4 sm:px-6 py-4">
        <div className="flex items-center gap-2 text-sm">
          <Link to="/modules" className="text-rose-400 hover:text-rose-300 font-semibold transition-colors flex items-center gap-1">
            <ArrowLeft className="w-3.5 h-3.5" /> Modules
          </Link>
          <span className="text-zinc-600">/</span>
          <span className="text-zinc-200 font-semibold">Auto-Mentions</span>
        </div>
        {guildId && (
          <div className="flex items-center gap-2">
            <span className="text-xs text-zinc-500">{enabled ? "Enabled" : "Disabled"}</span>
            <Toggle enabled={enabled} onChange={toggleEnabled} disabled={toggling} />
          </div>
        )}
      </div>

      {!guildId ? (
        <div className="flex items-center gap-2 p-6 text-zinc-500 text-sm">
          <AlertCircle className="w-4 h-4" /> No server selected.
        </div>
      ) : loading ? (
        <div className="flex items-center gap-2 p-6 text-zinc-500 text-sm">
          <Loader2 className="w-4 h-4 animate-spin" /> Loading…
        </div>
      ) : (
        <div className="p-6 max-w-2xl">
          <div className="rounded-lg border border-zinc-800 bg-zinc-900">
            <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
              <Bell className="w-4 h-4 text-indigo-400" />
              <div>
                <h2 className="text-sm font-bold text-zinc-100">Channel → Role mentions</h2>
                <p className="text-xs text-zinc-500 mt-0.5">Bot mentions the configured role whenever a message is posted in that channel.</p>
              </div>
            </div>
            <div className="px-4 py-4 space-y-3">
              {mentions.length === 0 ? (
                <p className="text-xs text-zinc-600">No auto-mentions configured.</p>
              ) : (
                <div className="space-y-1">
                  {mentions.map(m => (
                    <div key={m.channel_id} className="flex items-center gap-3 px-3 py-2 bg-zinc-800/60 border border-zinc-700 rounded text-sm">
                      <span className="text-zinc-400 font-mono text-xs flex-shrink-0"># {channelName(m.channel_id)}</span>
                      <span className="text-zinc-600">→</span>
                      <span className="flex items-center gap-1.5 flex-shrink-0">
                        <span className="w-2 h-2 rounded-full" style={{ background: roleColor(m.role_id) }} />
                        <span className="text-zinc-300 text-xs">@{roleName(m.role_id)}</span>
                      </span>
                      <div className="ml-auto flex items-center gap-2">
                        <button onClick={() => toggleMention(m.channel_id)}
                          className={cn("text-xs font-semibold px-2 py-0.5 rounded transition-colors",
                            m.enabled ? "text-emerald-400 bg-emerald-500/10 hover:bg-emerald-500/20" : "text-zinc-500 bg-zinc-700/50 hover:bg-zinc-700"
                          )}>
                          {m.enabled ? "ON" : "OFF"}
                        </button>
                        <button onClick={() => remove(m.channel_id)} className="text-zinc-600 hover:text-red-400 transition-colors">
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto_auto] gap-2 items-end pt-1">
                <div>
                  <p className="text-[11px] text-zinc-600 mb-1 flex items-center gap-1"><Hash className="w-3 h-3" /> Channel</p>
                  <ChannelPicker channels={availableChannels} value={newChannel} onChange={setNewChannel} placeholder="Pick channel…" />
                </div>
                <div>
                  <p className="text-[11px] text-zinc-600 mb-1 flex items-center gap-1"><AtSign className="w-3 h-3" /> Role to mention</p>
                  <RoleSelect roles={roles} value={newRole} onChange={setNewRole} />
                </div>
                <button onClick={add} disabled={adding || !newChannel || !newRole}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider border border-zinc-600 bg-zinc-800 text-zinc-300 hover:border-indigo-500/60 hover:text-indigo-300 transition-all disabled:opacity-40">
                  {adding ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
                  Add
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
