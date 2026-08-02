import { useEffect, useRef, useState } from "react";
import { adminApi, apiErrorMessage, type AutoDeleteConfig } from "@/lib/api";
import {
  X, Loader2, CheckCircle2, AlertCircle, Search, ChevronDown, Hash, AtSign, Timer,
  ToggleLeft, ToggleRight,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useToast } from "@/components/ui/toast";
import { useSelectedGuildId, ModulePageHeader, ConfigSidebar, EmptyConfigSelection } from "@/components/modules/shared";

const DEFAULT_NOTIFY_MSG = "Your message in {channel} was automatically deleted.";

type Draft = Omit<AutoDeleteConfig, "id" | "guild_id">;

const DEFAULT_DRAFT: Draft = {
  channel_id: "",
  delay_seconds: 60,
  ignore_role_ids: [],
  ignore_user_ids: [],
  ignore_bots: true,
  ignore_pinned: true,
  notify_user: false,
  notify_via: "channel",
  notify_message: DEFAULT_NOTIFY_MSG,
  notify_delete_bot_msg: true,
  notify_delete_delay: 5,
};

// ─── Pickers ─────────────────────────────────────────────────────────────────

function ChannelSelect({ channels, value, onChange, usedChannelIds = [] }: {
  channels: { id: string; name: string }[];
  value: string;
  onChange: (id: string) => void;
  usedChannelIds?: string[];
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);
  const sel = channels.find(c => c.id === value);
  const filtered = channels.filter(c => c.name.toLowerCase().includes(search.toLowerCase()));
  return (
    <div ref={ref} className="relative">
      <button type="button" onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between gap-2 px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 hover:border-zinc-500 transition-all">
        <span className="flex items-center gap-1.5 min-w-0 truncate">
          {sel ? <><Hash className="w-3.5 h-3.5 text-zinc-500 flex-shrink-0" />{sel.name}</> : <span className="text-zinc-500">Select channel…</span>}
        </span>
        <ChevronDown className={cn("w-3.5 h-3.5 text-zinc-500 flex-shrink-0 transition-transform", open && "rotate-180")} />
      </button>
      {open && (
        <div className="absolute z-50 top-full mt-1 w-full rounded-lg overflow-hidden"
          style={{ background: "#3f3f46", border: "1px solid #52525b", boxShadow: "0 8px 32px rgba(0,0,0,0.6)" }}>
          <div className="flex items-center gap-2 px-3 py-2" style={{ background: "#52525b", borderBottom: "1px solid #71717a" }}>
            <Search className="w-3.5 h-3.5 text-zinc-300 flex-shrink-0" />
            <input autoFocus value={search} onChange={e => setSearch(e.target.value)} placeholder="Search…"
              className="bg-transparent text-sm text-white outline-none flex-1 placeholder:text-zinc-400" />
          </div>
          <div className="max-h-48 overflow-y-auto scrollbar-thin">
            {filtered.map(c => {
              const used = usedChannelIds.includes(c.id) && c.id !== value;
              return (
                <button key={c.id} type="button" disabled={used}
                  onClick={() => { onChange(c.id); setOpen(false); setSearch(""); }}
                  className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm transition-colors disabled:opacity-40 disabled:cursor-not-allowed hover:bg-zinc-600"
                  style={{ color: value === c.id ? "#c7d2fe" : "#fff", background: value === c.id ? "rgba(99,102,241,0.25)" : undefined }}>
                  <Hash className="w-3.5 h-3.5 text-zinc-500 flex-shrink-0" />{c.name}
                  {used && <span className="ml-auto text-[10px] text-zinc-500">configured</span>}
                  {value === c.id && <CheckCircle2 className="w-3.5 h-3.5 ml-auto text-indigo-400 flex-shrink-0" />}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function RoleMultiSelect({ roles, selected, onChange }: {
  roles: { id: string; name: string; color: string }[];
  selected: string[];
  onChange: (ids: string[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);
  const selItems = roles.filter(r => selected.includes(r.id));
  const filtered = roles.filter(r => !selected.includes(r.id) && r.name.toLowerCase().includes(search.toLowerCase()));
  const dot = (c: string) => c === "#000000" ? "#6b7280" : c;
  return (
    <div ref={ref} className="relative">
      <div onClick={() => setOpen(o => !o)}
        className="min-h-[38px] flex flex-wrap gap-1.5 items-center px-3 py-1.5 bg-zinc-800 border border-zinc-700 rounded cursor-pointer hover:border-zinc-500 transition-all">
        {selItems.length === 0 && <span className="text-sm text-zinc-500">None — all roles included</span>}
        {selItems.map(r => (
          <span key={r.id} className="flex items-center gap-1 bg-zinc-700 text-zinc-200 text-xs px-2 py-0.5 rounded">
            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: dot(r.color) }} />
            {r.name}
            <button type="button" onClick={e => { e.stopPropagation(); onChange(selected.filter(id => id !== r.id)); }}
              className="ml-0.5 text-zinc-400 hover:text-red-400 transition-colors"><X className="w-3 h-3" /></button>
          </span>
        ))}
      </div>
      {open && (
        <div className="absolute z-50 top-full mt-1 w-full rounded-lg overflow-hidden"
          style={{ background: "#3f3f46", border: "1px solid #52525b", boxShadow: "0 8px 32px rgba(0,0,0,0.6)" }}>
          <div className="flex items-center gap-2 px-3 py-2" style={{ background: "#52525b", borderBottom: "1px solid #71717a" }}>
            <Search className="w-3.5 h-3.5 text-zinc-300 flex-shrink-0" />
            <input autoFocus value={search} onChange={e => setSearch(e.target.value)} placeholder="Search…"
              className="bg-transparent text-sm text-white outline-none flex-1 placeholder:text-zinc-400" />
          </div>
          <div className="max-h-44 overflow-y-auto scrollbar-thin">
            {filtered.length === 0 && <p className="px-3 py-3 text-xs text-zinc-500">No roles</p>}
            {filtered.map(r => (
              <button key={r.id} type="button" onClick={() => { onChange([...selected, r.id]); setSearch(""); }}
                className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm text-white hover:bg-zinc-600 transition-colors">
                <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: dot(r.color) }} />
                {r.name}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function UserIdInput({ value, onChange }: { value: string[]; onChange: (ids: string[]) => void }) {
  const [input, setInput] = useState("");
  const commit = () => {
    const id = input.trim();
    if (id && /^\d{17,20}$/.test(id) && !value.includes(id)) onChange([...value, id]);
    setInput("");
  };
  return (
    <div>
      <div className="min-h-[38px] flex flex-wrap gap-1.5 items-center px-3 py-1.5 bg-zinc-800 border border-zinc-700 rounded">
        {value.map(id => (
          <span key={id} className="flex items-center gap-1 bg-zinc-700 text-zinc-200 text-xs px-2 py-0.5 rounded font-mono">
            {id}
            <button type="button" onClick={() => onChange(value.filter(v => v !== id))}
              className="ml-0.5 text-zinc-400 hover:text-red-400 transition-colors"><X className="w-3 h-3" /></button>
          </span>
        ))}
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === "Enter" || e.key === ",") { e.preventDefault(); commit(); } }}
          onBlur={commit}
          placeholder={value.length === 0 ? "Paste Discord user ID, press Enter…" : "Add another ID…"}
          className="bg-transparent text-sm text-zinc-200 outline-none flex-1 min-w-[180px] placeholder:text-zinc-500"
        />
      </div>
      <p className="text-[11px] text-zinc-600 mt-1">Enter 18-digit Discord user IDs. Press Enter to add.</p>
    </div>
  );
}

function Toggle({ enabled, onChange }: { enabled: boolean; onChange: (v: boolean) => void }) {
  return (
    <button type="button" onClick={() => onChange(!enabled)}>
      {enabled
        ? <ToggleRight className="w-9 h-9 text-indigo-400 hover:text-indigo-300 transition-colors" />
        : <ToggleLeft className="w-9 h-9 text-zinc-600 hover:text-zinc-400 transition-colors" />}
    </button>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export function AutoDeleteModule() {
  const guildId = useSelectedGuildId();
  const [configs, setConfigs] = useState<AutoDeleteConfig[]>([]);
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
    setConfigs([]);
    setSelectedId(null);
    setDraft({ ...DEFAULT_DRAFT });
    setRoles([]);
    setChannels([]);
    setDeleteId(null);
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getAutoDeleteConfigs(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
    ]).then(([cfgs, r, c]) => {
      if (cancelled) return;
      setConfigs(cfgs);
      setRoles(r);
      setChannels(c);
    }).catch(error => { if (!cancelled) console.error(error); }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [guildId]);

  const selectCfg = (cfg: AutoDeleteConfig) => {
    setSelectedId(cfg.id);
    setDraft({
      channel_id: cfg.channel_id,
      delay_seconds: cfg.delay_seconds,
      ignore_role_ids: cfg.ignore_role_ids,
      ignore_user_ids: cfg.ignore_user_ids,
      ignore_bots: cfg.ignore_bots,
      ignore_pinned: cfg.ignore_pinned,
      notify_user: cfg.notify_user,
      notify_via: cfg.notify_via,
      notify_message: cfg.notify_message,
      notify_delete_bot_msg: cfg.notify_delete_bot_msg,
      notify_delete_delay: cfg.notify_delete_delay,
    });
  };

  const newCfg = () => {
    setSelectedId("new");
    setDraft({ ...DEFAULT_DRAFT });
  };

  const upd = <K extends keyof Draft>(key: K, val: Draft[K]) => setDraft(d => ({ ...d, [key]: val }));

  const save = async () => {
    if (!guildId || !draft.channel_id) { toast("Select a channel first.", "error"); return; }
    setSaving(true);
    try {
      if (selectedId === "new") {
        const result = await adminApi.createAutoDeleteConfig(guildId, draft);
        setConfigs(prev => [...prev, result]);
        setSelectedId(result.id);
      } else {
        const result = await adminApi.updateAutoDeleteConfig(selectedId as number, guildId, draft);
        setConfigs(prev => prev.map(c => c.id === selectedId ? result : c));
      }
      toast("Config saved successfully.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to save config."), "error");
    } finally { setSaving(false); }
  };

  const deleteCfg = async (id: number) => {
    if (!guildId) return;
    try {
      await adminApi.deleteAutoDeleteConfig(id, guildId);
      setConfigs(prev => prev.filter(c => c.id !== id));
      if (selectedId === id) setSelectedId(null);
      setDeleteId(null);
      toast("Config deleted.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to delete config."), "error");
    }
  };

  const channelName = (id: string) => channels.find(c => c.id === id)?.name ?? id;
  const usedChannelIds = configs.map(c => c.channel_id);

  const formatDelay = (s: number) => {
    if (s === 0) return "instant";
    if (s < 60) return `${s}s`;
    if (s < 3600) return `${Math.round(s / 60)}m`;
    return `${Math.round(s / 3600)}h`;
  };

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      <ModulePageHeader moduleName="Auto Delete" guildId={guildId} loading={loading} />

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
            items={configs}
            getKey={cfg => cfg.id}
            selectedKey={selectedId}
            onSelect={selectCfg}
            onNew={newCfg}
            newLabel="New Channel"
            emptyLabel="No channels configured"
            renderTitle={cfg => (<><Hash className="w-3 h-3 flex-shrink-0" />{channelName(cfg.channel_id)}</>)}
            renderSubtitle={cfg => formatDelay(cfg.delay_seconds)}
            deleteKey={deleteId}
            onRequestDelete={setDeleteId}
            onDelete={cfg => deleteCfg(cfg.id)}
          />

          {/* ── Right editor ── */}
          {selectedId === null ? (
            <EmptyConfigSelection icon={<Timer className="w-10 h-10 mx-auto opacity-30" />} label="Select a channel config or create a new one" />
          ) : (
            <div className="flex-1 overflow-y-auto scrollbar-thin p-6 space-y-5 max-w-2xl">

              {/* Channel + Delay */}
              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <Hash className="w-4 h-4 text-indigo-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Channel Settings</h2>
                </div>
                <div className="px-4 py-4 space-y-4">
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Channel</p>
                    <ChannelSelect
                      channels={channels}
                      value={draft.channel_id}
                      onChange={v => upd("channel_id", v)}
                      usedChannelIds={usedChannelIds}
                    />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Delete after (seconds)</p>
                    <div className="flex items-center gap-2">
                      <input
                        type="number"
                        min={0}
                        value={draft.delay_seconds}
                        onChange={e => upd("delay_seconds", Math.max(0, parseInt(e.target.value) || 0))}
                        className="w-32 px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors"
                      />
                      <span className="text-xs text-zinc-500">
                        {draft.delay_seconds === 0 ? "instant delete on send" : `≈ ${formatDelay(draft.delay_seconds)}`}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Ignore Rules */}
              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <AtSign className="w-4 h-4 text-amber-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Ignore Rules</h2>
                </div>
                <div className="px-4 py-4 space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-zinc-200">Ignore bots</p>
                      <p className="text-xs text-zinc-500 mt-0.5">Bot messages are never deleted</p>
                    </div>
                    <Toggle enabled={draft.ignore_bots} onChange={v => upd("ignore_bots", v)} />
                  </div>
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-zinc-200">Ignore pinned</p>
                      <p className="text-xs text-zinc-500 mt-0.5">Skip deletion if message was pinned</p>
                    </div>
                    <Toggle enabled={draft.ignore_pinned} onChange={v => upd("ignore_pinned", v)} />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Ignore roles</p>
                    <RoleMultiSelect roles={roles} selected={draft.ignore_role_ids} onChange={v => upd("ignore_role_ids", v)} />
                    <p className="text-[11px] text-zinc-600 mt-1">Messages from users with these roles are not deleted</p>
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Ignore users</p>
                    <UserIdInput value={draft.ignore_user_ids} onChange={v => upd("ignore_user_ids", v)} />
                  </div>
                </div>
              </div>

              {/* Notify */}
              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Notify User</h2>
                </div>
                <div className="px-4 py-4 space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-zinc-200">Send notification</p>
                      <p className="text-xs text-zinc-500 mt-0.5">Tell the user their message was deleted</p>
                    </div>
                    <Toggle enabled={draft.notify_user} onChange={v => upd("notify_user", v)} />
                  </div>
                  {draft.notify_user && (
                    <>
                      <div>
                        <p className="text-xs font-semibold text-zinc-400 mb-1.5">Notify via</p>
                        <div className="grid grid-cols-2 rounded border border-zinc-700 overflow-hidden">
                          {(["channel", "dm"] as const).map(via => (
                            <button key={via} type="button" onClick={() => upd("notify_via", via)}
                              className={cn("py-2 text-xs font-bold uppercase tracking-wider transition-colors",
                                draft.notify_via === via ? "bg-indigo-600 text-white" : "bg-zinc-800 text-zinc-500 hover:text-zinc-300")}>
                              {via === "channel" ? "In Channel" : "DM"}
                            </button>
                          ))}
                        </div>
                        {draft.notify_via === "dm" && (
                          <p className="text-[11px] text-zinc-600 mt-1">Silently skipped if user has DMs closed</p>
                        )}
                      </div>
                      {draft.notify_via === "channel" && (
                        <div className="space-y-3 pl-3 border-l border-zinc-700">
                          <div className="flex items-center justify-between">
                            <div>
                              <p className="text-sm font-medium text-zinc-200">Delete bot message</p>
                              <p className="text-xs text-zinc-500 mt-0.5">Auto-remove the notification after a delay</p>
                            </div>
                            <Toggle enabled={draft.notify_delete_bot_msg} onChange={v => upd("notify_delete_bot_msg", v)} />
                          </div>
                          {draft.notify_delete_bot_msg && (
                            <div>
                              <p className="text-xs font-semibold text-zinc-400 mb-1.5">Delete after (seconds)</p>
                              <input
                                type="number"
                                min={3}
                                value={draft.notify_delete_delay}
                                onChange={e => upd("notify_delete_delay", Math.max(3, parseInt(e.target.value) || 3))}
                                className="w-24 px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors"
                              />
                              <p className="text-[11px] text-zinc-600 mt-1">Minimum 3 seconds</p>
                            </div>
                          )}
                        </div>
                      )}
                      <div>
                        <p className="text-xs font-semibold text-zinc-400 mb-1.5">Message</p>
                        <textarea
                          value={draft.notify_message}
                          onChange={e => upd("notify_message", e.target.value)}
                          rows={3}
                          className="w-full resize-none px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors scrollbar-thin"
                        />
                        <p className="text-[11px] text-zinc-600 mt-1">
                          Placeholders: <span className="font-mono">{"{channel}"}</span> <span className="font-mono">{"{server}"}</span> <span className="font-mono">{"{user}"}</span>
                        </p>
                      </div>
                    </>
                  )}
                </div>
              </div>

              {/* Actions */}
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
