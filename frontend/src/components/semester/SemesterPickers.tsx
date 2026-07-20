import {
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type ReactNode,
  type RefObject,
  type SetStateAction,
} from "react";
import { createPortal } from "react-dom";
import { AtSign, CheckCircle2, ChevronDown, Search, X } from "lucide-react";
import { cn } from "@/lib/utils";

export type SemesterCategory = { id: string; name: string; position: number };
export type SemesterRole = { id: string; name: string; color: string; position: number };

const dropdownStyle = {
  background: "#3f3f46",
  border: "1px solid #52525b",
  boxShadow: "0 8px 32px rgba(0,0,0,0.7)",
  borderRadius: 8,
  overflow: "hidden",
} as const;

export function roleColor(color: string | undefined) {
  return !color || color === "#000000" ? "#6b7280" : color;
}

function useDropdown(
  open: boolean,
  setOpen: Dispatch<SetStateAction<boolean>>,
  triggerRef: RefObject<HTMLElement | null>
) {
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [rect, setRect] = useState<DOMRect | null>(null);

  useEffect(() => {
    if (!open) return;
    setRect(triggerRef.current?.getBoundingClientRect() ?? null);

    const closeOutside = (event: MouseEvent) => {
      if (
        !triggerRef.current?.contains(event.target as Node)
        && !dropdownRef.current?.contains(event.target as Node)
      ) {
        setOpen(false);
        setRect(null);
      }
    };
    document.addEventListener("mousedown", closeOutside);
    return () => document.removeEventListener("mousedown", closeOutside);
  }, [open, setOpen, triggerRef]);

  return { dropdownRef, rect };
}

function Dropdown({ rect, dropdownRef, search, onSearch, children }: {
  rect: DOMRect;
  dropdownRef: RefObject<HTMLDivElement | null>;
  search: string;
  onSearch: (value: string) => void;
  children: ReactNode;
}) {
  return createPortal(
    <div
      ref={dropdownRef}
      style={{
        position: "fixed",
        top: rect.bottom + 4,
        left: rect.left,
        width: rect.width,
        zIndex: 9999,
        ...dropdownStyle,
      }}
    >
      <div className="flex items-center gap-2 px-3 py-2 bg-zinc-600 border-b border-zinc-500">
        <Search className="w-3.5 h-3.5 text-zinc-300 flex-shrink-0" />
        <input
          autoFocus
          value={search}
          onChange={event => onSearch(event.target.value)}
          placeholder="Search…"
          className="bg-transparent text-sm text-white outline-none flex-1 placeholder:text-zinc-400"
        />
      </div>
      {children}
    </div>,
    document.body
  );
}

export function RoleSelect({ roles, value, onChange, placeholder = "Select role…" }: {
  roles: SemesterRole[];
  value: string | null;
  onChange: (id: string | null) => void;
  placeholder?: string;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const triggerRef = useRef<HTMLButtonElement>(null);
  const { dropdownRef, rect } = useDropdown(open, setOpen, triggerRef);
  const selected = roles.find(role => role.id === value);
  const filtered = roles.filter(role => role.name.toLowerCase().includes(search.toLowerCase()));

  const close = () => {
    setOpen(false);
    setSearch("");
  };

  return (
    <>
      <button
        ref={triggerRef}
        onClick={() => setOpen(current => !current)}
        className="w-full flex items-center justify-between gap-2 px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 hover:border-zinc-500 transition-all"
      >
        <span className="flex items-center gap-2 min-w-0 truncate">
          {selected ? (
            <>
              <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: roleColor(selected.color) }} />
              <AtSign className="w-3.5 h-3.5 text-zinc-500 flex-shrink-0" />
              {selected.name}
            </>
          ) : (
            <span className="text-zinc-500">{placeholder}</span>
          )}
        </span>
        <ChevronDown className={cn("w-3.5 h-3.5 text-zinc-500 flex-shrink-0 transition-transform", open && "rotate-180")} />
      </button>
      {open && rect && (
        <Dropdown rect={rect} dropdownRef={dropdownRef} search={search} onSearch={setSearch}>
          <div className="max-h-48 overflow-y-auto scrollbar-thin">
            {value && (
              <button
                onClick={() => { onChange(null); close(); }}
                className="w-full text-left px-3 py-2 text-xs text-zinc-400 hover:bg-zinc-600 transition-colors"
              >
                — Clear
              </button>
            )}
            {filtered.map(role => (
              <button
                key={role.id}
                onClick={() => { onChange(role.id); close(); }}
                className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm hover:bg-zinc-600 transition-colors"
                style={{
                  color: value === role.id ? "#c7d2fe" : "#fff",
                  background: value === role.id ? "rgba(99,102,241,0.25)" : undefined,
                }}
              >
                <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: roleColor(role.color) }} />
                {role.name}
                {value === role.id && <CheckCircle2 className="w-3.5 h-3.5 ml-auto text-indigo-400 flex-shrink-0" />}
              </button>
            ))}
          </div>
        </Dropdown>
      )}
    </>
  );
}

function SearchableMultiSelect<T extends { id: string; name: string }>({
  items,
  selected,
  onChange,
  placeholder,
  emptyLabel,
  renderMarker,
}: {
  items: T[];
  selected: string[];
  onChange: (ids: string[]) => void;
  placeholder: string;
  emptyLabel: string;
  renderMarker?: (item: T) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const triggerRef = useRef<HTMLDivElement>(null);
  const { dropdownRef, rect } = useDropdown(open, setOpen, triggerRef);
  const selectedItems = items.filter(item => selected.includes(item.id));
  const filtered = items.filter(item => (
    !selected.includes(item.id)
    && item.name.toLowerCase().includes(search.toLowerCase())
  ));

  return (
    <>
      <div
        ref={triggerRef}
        onClick={() => setOpen(current => !current)}
        className="min-h-[38px] flex flex-wrap gap-1.5 items-center px-3 py-1.5 bg-zinc-800 border border-zinc-700 rounded cursor-pointer hover:border-zinc-500 transition-all"
      >
        {selectedItems.length === 0 && <span className="text-sm text-zinc-500">{placeholder}</span>}
        {selectedItems.map(item => (
          <span key={item.id} className="flex items-center gap-1 bg-zinc-700 text-zinc-200 text-xs px-2 py-0.5 rounded">
            {renderMarker?.(item)}
            {item.name}
            <button
              onClick={event => {
                event.stopPropagation();
                onChange(selected.filter(id => id !== item.id));
              }}
              className="ml-0.5 text-zinc-400 hover:text-red-400 transition-colors"
            >
              <X className="w-3 h-3" />
            </button>
          </span>
        ))}
      </div>
      {open && rect && (
        <Dropdown rect={rect} dropdownRef={dropdownRef} search={search} onSearch={setSearch}>
          <div className="max-h-44 overflow-y-auto scrollbar-thin">
            {filtered.length === 0 && <p className="px-3 py-3 text-xs text-zinc-500">{emptyLabel}</p>}
            {filtered.map(item => (
              <button
                key={item.id}
                onClick={() => {
                  onChange([...selected, item.id]);
                  setSearch("");
                }}
                className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm text-white hover:bg-zinc-600 transition-colors"
              >
                {renderMarker?.(item)}
                {item.name}
              </button>
            ))}
          </div>
        </Dropdown>
      )}
    </>
  );
}

export function RoleMultiSelect({ roles, selected, onChange, placeholder }: {
  roles: SemesterRole[];
  selected: string[];
  onChange: (ids: string[]) => void;
  placeholder: string;
}) {
  return (
    <SearchableMultiSelect
      items={roles}
      selected={selected}
      onChange={onChange}
      placeholder={placeholder}
      emptyLabel="No roles"
      renderMarker={role => (
        <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: roleColor(role.color) }} />
      )}
    />
  );
}

export function CategoryMultiSelect({ categories, selected, onChange }: {
  categories: SemesterCategory[];
  selected: string[];
  onChange: (ids: string[]) => void;
}) {
  return (
    <SearchableMultiSelect
      items={categories}
      selected={selected}
      onChange={onChange}
      placeholder="Select categories…"
      emptyLabel="No categories"
    />
  );
}
