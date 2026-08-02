import { useState, useEffect, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { ArrowLeft, CheckCircle2, Plus, Search, Trash2, X, ChevronDown as ChevDown } from "lucide-react";
import { cn } from "@/lib/utils";

// ─── Guild selection ──────────────────────────────────────────────────────────

const GUILD_KEY = "selected_guild_id";
const GUILD_CHANGE = "guild-changed";

export function useSelectedGuildId(): string {
  const [guildId, setGuildId] = useState(() => localStorage.getItem(GUILD_KEY) ?? "");
  useEffect(() => {
    const handler = () => setGuildId(localStorage.getItem(GUILD_KEY) ?? "");
    window.addEventListener(GUILD_CHANGE, handler);
    return () => window.removeEventListener(GUILD_CHANGE, handler);
  }, []);
  return guildId;
}

export function setSelectedGuildId(id: string) {
  localStorage.setItem(GUILD_KEY, id);
  window.dispatchEvent(new CustomEvent(GUILD_CHANGE));
}

// ─── Toggle ───────────────────────────────────────────────────────────────────

export function Toggle({ enabled, onChange, disabled }: { enabled: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button
      onClick={e => { e.stopPropagation(); onChange(!enabled); }}
      disabled={disabled}
      title={enabled ? "Enabled — click to disable" : "Disabled — click to enable"}
      className={cn("relative w-9 h-5 rounded-full transition-colors duration-200 flex-shrink-0 focus:outline-none",
        enabled ? "bg-emerald-500 shadow-[0_0_6px_rgba(52,211,153,0.5)]" : "bg-zinc-600",
        disabled && "opacity-40 cursor-not-allowed")}>
      <span className={cn("absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform duration-200",
        enabled && "translate-x-4")} />
    </button>
  );
}

// ─── Module page header ───────────────────────────────────────────────────────

/** Breadcrumb + optional enabled/disabled toggle, shared by every /modules/* page. */
export function ModulePageHeader({ moduleName, guildId, loading, enabled, onToggleEnabled, toggling }: {
  moduleName: string;
  guildId?: string;
  loading?: boolean;
  enabled?: boolean;
  onToggleEnabled?: (v: boolean) => void;
  toggling?: boolean;
}) {
  return (
    <div className="flex items-center justify-between border-b border-zinc-800 px-4 sm:px-6 py-4">
      <div className="flex items-center gap-2 text-sm">
        <Link to="/modules" className="text-rose-400 hover:text-rose-300 font-semibold transition-colors flex items-center gap-1">
          <ArrowLeft className="w-3.5 h-3.5" /> Modules
        </Link>
        <span className="text-zinc-600">/</span>
        <span className="text-zinc-200 font-semibold">{moduleName}</span>
      </div>
      {guildId && !loading && onToggleEnabled && (
        <div className="flex items-center gap-2">
          <span className="text-xs text-zinc-500">{enabled ? "Enabled" : "Disabled"}</span>
          <Toggle enabled={!!enabled} onChange={onToggleEnabled} disabled={toggling} />
        </div>
      )}
    </div>
  );
}

/** Right-panel placeholder shown before a config in a ConfigSidebar list is selected. */
export function EmptyConfigSelection({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <div className="flex-1 flex items-center justify-center text-zinc-600">
      <div className="text-center space-y-2">
        {icon}
        <p className="text-sm">{label}</p>
      </div>
    </div>
  );
}

/**
 * Left sidebar for list-based modules (Auto Delete, Role Menu, Auto-Mentions): a "+ New" button
 * plus the list of existing configs, each with a click-to-select row and a hover-to-reveal
 * delete button with an inline Yes/No confirm - shared so each module doesn't reimplement it.
 */
export function ConfigSidebar<T, K extends string | number = string | number>({
  items, getKey, selectedKey, onSelect, onNew, newLabel, emptyLabel, renderTitle, renderSubtitle,
  deleteKey, onRequestDelete, onDelete,
}: {
  items: T[];
  getKey: (item: T) => K;
  selectedKey: K | "new" | null;
  onSelect: (item: T) => void;
  onNew: () => void;
  newLabel: string;
  emptyLabel: string;
  renderTitle: (item: T) => ReactNode;
  renderSubtitle: (item: T) => ReactNode;
  deleteKey: K | null;
  onRequestDelete: (key: K | null) => void;
  onDelete: (item: T) => void;
}) {
  return (
    <div className="w-64 flex-shrink-0 border-r border-zinc-800 flex flex-col">
      <div className="p-3 border-b border-zinc-800">
        <button type="button" onClick={onNew}
          className={cn("w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded text-xs font-bold uppercase tracking-wider border transition-all",
            selectedKey === "new"
              ? "border-rose-500/60 text-rose-400 bg-rose-500/10"
              : "border-zinc-700 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200")}>
          <Plus className="w-3.5 h-3.5" /> {newLabel}
        </button>
      </div>
      <div className="flex-1 overflow-y-auto scrollbar-thin p-2 space-y-1">
        {items.length === 0 && (
          <p className="text-xs text-zinc-600 text-center py-4">{emptyLabel}</p>
        )}
        {items.map(item => {
          const key = getKey(item);
          return (
            <div key={key}
              className={cn("group flex items-center gap-2 px-3 py-2.5 rounded cursor-pointer transition-all",
                selectedKey === key ? "bg-zinc-700 text-zinc-100" : "text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200")}
              onClick={() => onSelect(item)}>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold truncate flex items-center gap-1">{renderTitle(item)}</p>
                <p className="text-[10px] text-zinc-500 mt-0.5 flex items-center gap-1">{renderSubtitle(item)}</p>
              </div>
              {deleteKey === key ? (
                <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
                  <button type="button" onClick={() => onDelete(item)} className="text-red-400 hover:text-red-300 text-[10px] font-bold">Yes</button>
                  <button type="button" onClick={() => onRequestDelete(null)} className="text-zinc-500 hover:text-zinc-300 text-[10px] font-bold">No</button>
                </div>
              ) : (
                <button type="button" onClick={e => { e.stopPropagation(); onRequestDelete(key); }}
                  className="opacity-0 group-hover:opacity-100 transition-opacity text-zinc-600 hover:text-red-400">
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Settings row ─────────────────────────────────────────────────────────────

export function CmdSettingsRow({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-zinc-200">{label}</p>
        {hint && <p className="text-xs text-zinc-500 mt-0.5">{hint}</p>}
      </div>
      <div className="flex-shrink-0 pt-0.5">{children}</div>
    </div>
  );
}

// ─── Portal picker primitives ─────────────────────────────────────────────────

export function usePortalPicker() {
  const [open, setOpen] = useState(false);
  const [dropRect, setDropRect] = useState<DOMRect | null>(null);
  const [search, setSearch] = useState("");
  const triggerRef = useRef<HTMLDivElement>(null);
  const dropRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function h(e: MouseEvent) {
      if (triggerRef.current?.contains(e.target as Node)) return;
      if (dropRef.current?.contains(e.target as Node)) return;
      setOpen(false);
    }
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  function handleOpen() {
    if (triggerRef.current) setDropRect(triggerRef.current.getBoundingClientRect());
    setOpen(o => !o);
  }

  function close() { setOpen(false); setSearch(""); }

  return { open, dropRect, search, setSearch, triggerRef, dropRef, handleOpen, close };
}

export function PortalPickerDropdown({ dropRef, dropRect, search, onSearch, maxHeight = "max-h-44", children }: {
  dropRef: React.RefObject<HTMLDivElement | null>;
  dropRect: DOMRect;
  search: string;
  onSearch: (v: string) => void;
  maxHeight?: string;
  children: React.ReactNode;
}) {
  return createPortal(
    <div ref={dropRef} style={{
      position: "fixed", top: dropRect.bottom + 4, left: dropRect.left, width: dropRect.width,
      zIndex: 9999, background: "#3f3f46", border: "1px solid #52525b",
      borderRadius: 8, boxShadow: "0 8px 32px rgba(0,0,0,0.7)", overflow: "hidden",
    }}>
      <div className="flex items-center gap-2 px-3 py-2" style={{ background: "#52525b", borderBottom: "1px solid #71717a" }}>
        <Search className="w-3.5 h-3.5 text-zinc-300 flex-shrink-0" />
        <input autoFocus value={search} onChange={e => onSearch(e.target.value)} placeholder="Search…"
          className="bg-transparent text-sm text-white outline-none flex-1 placeholder:text-zinc-400" />
      </div>
      <div className={cn("overflow-y-auto scrollbar-thin", maxHeight)}>
        {children}
      </div>
    </div>,
    document.body
  );
}

// ─── Multi-select picker ──────────────────────────────────────────────────────

export function MultiPicker({ options, selected, onChange, placeholder }: {
  options: { id: string; name: string }[];
  selected: string[];
  onChange: (ids: string[]) => void;
  placeholder: string;
}) {
  const { open, dropRect, search, setSearch, triggerRef, dropRef, handleOpen } = usePortalPicker();

  const filtered = options.filter(o => !selected.includes(o.id) && o.name.toLowerCase().includes(search.toLowerCase()));
  const selectedItems = options.filter(o => selected.includes(o.id));
  const add = (id: string) => { onChange([...selected, id]); setSearch(""); };
  const remove = (id: string) => onChange(selected.filter(s => s !== id));

  return (
    <div ref={triggerRef} className="relative">
      <div onClick={handleOpen}
        className="min-h-[40px] flex flex-wrap gap-1.5 items-center px-3 py-2 bg-zinc-800 border border-zinc-700 rounded cursor-pointer hover:border-zinc-500 transition-all">
        {selectedItems.length === 0 && <span className="text-sm text-zinc-500">{placeholder}</span>}
        {selectedItems.map(item => (
          <span key={item.id} className="flex items-center gap-1 bg-zinc-700 text-zinc-200 text-xs px-2 py-0.5 rounded">
            {item.name}
            <button onClick={e => { e.stopPropagation(); remove(item.id); }} className="text-zinc-400 hover:text-red-400 transition-colors">
              <X className="w-2.5 h-2.5" />
            </button>
          </span>
        ))}
        <ChevDown className={cn("w-3.5 h-3.5 text-zinc-500 ml-auto flex-shrink-0 transition-transform", open && "rotate-180")} />
      </div>
      {open && dropRect && (
        <PortalPickerDropdown dropRef={dropRef} dropRect={dropRect} search={search} onSearch={setSearch}>
          {filtered.length === 0
            ? <p className="text-xs text-zinc-400 px-3 py-3">No options</p>
            : filtered.map(o => (
              <button key={o.id} onClick={() => add(o.id)}
                className="w-full text-left px-3 py-2 text-sm text-white hover:bg-zinc-600 transition-colors truncate">
                {o.name}
              </button>
            ))}
        </PortalPickerDropdown>
      )}
    </div>
  );
}

// ─── Channel picker (single-select) ──────────────────────────────────────────

export function ChannelPicker({ channels, value, onChange, prefix = "#", placeholder = "Select a channel…" }: {
  channels: { id: string; name: string }[];
  value: string | null;
  onChange: (id: string | null) => void;
  prefix?: string;
  placeholder?: string;
}) {
  const { open, dropRect, search, setSearch, triggerRef, dropRef, handleOpen, close } = usePortalPicker();
  const selected = channels.find(c => c.id === value);
  const filtered = channels.filter(c => c.name.toLowerCase().includes(search.toLowerCase()));

  return (
    <div ref={triggerRef} className="relative">
      <button onClick={handleOpen}
        className="w-full flex items-center justify-between gap-2 px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 hover:border-zinc-500 transition-all">
        <span className="truncate text-left">{selected ? `${prefix}${selected.name}` : <span className="text-zinc-500">{placeholder}</span>}</span>
        <ChevDown className={cn("w-3.5 h-3.5 text-zinc-500 flex-shrink-0 transition-transform", open && "rotate-180")} />
      </button>
      {open && dropRect && (
        <PortalPickerDropdown dropRef={dropRef} dropRect={dropRect} search={search} onSearch={setSearch} maxHeight="max-h-48">
          {value && (
            <button onClick={() => { onChange(null); close(); }}
              className="w-full text-left px-3 py-2 text-xs text-zinc-400 hover:bg-zinc-600 transition-colors">
              — Clear selection
            </button>
          )}
          {filtered.map(c => (
            <button key={c.id} onClick={() => { onChange(c.id); close(); }}
              className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm hover:bg-zinc-600 transition-colors"
              style={{ color: value === c.id ? "#c7d2fe" : "#ffffff", background: value === c.id ? "rgba(99,102,241,0.25)" : undefined }}>
              {prefix}{c.name}
              {value === c.id && <CheckCircle2 className="w-3.5 h-3.5 ml-auto text-indigo-400 flex-shrink-0" />}
            </button>
          ))}
        </PortalPickerDropdown>
      )}
    </div>
  );
}

// ─── Viewport-aware floating panel positioning ───────────────────────────────

export function roleDotColor(color: string) {
  return color === "#000000" ? "#6b7280" : color;
}

/**
 * Positions a floating panel next to its trigger, clamped to the viewport - without this, a
 * trigger near the bottom/right edge of the screen (e.g. the last row in a long list) would
 * render the panel partly or fully off-screen instead of flipping to fit.
 */
export function computeDropPosition(rect: DOMRect, dropWidth: number, dropHeight: number) {
  const margin = 8;
  const spaceBelow = window.innerHeight - rect.bottom;
  const spaceAbove = rect.top;
  const openUp = spaceBelow < dropHeight + margin && spaceAbove > spaceBelow;
  const top = openUp ? Math.max(margin, rect.top - dropHeight - 4) : rect.bottom + 4;
  const maxLeft = Math.max(margin, window.innerWidth - dropWidth - margin);
  const left = Math.min(Math.max(margin, rect.left), maxLeft);
  return { top, left };
}

// ─── Role picker (single-select, Discord-colored) ────────────────────────────

const ROLE_SELECT_WIDTH = 208;
const ROLE_SELECT_HEIGHT = 260;

export function RoleSelect({ roles, value, onChange }: {
  roles: { id: string; name: string; color: string }[];
  value: string;
  onChange: (id: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
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

  const selected = roles.find(r => r.id === value);
  const filtered = roles.filter(r => r.name.toLowerCase().includes(search.toLowerCase()));

  const handleOpen = () => {
    if (triggerRef.current) setPos(computeDropPosition(triggerRef.current.getBoundingClientRect(), ROLE_SELECT_WIDTH, ROLE_SELECT_HEIGHT));
    setOpen(o => !o);
  };

  return (
    <>
      <button ref={triggerRef} type="button" onClick={handleOpen}
        className="w-40 flex-shrink-0 flex items-center gap-1.5 px-2.5 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 hover:border-zinc-500 transition-all">
        {selected ? (
          <>
            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: roleDotColor(selected.color) }} />
            <span className="truncate">{selected.name}</span>
          </>
        ) : <span className="text-zinc-500">Select role…</span>}
        <ChevDown className={cn("w-3 h-3 text-zinc-500 ml-auto flex-shrink-0 transition-transform", open && "rotate-180")} />
      </button>
      {open && pos && createPortal(
        <div ref={dropRef} style={{
          position: "fixed", top: pos.top, left: pos.left, width: ROLE_SELECT_WIDTH, zIndex: 9999,
          background: "#3f3f46", border: "1px solid #52525b", borderRadius: 8,
          boxShadow: "0 8px 32px rgba(0,0,0,0.6)", overflow: "hidden",
        }}>
          <div className="flex items-center gap-2 px-3 py-2" style={{ background: "#52525b", borderBottom: "1px solid #71717a" }}>
            <Search className="w-3.5 h-3.5 text-zinc-300 flex-shrink-0" />
            <input autoFocus value={search} onChange={e => setSearch(e.target.value)} placeholder="Search…"
              className="bg-transparent text-sm text-white outline-none flex-1 placeholder:text-zinc-400" />
          </div>
          <div className="max-h-48 overflow-y-auto scrollbar-thin">
            {filtered.length === 0 && <p className="px-3 py-3 text-xs text-zinc-400">No roles</p>}
            {filtered.map(r => (
              <button key={r.id} type="button" onClick={() => { onChange(r.id); setOpen(false); setSearch(""); }}
                className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm hover:bg-zinc-600 transition-colors truncate"
                style={{ color: value === r.id ? "#c7d2fe" : "#fff", background: value === r.id ? "rgba(99,102,241,0.25)" : undefined }}>
                <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: roleDotColor(r.color) }} />
                {r.name}
              </button>
            ))}
          </div>
        </div>,
        document.body
      )}
    </>
  );
}
