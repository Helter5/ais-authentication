import { useEffect, useState } from "react";
import { adminApi, apiErrorMessage, type AutoMention } from "@/lib/api";
import { Loader2, CheckCircle2, AlertCircle, Hash, Bell } from "lucide-react";
import { useToast } from "@/components/ui/toast";
import {
  useSelectedGuildId, Toggle, ChannelPicker, RoleSelect,
  ModulePageHeader, ConfigSidebar, EmptyConfigSelection,
} from "@/components/modules/shared";

type Draft = Omit<AutoMention, "id">;

const DEFAULT_DRAFT: Draft = {
  channel_id: "",
  role_id: "",
  enabled: true,
};

export function AutoMentionsModule() {
  const guildId = useSelectedGuildId();
  const [moduleEnabled, setModuleEnabled] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [mentions, setMentions] = useState<AutoMention[]>([]);
  const [selectedId, setSelectedId] = useState<number | "new" | null>(null);
  const [draft, setDraft] = useState<Draft>({ ...DEFAULT_DRAFT });
  const [roles, setRoles] = useState<{ id: string; name: string; color: string }[]>([]);
  const [channels, setChannels] = useState<{ id: string; name: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    let cancelled = false;
    setLoading(Boolean(guildId));
    setModuleEnabled(false);
    setMentions([]);
    setSelectedId(null);
    setDraft({ ...DEFAULT_DRAFT });
    setRoles([]);
    setChannels([]);
    setDeleteId(null);
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getAutoMentionEnabled(guildId),
      adminApi.getAutoMentions(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
    ]).then(([e, m, r, c]) => {
      if (cancelled) return;
      setModuleEnabled(e.enabled);
      setMentions(m);
      setRoles(r);
      setChannels(c);
    }).catch(error => { if (!cancelled) console.error(error); }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [guildId]);

  const toggleModuleEnabled = async (value: boolean) => {
    if (!guildId) return;
    setToggling(true);
    setModuleEnabled(value);
    try {
      await adminApi.setAutoMentionEnabled(guildId, value);
      toast(value ? "Auto-Mentions enabled." : "Auto-Mentions disabled.");
    } catch (e: unknown) {
      setModuleEnabled(!value);
      toast(apiErrorMessage(e, "Failed to change module state."), "error");
    } finally {
      setToggling(false);
    }
  };

  const selectCfg = (mention: AutoMention) => {
    setSelectedId(mention.id);
    setDraft({ channel_id: mention.channel_id, role_id: mention.role_id, enabled: mention.enabled });
  };

  const newCfg = () => {
    setSelectedId("new");
    setDraft({ ...DEFAULT_DRAFT });
  };

  const upd = <K extends keyof Draft>(key: K, val: Draft[K]) => setDraft(d => ({ ...d, [key]: val }));

  const save = async () => {
    if (!guildId || !draft.channel_id) { toast("Select a channel first.", "error"); return; }
    if (!draft.role_id) { toast("Select a role to mention.", "error"); return; }
    setSaving(true);
    try {
      if (selectedId === "new") {
        const result = await adminApi.createAutoMention(guildId, draft);
        setMentions(prev => [...prev, result]);
        setSelectedId(result.id);
      } else {
        const result = await adminApi.updateAutoMention(selectedId as number, guildId, draft);
        setMentions(prev => prev.map(m => m.id === selectedId ? result : m));
      }
      toast("Auto-mention saved.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to save auto-mention."), "error");
    } finally {
      setSaving(false);
    }
  };

  const deleteCfg = async (id: number) => {
    if (!guildId) return;
    try {
      await adminApi.deleteAutoMention(id, guildId);
      setMentions(prev => prev.filter(m => m.id !== id));
      if (selectedId === id) setSelectedId(null);
      setDeleteId(null);
      toast("Auto-mention deleted.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to delete auto-mention."), "error");
    }
  };

  const channelName = (id: string) => channels.find(c => c.id === id)?.name ?? id;
  const roleName = (id: string) => roles.find(r => r.id === id)?.name ?? id;

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      <ModulePageHeader moduleName="Auto-Mentions" guildId={guildId} loading={loading}
        enabled={moduleEnabled} onToggleEnabled={toggleModuleEnabled} toggling={toggling} />

      {!guildId ? (
        <div className="flex items-center gap-2 p-6 text-zinc-500 text-sm">
          <AlertCircle className="w-4 h-4" /> No server selected.
        </div>
      ) : loading ? (
        <div className="flex items-center gap-2 p-6 text-zinc-500 text-sm">
          <Loader2 className="w-4 h-4 animate-spin" /> Loading…
        </div>
      ) : (
        <div className="flex flex-1 min-h-0">
          <ConfigSidebar
            items={mentions}
            getKey={m => m.id}
            selectedKey={selectedId}
            onSelect={selectCfg}
            onNew={newCfg}
            newLabel="New Channel"
            emptyLabel="No auto-mentions configured"
            renderTitle={m => (<><Hash className="w-3 h-3 flex-shrink-0" />{channelName(m.channel_id)}</>)}
            renderSubtitle={m => `@${roleName(m.role_id)} · ${m.enabled ? "on" : "off"}`}
            deleteKey={deleteId}
            onRequestDelete={setDeleteId}
            onDelete={m => deleteCfg(m.id)}
          />

          {selectedId === null ? (
            <EmptyConfigSelection icon={<Bell className="w-10 h-10 mx-auto opacity-30" />} label="Select a channel or create a new one" />
          ) : (
            <div className="flex-1 overflow-y-auto scrollbar-thin p-6 space-y-5 max-w-2xl">

              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <Bell className="w-4 h-4 text-indigo-400" />
                  <div>
                    <h2 className="text-sm font-bold text-zinc-100">Channel → Role mention</h2>
                    <p className="text-xs text-zinc-500 mt-0.5">Bot mentions the configured role whenever a message is posted in this channel.</p>
                  </div>
                </div>
                <div className="px-4 py-4 space-y-4">
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Channel</p>
                    <ChannelPicker channels={channels} value={draft.channel_id} onChange={v => upd("channel_id", v ?? "")} />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Role to mention</p>
                    <RoleSelect roles={roles} value={draft.role_id} onChange={v => upd("role_id", v)} />
                  </div>
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-zinc-200">Enabled</p>
                      <p className="text-xs text-zinc-500 mt-0.5">Turn this mention off without deleting it</p>
                    </div>
                    <Toggle enabled={draft.enabled} onChange={v => upd("enabled", v)} />
                  </div>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <button type="button" onClick={save} disabled={saving}
                  className="flex items-center gap-2 px-4 py-2 rounded bg-indigo-600 hover:bg-indigo-500 text-sm font-bold text-white disabled:opacity-50 transition-colors">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
                  Save
                </button>
              </div>

            </div>
          )}
        </div>
      )}
    </div>
  );
}
