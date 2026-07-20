import { useState, useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { AlertCircle, ArrowLeft, CheckCircle2, Search, X, ChevronDown as ChevDown } from "lucide-react";
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

// ─── Module page shell ────────────────────────────────────────────────────────

export function ModuleShell({ title, children, guildId, action }: {
  title: string;
  children: React.ReactNode;
  guildId: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-0 md:pl-64 min-h-screen">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-zinc-800 px-4 pb-4 pt-5 sm:px-6">
        <div className="flex items-center gap-2 text-sm">
          <Link to="/modules" className="text-rose-400 hover:text-rose-300 font-semibold transition-colors flex items-center gap-1">
            <ArrowLeft className="w-3.5 h-3.5" /> Modules
          </Link>
          <span className="text-zinc-600">/</span>
          <span className="text-zinc-200 font-semibold">{title}</span>
        </div>
        {action}
      </div>
      <div className="px-4 sm:px-6 py-6 max-w-3xl w-full">
        {!guildId ? (
          <div className="flex items-center gap-2 text-zinc-500 text-sm p-4 bg-zinc-800/40 border border-zinc-700 rounded-lg">
            <AlertCircle className="w-4 h-4" /> No server selected. <Link to="/select-server" className="text-indigo-400 hover:text-indigo-300 underline ml-1">Pick one</Link>.
          </div>
        ) : children}
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
