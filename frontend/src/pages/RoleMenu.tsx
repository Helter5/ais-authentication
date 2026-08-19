import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import EmojiPicker, { EmojiStyle, Theme as EmojiTheme } from "emoji-picker-react";
import { adminApi, apiErrorMessage, type DiscordEmoji, type RoleMenuConfig, type RoleMenuOption } from "@/lib/api";
import {
  Plus, X, Loader2, CheckCircle2, AlertCircle, Hash, ListChecks, Send, RefreshCw,
  Smile,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useToast } from "@/components/ui/toast";
import { NumberStepper } from "@/components/ui/number-stepper";
import {
  useSelectedGuildId, Toggle, ChannelPicker, MultiPicker, computeDropPosition,
  ModulePageHeader, ConfigSidebar, EmptyConfigSelection,
} from "@/components/modules/shared";

type Draft = Omit<RoleMenuConfig, "id" | "guild_id" | "message_id">;

const DEFAULT_DRAFT: Draft = {
  channel_id: "",
  title: "",
  description: "",
  ui_type: "SELECT_MENU",
  message_type: "UNIQUE",
  require_verified: false,
  options: [],
  allowed_role_ids: [],
  blocked_role_ids: [],
  max_selectable: null,
};

const MESSAGE_TYPES: { value: RoleMenuConfig["message_type"]; label: string; description: string }[] = [
  { value: "NORMAL", label: "Normal", description: "Clicking hands out or removes roles the way you'd expect - click again to take it back off." },
  { value: "UNIQUE", label: "Unique", description: "Only one role from this menu at a time - picking a new one swaps out the old one." },
  { value: "VERIFY", label: "Verify", description: "Roles can only be picked up here, never removed - use this for one-way opt-ins." },
  { value: "DROP", label: "Drop", description: "Roles can only be removed here, never picked up - use this to let members opt out of a role granted elsewhere." },
  { value: "BINDING", label: "Binding", description: "Members get exactly one pick from this menu, ever - once chosen it can't be changed." },
];

function emptyOption(): RoleMenuOption {
  return { role_ids: [], label: "", emoji: null, description: null };
}

// ─── Emoji picker (Discord custom emoji + unicode, with search) ─────────────

const EMOJI_PICKER_WIDTH = 320;
const EMOJI_PICKER_HEIGHT = 430;

function EmojiPickerButton({ value, onChange, guildEmojis }: {
  value: string | null;
  onChange: (value: string | null) => void;
  guildEmojis: DiscordEmoji[];
}) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const dropRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const h = (e: MouseEvent) => {
      if (triggerRef.current?.contains(e.target as Node)) return;
      if (dropRef.current?.contains(e.target as Node)) return;
      setOpen(false);
    };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, [open]);

  const customEmojis = useMemo(
    () => guildEmojis.map(e => ({ names: [e.name], imgUrl: e.url, id: e.mention })),
    [guildEmojis]
  );
  const customPreview = value ? guildEmojis.find(e => e.mention.toLowerCase() === value.toLowerCase()) : undefined;

  const handleOpen = () => {
    if (triggerRef.current) setPos(computeDropPosition(triggerRef.current.getBoundingClientRect(), EMOJI_PICKER_WIDTH, EMOJI_PICKER_HEIGHT));
    setOpen(o => !o);
  };

  return (
    <>
      <button ref={triggerRef} type="button" onClick={handleOpen}
        className="w-14 h-[30px] flex-shrink-0 flex items-center justify-center bg-zinc-800 border border-zinc-700 rounded text-sm hover:border-zinc-500 transition-colors">
        {customPreview
          ? <img src={customPreview.url} alt={customPreview.name} className="w-4 h-4" />
          : value
            ? <span>{value}</span>
            : <Smile className="w-4 h-4 text-zinc-500" />}
      </button>
      {open && pos && createPortal(
        <div ref={dropRef} style={{ position: "fixed", top: pos.top, left: pos.left, zIndex: 9999 }}>
          <EmojiPicker
            theme={EmojiTheme.DARK}
            emojiStyle={EmojiStyle.NATIVE}
            customEmojis={customEmojis}
            onEmojiClick={data => { onChange(data.emoji); setOpen(false); }}
            previewConfig={{ showPreview: false }}
            width={EMOJI_PICKER_WIDTH}
            height={380}
          />
          {value && (
            <button type="button" onClick={() => { onChange(null); setOpen(false); }}
              className="mt-1.5 w-full rounded bg-zinc-800 border border-zinc-700 px-3 py-1.5 text-xs text-zinc-400 hover:text-zinc-200 hover:border-zinc-500 transition-colors">
              Clear emoji
            </button>
          )}
        </div>,
        document.body
      )}
    </>
  );
}

export function RoleMenuModule() {
  const guildId = useSelectedGuildId();
  const [enabled, setEnabled] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [configs, setConfigs] = useState<RoleMenuConfig[]>([]);
  const [selectedId, setSelectedId] = useState<number | "new" | null>(null);
  const [draft, setDraft] = useState<Draft>({ ...DEFAULT_DRAFT });
  const [roles, setRoles] = useState<{ id: string; name: string; color: string }[]>([]);
  const [channels, setChannels] = useState<{ id: string; name: string }[]>([]);
  const [guildEmojis, setGuildEmojis] = useState<DiscordEmoji[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [reposting, setReposting] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    let cancelled = false;
    setLoading(Boolean(guildId));
    setEnabled(false);
    setConfigs([]);
    setSelectedId(null);
    setDraft({ ...DEFAULT_DRAFT });
    setRoles([]);
    setChannels([]);
    setGuildEmojis([]);
    setDeleteId(null);
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getRoleMenuEnabled(guildId),
      adminApi.getRoleMenus(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
      adminApi.getDiscordEmojis(guildId),
    ]).then(([e, cfgs, r, c, emojis]) => {
      if (cancelled) return;
      setEnabled(e.enabled);
      setConfigs(cfgs);
      setRoles(r);
      setChannels(c);
      setGuildEmojis(emojis);
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
      await adminApi.setRoleMenuEnabled(guildId, value);
      toast(value ? "Role menus enabled." : "Role menus disabled.");
    } catch (e: unknown) {
      setEnabled(!value);
      toast(apiErrorMessage(e, "Failed to change module state."), "error");
    } finally {
      setToggling(false);
    }
  };

  const selectCfg = (cfg: RoleMenuConfig) => {
    setSelectedId(cfg.id);
    setDraft({
      channel_id: cfg.channel_id,
      title: cfg.title,
      description: cfg.description,
      ui_type: cfg.ui_type,
      message_type: cfg.message_type,
      require_verified: cfg.require_verified,
      options: cfg.options,
      allowed_role_ids: cfg.allowed_role_ids,
      blocked_role_ids: cfg.blocked_role_ids,
      max_selectable: cfg.max_selectable,
    });
  };

  const newCfg = () => {
    setSelectedId("new");
    setDraft({ ...DEFAULT_DRAFT });
  };

  const upd = <K extends keyof Draft>(key: K, val: Draft[K]) => setDraft(d => ({ ...d, [key]: val }));

  const updOption = (index: number, patch: Partial<RoleMenuOption>) => {
    setDraft(d => ({ ...d, options: d.options.map((o, i) => i === index ? { ...o, ...patch } : o) }));
  };

  const addOption = () => setDraft(d => ({ ...d, options: [...d.options, emptyOption()] }));
  const removeOption = (index: number) => setDraft(d => ({ ...d, options: d.options.filter((_, i) => i !== index) }));

  const save = async () => {
    if (!guildId || !draft.channel_id) { toast("Select a channel first.", "error"); return; }
    if (!draft.title.trim()) { toast("Give the role menu a title.", "error"); return; }
    if (draft.options.length === 0) { toast("Add at least one role.", "error"); return; }
    if (draft.options.some(o => o.role_ids.length === 0 || !o.label.trim())) { toast("Every option needs at least one role and a label.", "error"); return; }

    setSaving(true);
    try {
      if (selectedId === "new") {
        const result = await adminApi.createRoleMenu(guildId, draft);
        setConfigs(prev => [...prev, result]);
        setSelectedId(result.id);
      } else {
        const result = await adminApi.updateRoleMenu(selectedId as number, guildId, draft);
        setConfigs(prev => prev.map(c => c.id === selectedId ? result : c));
      }
      toast("Role menu saved and posted.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to save role menu."), "error");
    } finally {
      setSaving(false);
    }
  };

  const repost = async () => {
    if (!guildId || selectedId === "new" || selectedId === null) return;
    setReposting(true);
    try {
      const result = await adminApi.repostRoleMenu(selectedId, guildId);
      setConfigs(prev => prev.map(c => c.id === selectedId ? result : c));
      toast("Role menu reposted.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to repost role menu."), "error");
    } finally {
      setReposting(false);
    }
  };

  const deleteCfg = async (id: number) => {
    if (!guildId) return;
    try {
      await adminApi.deleteRoleMenu(id, guildId);
      setConfigs(prev => prev.filter(c => c.id !== id));
      if (selectedId === id) setSelectedId(null);
      setDeleteId(null);
      toast("Role menu deleted.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to delete role menu."), "error");
    }
  };

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      <ModulePageHeader moduleName="Role Menu" guildId={guildId} loading={loading} enabled={enabled} onToggleEnabled={toggleEnabled} toggling={toggling} />

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
            newLabel="New Menu"
            emptyLabel="No role menus configured"
            renderTitle={cfg => cfg.title || "Untitled menu"}
            renderSubtitle={cfg => (
              <>
                <Hash className="w-2.5 h-2.5" />{channels.find(c => c.id === cfg.channel_id)?.name ?? cfg.channel_id}
                <span className="text-zinc-600">·</span>{cfg.options.length} role{cfg.options.length === 1 ? "" : "s"}
              </>
            )}
            deleteKey={deleteId}
            onRequestDelete={setDeleteId}
            onDelete={cfg => deleteCfg(cfg.id)}
          />

          {/* ── Right editor ── */}
          {selectedId === null ? (
            <EmptyConfigSelection icon={<ListChecks className="w-10 h-10 mx-auto opacity-30" />} label="Select a role menu or create a new one" />
          ) : (
            <div className="flex-1 overflow-y-auto scrollbar-thin p-6 space-y-5 max-w-2xl">

              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <Hash className="w-4 h-4 text-indigo-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Message</h2>
                </div>
                <div className="px-4 py-4 space-y-4">
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Channel</p>
                    <ChannelPicker channels={channels} value={draft.channel_id} onChange={v => upd("channel_id", v ?? "")} />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Title</p>
                    <input value={draft.title} onChange={e => upd("title", e.target.value)}
                      placeholder="e.g. Choose your enrollment year"
                      className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Description</p>
                    <textarea value={draft.description} onChange={e => upd("description", e.target.value)} rows={3}
                      placeholder="Pick the role that matches your year below."
                      className="w-full resize-none px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors scrollbar-thin" />
                  </div>
                </div>
              </div>

              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <ListChecks className="w-4 h-4 text-amber-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Behavior</h2>
                </div>
                <div className="px-4 py-4 space-y-4">
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Display as</p>
                    <div className="grid grid-cols-2 rounded border border-zinc-700 overflow-hidden">
                      {(["SELECT_MENU", "BUTTONS"] as const).map(t => (
                        <button key={t} type="button" onClick={() => upd("ui_type", t)}
                          className={cn("py-2 text-xs font-bold uppercase tracking-wider transition-colors",
                            draft.ui_type === t ? "bg-indigo-600 text-white" : "bg-zinc-800 text-zinc-500 hover:text-zinc-300")}>
                          {t === "SELECT_MENU" ? "Dropdown" : "Buttons"}
                        </button>
                      ))}
                    </div>
                    <p className="text-[11px] text-zinc-600 mt-1">
                      {draft.ui_type === "SELECT_MENU" ? "Scales better for longer lists." : "Best for a small number of roles."}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Message type</p>
                    <div className="space-y-1.5">
                      {MESSAGE_TYPES.map(mt => (
                        <button key={mt.value} type="button" onClick={() => upd("message_type", mt.value)}
                          className={cn("w-full text-left px-3 py-2 rounded border transition-colors",
                            draft.message_type === mt.value
                              ? "border-indigo-500 bg-indigo-500/10"
                              : "border-zinc-700 bg-zinc-800 hover:border-zinc-500")}>
                          <p className={cn("text-xs font-bold uppercase tracking-wider",
                            draft.message_type === mt.value ? "text-indigo-300" : "text-zinc-300")}>{mt.label}</p>
                          <p className="text-[11px] text-zinc-500 mt-0.5">{mt.description}</p>
                        </button>
                      ))}
                    </div>
                  </div>
                  {(draft.message_type === "NORMAL" || draft.message_type === "VERIFY") && (
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium text-zinc-200">Limit selectable roles</p>
                        <p className="text-xs text-zinc-500 mt-0.5">
                          Cap how many of this menu's roles a member can hold at once, regardless of how many are configured
                        </p>
                      </div>
                      <div className="flex items-center gap-2 flex-shrink-0">
                        <Toggle enabled={draft.max_selectable !== null} onChange={v => upd("max_selectable", v ? 1 : null)} />
                        {draft.max_selectable !== null && (
                          <NumberStepper
                            value={draft.max_selectable}
                            onChange={v => upd("max_selectable", v)}
                            min={1}
                            max={Math.max(1, draft.options.length)}
                            className="h-9 w-32"
                            ariaLabel="Max selectable roles"
                          />
                        )}
                      </div>
                    </div>
                  )}
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-zinc-200">Require verified</p>
                      <p className="text-xs text-zinc-500 mt-0.5">Only members who hold the Verified role and are in the Users Directory can use this menu</p>
                    </div>
                    <Toggle enabled={draft.require_verified} onChange={v => upd("require_verified", v)} />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Only allow these roles</p>
                    <MultiPicker options={roles} selected={draft.allowed_role_ids} onChange={v => upd("allowed_role_ids", v)}
                      placeholder="None — everyone can use this menu" />
                    <p className="text-[11px] text-zinc-600 mt-1">Optional. If set, only members with at least one of these roles can use this menu.</p>
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Block these roles</p>
                    <MultiPicker options={roles} selected={draft.blocked_role_ids} onChange={v => upd("blocked_role_ids", v)}
                      placeholder="None — nobody is blocked" />
                    <p className="text-[11px] text-zinc-600 mt-1">Optional. Members with any of these roles can never use this menu, even if allowed above.</p>
                  </div>
                </div>
              </div>

              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <ListChecks className="w-4 h-4 text-emerald-400" />
                    <h2 className="text-sm font-bold text-zinc-100">Roles ({draft.options.length}/25)</h2>
                  </div>
                  <button type="button" onClick={addOption} disabled={draft.options.length >= 25}
                    className="flex items-center gap-1 text-xs font-semibold text-rose-400 hover:text-rose-300 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                    <Plus className="w-3.5 h-3.5" /> Add role
                  </button>
                </div>
                {draft.options.length === 0 ? (
                  <p className="px-4 py-6 text-xs text-zinc-600 text-center">No roles added yet.</p>
                ) : (
                  <div className="divide-y divide-zinc-800">
                    {draft.options.map((option, i) => (
                      <div key={i} className="px-4 py-3 space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="w-4 flex-shrink-0 text-right text-[11px] font-mono text-zinc-600">{i + 1}</span>
                          <div className="flex-1 min-w-0">
                            <MultiPicker options={roles} selected={option.role_ids} onChange={v => updOption(i, { role_ids: v })}
                              placeholder="Select role(s) this grants…" />
                          </div>
                          <button type="button" onClick={() => removeOption(i)}
                            className="flex-shrink-0 text-zinc-600 hover:text-red-400 transition-colors"><X className="w-4 h-4" /></button>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="w-4 flex-shrink-0" />
                          <EmojiPickerButton value={option.emoji} onChange={v => updOption(i, { emoji: v })} guildEmojis={guildEmojis} />
                          <input value={option.label} onChange={e => updOption(i, { label: e.target.value })}
                            placeholder={option.role_ids.length > 1 ? "e.g. 2. ROCNIK + API" : "Label shown to members"}
                            className="flex-1 min-w-0 px-2.5 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 outline-none focus:border-indigo-500 transition-colors" />
                        </div>
                        {draft.ui_type === "SELECT_MENU" && (
                          <div className="flex items-center gap-2">
                            <span className="w-4 flex-shrink-0" />
                            <input value={option.description ?? ""} onChange={e => updOption(i, { description: e.target.value || null })}
                              placeholder="Description shown in dropdown (optional)"
                              className="flex-1 min-w-0 px-2.5 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 outline-none focus:border-indigo-500 transition-colors" />
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <button type="button" onClick={save} disabled={saving}
                  className="flex items-center gap-2 px-4 py-2 rounded bg-indigo-600 hover:bg-indigo-500 text-sm font-bold text-white disabled:opacity-50 transition-colors">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
                  Save &amp; Post
                </button>
                {selectedId !== "new" && (
                  <button type="button" onClick={repost} disabled={reposting}
                    className="flex items-center gap-2 px-4 py-2 rounded border border-zinc-700 hover:border-zinc-500 text-sm font-semibold text-zinc-300 disabled:opacity-50 transition-colors">
                    {reposting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                    Repost
                  </button>
                )}
              </div>
              {selectedId !== "new" && (
                <p className="text-[11px] text-zinc-600 flex items-center gap-1.5">
                  <RefreshCw className="w-3 h-3" /> Repost sends a fresh message if the original was deleted on Discord.
                </p>
              )}

            </div>
          )}
        </div>
      )}
    </div>
  );
}
