import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import type { EmojiClickData, EmojiStyle, Theme } from "emoji-picker-react";
import { adminApi, type DiscordEmoji, type ReactionRoleConfig } from "@/lib/api";
import {
  ArrowLeft, Plus, X, Loader2, CheckCircle2, AlertCircle, Search,
  Trash2, ChevronDown, Send, Smile, Hash, AtSign, Settings2, ToggleLeft, ToggleRight,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useSelectedGuildId } from "@/components/modules/shared";

const EmojiPicker = lazy(() => import("emoji-picker-react"));

// ─── types ────────────────────────────────────────────────────────────────────

type EmbedData = NonNullable<ReactionRoleConfig["embed_data"]>;
type EmbedField = { name: string; value: string; inline: boolean };
type RRConfig = Omit<ReactionRoleConfig, "id" | "guild_id"> & { id?: number };

const DEFAULT_EMBED: EmbedData = {
  color: "#5865F2", title: "", title_url: "", description: "",
  author_name: "", author_icon_url: "", image_url: "", thumbnail_url: "",
  footer_text: "", footer_icon_url: "", fields: [],
};

const DEFAULT_CONFIG: RRConfig = {
  name: "New Config",
  channel_id: null,
  message_id: null,
  message_type: "plain",
  message_content: "React below to get a role!",
  embed_data: { ...DEFAULT_EMBED },
  existing_message_ref: "",
  role_type: "normal",
  allowed_role_ids: [],
  ignored_role_ids: [],
  allow_multiple: true,
  reactions: [],
};

// ─── helpers ──────────────────────────────────────────────────────────────────

function customEmojiId(value: string) {
  return value.match(/^<a?:[^:]+:(\d+)>$/)?.[1] ?? null;
}

function EmojiPreview({ value, serverEmojis, className }: {
  value: string;
  serverEmojis: DiscordEmoji[];
  className?: string;
}) {
  const id = customEmojiId(value);
  const serverEmoji = id ? serverEmojis.find(emoji => emoji.id === id) : null;

  return serverEmoji
    ? <img src={serverEmoji.url} alt={`:${serverEmoji.name}:`} title={`:${serverEmoji.name}:`} className={cn("object-contain", className)} />
    : <span className={cn("leading-none", className)}>{value || <Smile className="w-4 h-4" />}</span>;
}

function ReactionEmojiPicker({ value, serverEmojis, onChange }: {
  value: string;
  serverEmojis: DiscordEmoji[];
  onChange: (emoji: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const customEmojis = serverEmojis.map(emoji => ({
    id: emoji.id,
    names: [emoji.name, `:${emoji.name}:`, "server emoji"],
    imgUrl: emoji.url,
  }));

  const selectEmoji = (emoji: EmojiClickData) => {
    if (emoji.isCustom) {
      const serverEmoji = serverEmojis.find(item => item.id === emoji.unified);
      if (serverEmoji) onChange(serverEmoji.mention);
    } else {
      onChange(emoji.emoji);
    }
    setOpen(false);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button type="button"
          className="w-36 h-10 flex items-center gap-2 px-3 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 hover:border-indigo-500/60 focus:border-indigo-500 outline-none transition-colors">
          <EmojiPreview value={value} serverEmojis={serverEmojis} className="w-5 h-5 text-xl flex-shrink-0" />
          <span className={cn("truncate", value ? "text-zinc-300" : "text-zinc-500")}>
            {value ? (serverEmojis.find(emoji => emoji.id === customEmojiId(value))?.name ?? "Change emoji") : "Pick emoji"}
          </span>
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" sideOffset={6}
        className="w-auto border-zinc-700 bg-zinc-900 p-0 shadow-2xl overflow-hidden">
        <Suspense fallback={
          <div className="w-[360px] h-[420px] flex items-center justify-center text-zinc-500">
            <Loader2 className="w-5 h-5 animate-spin" />
          </div>
        }>
          <EmojiPicker
            className="reaction-emoji-picker"
            theme={"dark" as Theme}
            emojiStyle={"native" as EmojiStyle}
            customEmojis={customEmojis}
            onEmojiClick={selectEmoji}
            searchPlaceholder="Search emoji..."
            width={360}
            height={420}
            lazyLoadEmojis
            previewConfig={{ showPreview: true, defaultCaption: "Pick an emoji" }}
          />
        </Suspense>
      </PopoverContent>
    </Popover>
  );
}

// ─── Pickers ─────────────────────────────────────────────────────────────────

function ChannelSelect({ channels, value, onChange }: {
  channels: { id: string; name: string }[];
  value: string | null;
  onChange: (id: string | null) => void;
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
      <button onClick={() => setOpen(o => !o)}
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
            {value && <button onClick={() => { onChange(null); setOpen(false); }} className="w-full text-left px-3 py-2 text-xs text-zinc-400 hover:bg-zinc-600 transition-colors">— Clear</button>}
            {filtered.map(c => (
              <button key={c.id} onClick={() => { onChange(c.id); setOpen(false); setSearch(""); }}
                className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm hover:bg-zinc-600 transition-colors"
                style={{ color: value === c.id ? "#c7d2fe" : "#fff", background: value === c.id ? "rgba(99,102,241,0.25)" : undefined }}>
                <Hash className="w-3.5 h-3.5 text-zinc-500 flex-shrink-0" />{c.name}
                {value === c.id && <CheckCircle2 className="w-3.5 h-3.5 ml-auto text-indigo-400 flex-shrink-0" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function RoleSelect({ roles, value, onChange, placeholder = "Select role…" }: {
  roles: { id: string; name: string; color: string }[];
  value: string | null;
  onChange: (id: string | null) => void;
  placeholder?: string;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);
  const sel = roles.find(r => r.id === value);
  const filtered = roles.filter(r => r.name.toLowerCase().includes(search.toLowerCase()));
  const dot = (color: string) => color === "#000000" ? "#6b7280" : color;
  return (
    <div ref={ref} className="relative">
      <button onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between gap-2 px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 hover:border-zinc-500 transition-all">
        <span className="flex items-center gap-2 min-w-0 truncate">
          {sel ? <><span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: dot(sel.color) }} /><AtSign className="w-3.5 h-3.5 text-zinc-500 flex-shrink-0" />{sel.name}</> : <span className="text-zinc-500">{placeholder}</span>}
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
            {value && <button onClick={() => { onChange(null); setOpen(false); }} className="w-full text-left px-3 py-2 text-xs text-zinc-400 hover:bg-zinc-600 transition-colors">— Clear</button>}
            {filtered.map(r => (
              <button key={r.id} onClick={() => { onChange(r.id); setOpen(false); setSearch(""); }}
                className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm hover:bg-zinc-600 transition-colors"
                style={{ color: value === r.id ? "#c7d2fe" : "#fff", background: value === r.id ? "rgba(99,102,241,0.25)" : undefined }}>
                <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: dot(r.color) }} />
                {r.name}
                {value === r.id && <CheckCircle2 className="w-3.5 h-3.5 ml-auto text-indigo-400 flex-shrink-0" />}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function RoleMultiSelect({ roles, selected, onChange, placeholder }: {
  roles: { id: string; name: string; color: string }[];
  selected: string[];
  onChange: (ids: string[]) => void;
  placeholder: string;
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
        {selItems.length === 0 && <span className="text-sm text-zinc-500">{placeholder}</span>}
        {selItems.map(r => (
          <span key={r.id} className="flex items-center gap-1 bg-zinc-700 text-zinc-200 text-xs px-2 py-0.5 rounded">
            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: dot(r.color) }} />
            {r.name}
            <button onClick={e => { e.stopPropagation(); onChange(selected.filter(id => id !== r.id)); }}
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
              <button key={r.id} onClick={() => { onChange([...selected, r.id]); setSearch(""); }}
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

// ─── Embed builder ────────────────────────────────────────────────────────────

function CollapsibleRow({ title, open, onToggle, children }: {
  title: string; open: boolean; onToggle: () => void; children: React.ReactNode;
}) {
  return (
    <div className="rounded border border-zinc-800 overflow-hidden">
      <button onClick={onToggle}
        className="w-full flex items-center justify-between px-3 py-2 text-xs font-semibold text-zinc-500 hover:text-zinc-300 hover:bg-zinc-800/50 transition-colors">
        {title}
        <ChevronDown className={cn("w-3.5 h-3.5 transition-transform", open && "rotate-180")} />
      </button>
      {open && <div className="px-3 pb-3 pt-1 space-y-2 bg-zinc-900/40">{children}</div>}
    </div>
  );
}

function inp(extra = "") {
  return cn("w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors placeholder:text-zinc-600", extra);
}

function EmbedBuilder({ data, onChange }: { data: EmbedData; onChange: (d: EmbedData) => void }) {
  const [open, setOpen] = useState<string | null>(null);
  const u = <K extends keyof EmbedData>(key: K, val: EmbedData[K]) => onChange({ ...data, [key]: val });
  const tog = (s: string) => setOpen(o => o === s ? null : s);
  const fields: EmbedField[] = data.fields ?? [];

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <label className="text-xs font-semibold text-zinc-400 w-12 flex-shrink-0">Color</label>
        <div className="flex items-center gap-2">
          <input type="color" value={data.color || "#5865F2"} onChange={e => u("color", e.target.value)}
            className="w-8 h-8 rounded cursor-pointer border-0 bg-transparent p-0" />
          <input value={data.color || "#5865F2"} onChange={e => u("color", e.target.value)}
            className="w-24 px-2 py-1 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 font-mono outline-none focus:border-indigo-500" />
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <div>
          <p className="text-xs font-semibold text-zinc-500 mb-1">Title</p>
          <input value={data.title || ""} onChange={e => u("title", e.target.value)} placeholder="Embed title" className={inp()} />
        </div>
        <div>
          <p className="text-xs font-semibold text-zinc-500 mb-1">Title URL</p>
          <input value={data.title_url || ""} onChange={e => u("title_url", e.target.value)} placeholder="https://…" className={inp()} />
        </div>
      </div>
      <div>
        <p className="text-xs font-semibold text-zinc-500 mb-1">Description</p>
        <textarea value={data.description || ""} onChange={e => u("description", e.target.value)}
          rows={3} placeholder="Embed description"
          className="w-full resize-none px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors placeholder:text-zinc-600 scrollbar-thin" />
      </div>
      <CollapsibleRow title="Author" open={open === "author"} onToggle={() => tog("author")}>
        <div className="grid grid-cols-2 gap-2">
          <input value={data.author_name || ""} onChange={e => u("author_name", e.target.value)} placeholder="Author name" className={inp()} />
          <input value={data.author_icon_url || ""} onChange={e => u("author_icon_url", e.target.value)} placeholder="Icon URL" className={inp()} />
        </div>
      </CollapsibleRow>
      <CollapsibleRow title="Image / Thumbnail" open={open === "image"} onToggle={() => tog("image")}>
        <div className="grid grid-cols-2 gap-2">
          <input value={data.image_url || ""} onChange={e => u("image_url", e.target.value)} placeholder="Image URL" className={inp()} />
          <input value={data.thumbnail_url || ""} onChange={e => u("thumbnail_url", e.target.value)} placeholder="Thumbnail URL" className={inp()} />
        </div>
      </CollapsibleRow>
      <CollapsibleRow title="Footer" open={open === "footer"} onToggle={() => tog("footer")}>
        <div className="grid grid-cols-2 gap-2">
          <input value={data.footer_text || ""} onChange={e => u("footer_text", e.target.value)} placeholder="Footer text" className={inp()} />
          <input value={data.footer_icon_url || ""} onChange={e => u("footer_icon_url", e.target.value)} placeholder="Icon URL" className={inp()} />
        </div>
      </CollapsibleRow>
      <CollapsibleRow title={`Fields (${fields.length})`} open={open === "fields"} onToggle={() => tog("fields")}>
        <div className="space-y-2">
          {fields.map((f, i) => (
            <div key={i} className="flex gap-2 items-center">
              <input value={f.name} onChange={e => { const ff = [...fields]; ff[i] = { ...ff[i], name: e.target.value }; u("fields", ff); }}
                placeholder="Name" className={cn(inp(), "flex-1")} />
              <input value={f.value} onChange={e => { const ff = [...fields]; ff[i] = { ...ff[i], value: e.target.value }; u("fields", ff); }}
                placeholder="Value" className={cn(inp(), "flex-1")} />
              <label className="flex items-center gap-1 text-xs text-zinc-500 whitespace-nowrap flex-shrink-0">
                <input type="checkbox" checked={f.inline} onChange={e => { const ff = [...fields]; ff[i] = { ...ff[i], inline: e.target.checked }; u("fields", ff); }}
                  className="accent-indigo-500" /> Inline
              </label>
              <button onClick={() => { const ff = [...fields]; ff.splice(i, 1); u("fields", ff); }}
                className="text-zinc-600 hover:text-red-400 transition-colors flex-shrink-0"><X className="w-4 h-4" /></button>
            </div>
          ))}
          <button onClick={() => u("fields", [...fields, { name: "", value: "", inline: false }])}
            className="flex items-center gap-1.5 text-xs font-bold text-zinc-500 hover:text-zinc-300 transition-colors">
            <Plus className="w-3.5 h-3.5" /> Add Field
          </button>
        </div>
      </CollapsibleRow>
    </div>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export function ReactionRolesModule() {
  const guildId = useSelectedGuildId();
  const [configs, setConfigs] = useState<ReactionRoleConfig[]>([]);
  const [selectedId, setSelectedId] = useState<number | "new" | null>(null);
  const [draft, setDraft] = useState<RRConfig>({ ...DEFAULT_CONFIG });
  const [roles, setRoles] = useState<{ id: string; name: string; color: string }[]>([]);
  const [channels, setChannels] = useState<{ id: string; name: string }[]>([]);
  const [serverEmojis, setServerEmojis] = useState<DiscordEmoji[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [posting, setPosting] = useState(false);
  const [postStatus, setPostStatus] = useState<{ ok: boolean; msg: string } | null>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [newEmoji, setNewEmoji] = useState("");
  const [newRoleId, setNewRoleId] = useState<string | null>(null);
  const [optionsOpen, setOptionsOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(Boolean(guildId));
    setConfigs([]);
    setSelectedId(null);
    setDraft({ ...DEFAULT_CONFIG, embed_data: { ...DEFAULT_EMBED } });
    setRoles([]);
    setChannels([]);
    setServerEmojis([]);
    setPostStatus(null);
    setDeleteId(null);
    setNewEmoji("");
    setNewRoleId(null);
    setOptionsOpen(false);
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getReactionRoles(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
      adminApi.getDiscordEmojis(guildId),
    ]).then(([cfgs, r, c, emojis]) => {
      if (cancelled) return;
      setConfigs(cfgs);
      setRoles(r);
      setChannels(c);
      setServerEmojis(emojis);
    })
      .catch(error => { if (!cancelled) console.error(error); })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [guildId]);

  const selectCfg = (cfg: ReactionRoleConfig) => {
    setSelectedId(cfg.id);
    setDraft({ ...cfg, embed_data: { ...DEFAULT_EMBED, ...(cfg.embed_data || {}) } });
    setPostStatus(null);
  };

  const newCfg = () => {
    setSelectedId("new");
    setDraft({ ...DEFAULT_CONFIG, embed_data: { ...DEFAULT_EMBED } });
    setPostStatus(null);
  };

  const upd = <K extends keyof RRConfig>(key: K, val: RRConfig[K]) => setDraft(d => ({ ...d, [key]: val }));

  const save = async (): Promise<number | null> => {
    if (!guildId) return null;
    setSaving(true);
    try {
      if (selectedId === "new") {
        const result = await adminApi.createReactionRole(guildId, draft);
        setConfigs(prev => [...prev, result]);
        setSelectedId(result.id);
        setDraft(prev => ({ ...prev, id: result.id }));
        return result.id;
      } else {
        const result = await adminApi.updateReactionRole(selectedId as number, guildId, draft);
        setConfigs(prev => prev.map(c => c.id === selectedId ? result : c));
        return result.id;
      }
    } catch (e) {
      console.error(e);
      return null;
    } finally { setSaving(false); }
  };

  const postMessage = async () => {
    if (!guildId) return;
    setPosting(true);
    setPostStatus(null);
    try {
      const configId = await save();
      if (!configId) throw new Error("Save failed");
      const result = await adminApi.postReactionRoleMessage(configId, guildId);
      setPostStatus({ ok: true, msg: `Message ${result.action} in #${result.channelName}` });
      const cfgs = await adminApi.getReactionRoles(guildId);
      setConfigs(cfgs);
      const updated = cfgs.find(c => c.id === configId);
      if (updated) setDraft(d => ({ ...d, message_id: updated.message_id }));
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } } };
      setPostStatus({ ok: false, msg: err.response?.data?.error ?? (e instanceof Error ? e.message : "Failed") });
    } finally { setPosting(false); }
  };

  const deleteCfg = async (id: number) => {
    if (!guildId) return;
    await adminApi.deleteReactionRole(id, guildId);
    setConfigs(prev => prev.filter(c => c.id !== id));
    if (selectedId === id) { setSelectedId(null); }
    setDeleteId(null);
  };

  const addReaction = () => {
    if (!newEmoji.trim() || !newRoleId) return;
    upd("reactions", [...(draft.reactions ?? []), { emoji: newEmoji.trim(), roleId: newRoleId }]);
    setNewEmoji("");
    setNewRoleId(null);
  };

  const roleName = (id: string) => roles.find(r => r.id === id)?.name ?? id;
  const roleColor = (id: string) => { const c = roles.find(r => r.id === id)?.color; return !c || c === "#000000" ? "#6b7280" : c; };

  const canPost = selectedId !== null && (
    draft.message_type === "existing" ? !!draft.existing_message_ref.trim() :
      !!draft.channel_id && (draft.reactions ?? []).length > 0
  );

  const msgTypeBadge = (t: string) => ({
    plain: "text-zinc-400 bg-zinc-800 border-zinc-700",
    embed: "text-indigo-400 bg-indigo-500/10 border-indigo-500/30",
    existing: "text-amber-400 bg-amber-500/10 border-amber-500/30",
  }[t] ?? "text-zinc-400 bg-zinc-800 border-zinc-700");

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-zinc-800 px-4 sm:px-6 py-4">
        <div className="flex items-center gap-2 text-sm">
          <Link to="/modules" className="text-rose-400 hover:text-rose-300 font-semibold transition-colors flex items-center gap-1">
            <ArrowLeft className="w-3.5 h-3.5" /> Modules
          </Link>
          <span className="text-zinc-600">/</span>
          <span className="text-zinc-200 font-semibold">Reaction Roles</span>
        </div>
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
        <div className="flex flex-1 min-h-0">
          {/* ── Left sidebar ── */}
          <div className="w-64 flex-shrink-0 border-r border-zinc-800 flex flex-col">
            <div className="p-3 border-b border-zinc-800">
              <button onClick={newCfg}
                className={cn("w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded text-xs font-bold uppercase tracking-wider border transition-all",
                  selectedId === "new"
                    ? "border-rose-500/60 text-rose-400 bg-rose-500/10"
                    : "border-zinc-700 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200")}>
                <Plus className="w-3.5 h-3.5" /> New Config
              </button>
            </div>
            <div className="flex-1 overflow-y-auto scrollbar-thin p-2 space-y-1">
              {configs.length === 0 && (
                <p className="text-xs text-zinc-600 text-center py-4">No configs yet</p>
              )}
              {configs.map(cfg => (
                <div key={cfg.id}
                  className={cn("group flex items-center gap-2 px-3 py-2.5 rounded cursor-pointer transition-all",
                    selectedId === cfg.id ? "bg-zinc-700 text-zinc-100" : "text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200")}
                  onClick={() => selectCfg(cfg)}>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold truncate">{cfg.name}</p>
                    <div className="flex items-center gap-1 mt-0.5">
                      <span className={cn("text-[10px] px-1.5 py-0.5 rounded border font-bold uppercase tracking-wide", msgTypeBadge(cfg.message_type))}>
                        {cfg.message_type}
                      </span>
                      <span className="text-[10px] text-zinc-600">{cfg.reactions?.length ?? 0} rxn</span>
                    </div>
                  </div>
                  {deleteId === cfg.id ? (
                    <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
                      <button onClick={() => deleteCfg(cfg.id)} className="text-red-400 hover:text-red-300 text-[10px] font-bold">Yes</button>
                      <button onClick={() => setDeleteId(null)} className="text-zinc-500 hover:text-zinc-300 text-[10px] font-bold">No</button>
                    </div>
                  ) : (
                    <button onClick={e => { e.stopPropagation(); setDeleteId(cfg.id); }}
                      className="opacity-0 group-hover:opacity-100 transition-opacity text-zinc-600 hover:text-red-400">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* ── Right editor ── */}
          {selectedId === null ? (
            <div className="flex-1 flex items-center justify-center text-zinc-600">
              <div className="text-center space-y-2">
                <Smile className="w-10 h-10 mx-auto opacity-30" />
                <p className="text-sm">Select a config or create a new one</p>
              </div>
            </div>
          ) : (
            <div className="flex-1 overflow-y-auto scrollbar-thin p-6 space-y-5 max-w-2xl">

              {/* Message Config */}
              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <Settings2 className="w-4 h-4 text-indigo-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Message Config</h2>
                </div>
                <div className="px-4 py-4 space-y-4">
                  {/* Name */}
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Name</p>
                    <input value={draft.name} onChange={e => upd("name", e.target.value)}
                      placeholder="Give it a unique name"
                      className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors" />
                  </div>

                  {/* Message Type */}
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Message Type</p>
                    <div className="grid grid-cols-3 rounded border border-zinc-700 overflow-hidden">
                      {(["plain", "embed", "existing"] as const).map(t => (
                        <button key={t} onClick={() => upd("message_type", t)}
                          className={cn("py-2 text-xs font-bold uppercase tracking-wider transition-colors",
                            draft.message_type === t ? "bg-indigo-600 text-white" : "bg-zinc-800 text-zinc-500 hover:text-zinc-300")}>
                          {t === "plain" ? "Plain" : t === "embed" ? "Embed" : "Existing"}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Channel (plain + embed only) */}
                  {draft.message_type !== "existing" && (
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-1.5">Channel to post in</p>
                      <ChannelSelect channels={channels} value={draft.channel_id} onChange={v => upd("channel_id", v)} />
                    </div>
                  )}

                  {/* Content area */}
                  {draft.message_type === "plain" && (
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-1.5">Message Content</p>
                      <textarea value={draft.message_content} onChange={e => upd("message_content", e.target.value)}
                        rows={4} placeholder="Message text…"
                        className="w-full resize-none px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors scrollbar-thin" />
                    </div>
                  )}

                  {draft.message_type === "embed" && (
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-2">Embed</p>
                      <div className="p-3 bg-zinc-800/50 border border-zinc-700 rounded-lg space-y-3">
                        <EmbedBuilder
                          data={draft.embed_data as EmbedData}
                          onChange={d => upd("embed_data", d)}
                        />
                      </div>
                    </div>
                  )}

                  {draft.message_type === "existing" && (
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-1.5">Message Link or ID</p>
                      <input value={draft.existing_message_ref} onChange={e => upd("existing_message_ref", e.target.value)}
                        placeholder="https://discord.com/channels/…/… or channelId/messageId"
                        className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors font-mono text-xs" />
                      <p className="text-[11px] text-zinc-600 mt-1">Paste the message link or <span className="font-mono">channelId/messageId</span></p>
                    </div>
                  )}

                  {draft.message_id && (
                    <p className="text-[11px] text-emerald-400 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Message posted · ID {draft.message_id}
                    </p>
                  )}
                </div>
              </div>

              {/* Reaction Settings */}
              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <Smile className="w-4 h-4 text-amber-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Reaction Settings</h2>
                </div>
                <div className="px-4 py-4 space-y-3">
                  {(draft.reactions ?? []).length === 0 ? (
                    <p className="text-xs text-zinc-600">No reactions configured.</p>
                  ) : (
                    <div className="space-y-1">
                      {(draft.reactions ?? []).map((r, i) => (
                        <div key={i} className="flex items-center gap-3 px-3 py-2 bg-zinc-800/60 border border-zinc-700 rounded text-sm">
                          <EmojiPreview value={r.emoji} serverEmojis={serverEmojis} className="w-6 h-6 text-lg flex-shrink-0" />
                          <span className="text-zinc-500">→</span>
                          <span className="flex items-center gap-1.5 flex-1 min-w-0">
                            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: roleColor(r.roleId) }} />
                            <span className="text-zinc-300 text-xs truncate">@{roleName(r.roleId)}</span>
                          </span>
                          <button onClick={() => upd("reactions", (draft.reactions ?? []).filter((_, j) => j !== i))}
                            className="text-zinc-600 hover:text-red-400 transition-colors flex-shrink-0">
                            <X className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}

                  <div className="flex gap-2 items-center pt-1">
                    <ReactionEmojiPicker value={newEmoji} serverEmojis={serverEmojis} onChange={setNewEmoji} />
                    <div className="flex-1">
                      <RoleSelect roles={roles} value={newRoleId} onChange={setNewRoleId} placeholder="Pick a role…" />
                    </div>
                    <button onClick={addReaction} disabled={!newEmoji.trim() || !newRoleId}
                      className="flex items-center gap-1.5 px-3 py-2 rounded text-xs font-bold uppercase tracking-wider border border-zinc-600 bg-zinc-800 text-zinc-300 hover:border-indigo-500/60 hover:text-indigo-300 transition-all disabled:opacity-40 flex-shrink-0">
                      <Plus className="w-3.5 h-3.5" /> Add
                    </button>
                  </div>
                </div>
              </div>

              {/* Options */}
              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <button onClick={() => setOptionsOpen(o => !o)}
                  className="w-full flex items-center justify-between px-4 py-3 text-sm font-bold text-zinc-300 hover:text-zinc-100 transition-colors">
                  <div className="flex items-center gap-2">
                    <Settings2 className="w-4 h-4 text-zinc-500" />
                    Options
                  </div>
                  <ChevronDown className={cn("w-4 h-4 text-zinc-600 transition-transform", optionsOpen && "rotate-180")} />
                </button>
                {optionsOpen && (
                  <div className="px-4 pb-4 space-y-4 border-t border-zinc-800 pt-4">
                    <div>
                      <p className="text-xs font-semibold text-zinc-400 mb-1.5">Type</p>
                      <select value={draft.role_type} onChange={e => upd("role_type", e.target.value as "normal" | "unique")}
                        className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500">
                        <option value="normal">Normal — members can collect multiple roles</option>
                        <option value="unique">Unique — switching reaction removes previous role</option>
                      </select>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <p className="text-xs font-semibold text-zinc-400 mb-1.5">Allowed Roles</p>
                        <RoleMultiSelect roles={roles} selected={draft.allowed_role_ids} onChange={v => upd("allowed_role_ids", v)} placeholder="Anyone can react" />
                        <p className="text-[11px] text-zinc-600 mt-1">Only these roles can use the reaction</p>
                      </div>
                      <div>
                        <p className="text-xs font-semibold text-zinc-400 mb-1.5">Ignored Roles</p>
                        <RoleMultiSelect roles={roles} selected={draft.ignored_role_ids} onChange={v => upd("ignored_role_ids", v)} placeholder="None ignored" />
                        <p className="text-[11px] text-zinc-600 mt-1">These roles are skipped</p>
                      </div>
                    </div>
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium text-zinc-200">Allow multiple roles</p>
                        <p className="text-xs text-zinc-500 mt-0.5">Members can hold more than one role from this config</p>
                      </div>
                      <button onClick={() => upd("allow_multiple", !draft.allow_multiple)}>
                        {draft.allow_multiple
                          ? <ToggleRight className="w-9 h-9 text-indigo-400 hover:text-indigo-300 transition-colors" />
                          : <ToggleLeft  className="w-9 h-9 text-zinc-600 hover:text-zinc-400 transition-colors" />
                        }
                      </button>
                    </div>
                  </div>
                )}
              </div>

              {/* Actions */}
              <div className="flex flex-wrap items-center gap-3">
                <button onClick={save} disabled={saving}
                  className="flex items-center gap-2 px-4 py-2 rounded bg-zinc-700 hover:bg-zinc-600 text-sm font-bold text-zinc-200 disabled:opacity-50 transition-colors">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
                  Save
                </button>

                <button onClick={postMessage} disabled={posting || !canPost}
                  className="flex items-center gap-2 px-4 py-2 rounded bg-indigo-600 hover:bg-indigo-500 text-sm font-bold text-white disabled:opacity-50 transition-colors">
                  {posting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                  {draft.message_type === "existing" ? "Attach Reactions" : draft.message_id ? "Edit Message" : "Post Message"}
                </button>

                {postStatus && (
                  <span className={cn("flex items-center gap-1.5 text-sm", postStatus.ok ? "text-emerald-400" : "text-red-400")}>
                    {postStatus.ok ? <CheckCircle2 className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                    {postStatus.msg}
                  </span>
                )}
              </div>

            </div>
          )}
        </div>
      )}
    </div>
  );
}
