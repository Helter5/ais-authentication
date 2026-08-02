import { useEffect, useRef, useState } from "react";
import { adminApi, apiErrorMessage } from "@/lib/api";
import {
  Loader2, CheckCircle2, AlertCircle, Search, X, Plus,
  ChevronDown, Shield, Bell, AlertTriangle,
  Hash, UserCheck, UserMinus, Clock, Users, Save,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { NumberStepper } from "@/components/ui/number-stepper";
import { Toggle, useSelectedGuildId } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";

// ─── types ────────────────────────────────────────────────────────────────────

type DiscordRole    = { id: string; name: string; color: string; position: number };
type DiscordChannel = { id: string; name: string; position: number };

// ─── Searchable picker ────────────────────────────────────────────────────────

function Picker<T extends { id: string; name: string }>({
  options,
  value,
  onChange,
  placeholder,
  colorMap,
  prefix,
}: {
  options: T[];
  value: string | null;
  onChange: (id: string | null) => void;
  placeholder: string;
  colorMap?: Record<string, string>;
  prefix?: string;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function h(e: MouseEvent) { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); }
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  const selected = options.find(o => o.id === value);
  const filtered = options.filter(o => o.name.toLowerCase().includes(search.toLowerCase()));

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between gap-2 px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 hover:border-zinc-500 transition-all"
      >
        <span className="flex items-center gap-2 min-w-0">
          {selected ? (
            <>
              {colorMap && <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: colorMap[selected.id] || "#6b7280" }} />}
              {prefix && <span className="text-zinc-500 text-xs flex-shrink-0">{prefix}</span>}
              <span className="truncate">{selected.name}</span>
            </>
          ) : (
            <span className="text-zinc-500">{placeholder}</span>
          )}
        </span>
        <ChevronDown className={cn("w-3.5 h-3.5 text-zinc-500 flex-shrink-0 transition-transform", open && "rotate-180")} />
      </button>

      {open && (
        <div className="absolute z-50 top-full mt-1 w-full rounded-lg overflow-hidden"
          style={{ background: "#3f3f46", border: "1px solid #52525b", boxShadow: "0 8px 32px rgba(0,0,0,0.6)" }}>
          <div className="flex items-center gap-2 px-3 py-2" style={{ background: "#52525b", borderBottom: "1px solid #71717a" }}>
            <Search className="w-3.5 h-3.5 flex-shrink-0" style={{ color: "#d4d4d8" }} />
            <input autoFocus value={search} onChange={e => setSearch(e.target.value)}
              placeholder="Search…"
              className="bg-transparent text-sm outline-none flex-1"
              style={{ color: "#ffffff" }}
            />
          </div>
          <div className="max-h-52 overflow-y-auto scrollbar-thin">
            {value && (
              <button onClick={() => { onChange(null); setOpen(false); }}
                className="w-full text-left px-3 py-2 text-xs transition-colors"
                style={{ color: "#a1a1aa" }}
                onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = "#52525b"; (e.currentTarget as HTMLButtonElement).style.color = "#ffffff"; }}
                onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = "transparent"; (e.currentTarget as HTMLButtonElement).style.color = "#a1a1aa"; }}>
                — Clear selection
              </button>
            )}
            {filtered.length === 0
              ? <p className="text-xs px-3 py-3" style={{ color: "#a1a1aa" }}>No results</p>
              : filtered.map(opt => (
                <button key={opt.id} onClick={() => { onChange(opt.id); setOpen(false); setSearch(""); }}
                  className="w-full flex items-center gap-2 text-left px-3 py-2 text-sm transition-colors"
                  style={{ background: value === opt.id ? "rgba(99,102,241,0.25)" : "transparent", color: value === opt.id ? "#c7d2fe" : "#ffffff" }}
                  onMouseEnter={e => { if (value !== opt.id) (e.currentTarget as HTMLButtonElement).style.background = "#52525b"; }}
                  onMouseLeave={e => { if (value !== opt.id) (e.currentTarget as HTMLButtonElement).style.background = "transparent"; }}>
                  {colorMap && <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: colorMap[opt.id] || "#6b7280" }} />}
                  {prefix && <span className="text-xs flex-shrink-0" style={{ color: "#71717a" }}>{prefix}</span>}
                  <span className="truncate">{opt.name}</span>
                  {value === opt.id && <CheckCircle2 className="w-3.5 h-3.5 ml-auto flex-shrink-0" style={{ color: "#818cf8" }} />}
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Section wrapper ──────────────────────────────────────────────────────────

function Section({ icon: Icon, title, description, children }: {
  icon: React.ElementType;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-900">
      <div className="px-5 py-4 border-b border-zinc-800">
        <div className="flex items-center gap-2">
          <Icon className="w-4 h-4 text-indigo-400" />
          <h2 className="font-bold text-zinc-100 text-sm tracking-tight">{title}</h2>
        </div>
        <p className="text-xs text-zinc-500 mt-0.5">{description}</p>
      </div>
      <div className="px-5 py-4 space-y-4">{children}</div>
    </div>
  );
}

// ─── Field row ────────────────────────────────────────────────────────────────

function FieldRow({ label, hint, icon: Icon, children }: {
  label: string;
  hint?: string;
  icon?: React.ElementType;
  children: React.ReactNode;
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-[160px_1fr] gap-2 items-start">
      <div className="pt-2">
        <p className="flex items-center gap-1.5 text-xs font-semibold text-zinc-300">
          {Icon && <Icon className="w-3.5 h-3.5 text-zinc-500" />}
          {label}
        </p>
        {hint && <p className="text-[11px] text-zinc-600 mt-0.5">{hint}</p>}
      </div>
      <div>{children}</div>
    </div>
  );
}

// ─── Save feedback ────────────────────────────────────────────────────────────

function useSave() {
  const [state, setState] = useState<"idle" | "saving" | "saved" | "error">("idle");
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { toast } = useToast();

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const save = async (fn: () => Promise<unknown>, successMessage = "Saved.") => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setState("saving");
    try {
      await fn();
      setState("saved");
      toast(successMessage);
      timerRef.current = setTimeout(() => setState("idle"), 2000);
    } catch {
      setState("error");
      toast("Failed to save.", "error");
      timerRef.current = setTimeout(() => setState("idle"), 3000);
    }
  };

  const indicator = (
    <span className="text-[11px] flex items-center gap-1 h-4">
      {state === "saving" && <><Loader2 className="w-3 h-3 animate-spin text-zinc-500" /><span className="text-zinc-500">Saving…</span></>}
      {state === "saved"  && <><CheckCircle2 className="w-3 h-3 text-emerald-400" /><span className="text-emerald-400">Saved</span></>}
      {state === "error"  && <><AlertCircle className="w-3 h-3 text-red-400" /><span className="text-red-400">Error</span></>}
    </span>
  );

  return { save, indicator };
}

// ─── Roles section ────────────────────────────────────────────────────────────
// Purely controlled by the parent Settings() draft state - no per-field auto-save here anymore,
// see the single "Save Settings" button at the bottom of the page.

function RolesSection({ roles, verifiedRole, onVerifiedRoleChange, inactiveRole, onInactiveRoleChange, verifEnabled, onVerifEnabledChange }: {
  roles: DiscordRole[];
  verifiedRole: string | null;
  onVerifiedRoleChange: (id: string | null) => void;
  inactiveRole: string | null;
  onInactiveRoleChange: (id: string | null) => void;
  verifEnabled: boolean;
  onVerifEnabledChange: (v: boolean) => void;
}) {
  const colorMap = Object.fromEntries(roles.map(r => [r.id, r.color === "#000000" ? "#6b7280" : r.color]));

  return (
    <Section icon={Shield} title="Verification & Roles" description="Enable verification and configure roles for verified and inactive members.">
      <FieldRow label="Verification" hint="Disable to block /verify and /code" icon={UserCheck}>
        <div className="flex items-center gap-3 pt-1.5">
          <Toggle enabled={verifEnabled} onChange={onVerifEnabledChange} />
        </div>
      </FieldRow>
      <FieldRow label="Verified Role" hint="Assigned on successful verification" icon={UserCheck}>
        <Picker options={roles} value={verifiedRole} onChange={onVerifiedRoleChange} placeholder="Select a role…" colorMap={colorMap} prefix="@" />
      </FieldRow>
      <FieldRow label="Inactive Role" hint="Assigned by /wipe to inactive students" icon={UserMinus}>
        <Picker options={roles} value={inactiveRole} onChange={onInactiveRoleChange} placeholder="Select a role…" colorMap={colorMap} prefix="@" />
      </FieldRow>
    </Section>
  );
}


// ─── Manager Roles section ────────────────────────────────────────────────────

type ManagerRole = Awaited<ReturnType<typeof adminApi.getManagerRoles>>["roles"][number];

function ManagerRolePicker({ roles, value, onChange }: {
  roles: ManagerRole[];
  value: string[];
  onChange: (ids: string[]) => void;
}) {
  const [search, setSearch] = useState("");
  const selected = roles.filter(r => value.includes(r.id));
  const filtered = roles.filter(r => r.name.toLowerCase().includes(search.toLowerCase()));
  const toggle = (id: string) => onChange(value.includes(id) ? value.filter(rid => rid !== id) : [...value, id]);

  return (
    <div className="rounded-lg border border-zinc-700 bg-zinc-800">
      <div className="flex min-h-11 flex-wrap gap-1.5 border-b border-zinc-700 p-2">
        {selected.length === 0 && <span className="px-1 py-1 text-sm text-zinc-500">No manager roles selected</span>}
        {selected.map(r => (
          <span key={r.id} className="flex items-center gap-1 rounded border border-zinc-600 bg-zinc-900 px-2 py-1 text-xs text-zinc-300">
            <span className="h-2 w-2 rounded-full flex-shrink-0" style={{ background: r.color === "#000000" ? "#71717a" : r.color }} />
            {r.name}
            <button onClick={() => toggle(r.id)} className="text-zinc-500 hover:text-red-400"><X className="h-3 w-3" /></button>
          </span>
        ))}
      </div>
      <div className="flex items-center gap-2 border-b border-zinc-700 px-3 py-2">
        <Search className="h-3.5 w-3.5 text-zinc-500 flex-shrink-0" />
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search roles…"
          className="min-w-0 flex-1 bg-transparent text-sm text-zinc-200 outline-none placeholder:text-zinc-600" />
      </div>
      <div className="max-h-48 overflow-y-auto scrollbar-thin p-1">
        {filtered.map(r => (
          <button key={r.id} onClick={() => toggle(r.id)}
            className="flex w-full items-center gap-2 rounded px-2 py-2 text-left text-sm text-zinc-300 hover:bg-zinc-700">
            <span className={cn("flex h-4 w-4 items-center justify-center rounded border flex-shrink-0",
              value.includes(r.id) ? "border-indigo-500 bg-indigo-600" : "border-zinc-600")}>
              {value.includes(r.id) && <CheckCircle2 className="h-3 w-3 text-white" />}
            </span>
            <span className="h-2.5 w-2.5 rounded-full flex-shrink-0" style={{ background: r.color === "#000000" ? "#71717a" : r.color }} />
            {r.name}
          </button>
        ))}
      </div>
    </div>
  );
}

function ManagerRolesSection({ roles, selected, onChange }: {
  roles: ManagerRole[];
  selected: string[];
  onChange: (ids: string[]) => void;
}) {
  return (
    <Section
      icon={Users}
      title="Manager Roles"
      description="Roles allowed to log in and use the standard server-management pages."
    >
      <div className="space-y-3">
        <p className="rounded border border-indigo-500/20 bg-indigo-500/10 px-3 py-2 text-xs leading-relaxed text-indigo-200">
          Managers can sign in and access Dashboard, Codes, Users Directory, Semester, and Logs.
          They cannot access the Super Admin area: Admin, Settings, Modules, Commands, Wipe, or Docker Logs.
        </p>
        <ManagerRolePicker roles={roles} value={selected} onChange={onChange} />
      </div>
    </Section>
  );
}

type LogChannelEntry = Awaited<ReturnType<typeof adminApi.getLogChannels>>["eventTypes"][number];

function LogChannelsSection({ channels, entries, assignments, setAssignments, channelSlots, setChannelSlots }: {
  channels: DiscordChannel[];
  entries: LogChannelEntry[];
  assignments: Record<string, string | null>;
  setAssignments: React.Dispatch<React.SetStateAction<Record<string, string | null>>>;
  channelSlots: string[];
  setChannelSlots: React.Dispatch<React.SetStateAction<string[]>>;
}) {
  const [newChannel, setNewChannel] = useState<string | null>(null);

  const channelOptions = channels.map(c => ({ id: c.id, name: `#${c.name}` }));
  const channelName = (id: string) => channelOptions.find(c => c.id === id)?.name ?? `#${id}`;

  const reassign = (fromChannelId: string | null, toChannelId: string | null) => {
    setAssignments(a => {
      const next = { ...a };
      Object.keys(next).forEach(key => { if (next[key] === fromChannelId) next[key] = toChannelId; });
      return next;
    });
  };

  const addChannelSlot = () => {
    if (!newChannel || channelSlots.includes(newChannel)) return;
    setChannelSlots(s => [...s, newChannel]);
    setNewChannel(null);
  };

  const removeChannelSlot = (channelId: string) => {
    setChannelSlots(s => s.filter(id => id !== channelId));
    reassign(channelId, null);
  };

  const changeSlotChannel = (oldChannelId: string, newChannelId: string | null) => {
    if (!newChannelId || newChannelId === oldChannelId || channelSlots.includes(newChannelId)) return;
    setChannelSlots(s => s.map(id => id === oldChannelId ? newChannelId : id));
    reassign(oldChannelId, newChannelId);
  };

  const toggleEvent = (eventType: string, channelId: string) => {
    setAssignments(a => ({ ...a, [eventType]: a[eventType] === channelId ? null : channelId }));
  };

  const unassigned = entries.filter(e => !assignments[e.eventType]);

  return (
    <Section icon={Bell} title="Log Channels" description="Pick a channel, then choose which events log to it.">
      <div className="space-y-4">
        {entries.filter(e => e.configured && !e.ok && assignments[e.eventType] === e.channelId).map(e => (
          <div key={e.eventType} className="flex items-start gap-2 rounded border border-amber-500/20 bg-amber-500/10 px-3 py-2 text-xs text-amber-300">
            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
            <span><strong>{e.label}:</strong> {e.error}</span>
          </div>
        ))}

        {channelSlots.length === 0 && (
          <p className="text-xs text-zinc-600">No log channels configured yet.</p>
        )}

        {channelSlots.map(channelId => (
          <div key={channelId} className="rounded-lg border border-zinc-700 bg-zinc-800/60 p-3 space-y-2">
            <div className="flex items-center gap-2">
              <Hash className="w-3.5 h-3.5 text-zinc-500 flex-shrink-0" />
              <div className="flex-1">
                <Picker
                  options={channelOptions.filter(c => c.id === channelId || !channelSlots.includes(c.id))}
                  value={channelId}
                  onChange={id => id && changeSlotChannel(channelId, id)}
                  placeholder="Select channel…" />
              </div>
              <button onClick={() => removeChannelSlot(channelId)} className="text-zinc-600 hover:text-red-400 transition-colors flex-shrink-0">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
            <div className="space-y-1 pl-1">
              {entries.map(e => (
                <div key={e.eventType} className="flex items-start gap-2 py-1">
                  <button type="button" onClick={() => toggleEvent(e.eventType, channelId)}
                    className={cn("mt-0.5 flex h-4 w-4 items-center justify-center rounded border flex-shrink-0",
                      assignments[e.eventType] === channelId ? "border-indigo-500 bg-indigo-600" : "border-zinc-600")}>
                    {assignments[e.eventType] === channelId && <CheckCircle2 className="h-3 w-3 text-white" />}
                  </button>
                  <span className="text-xs leading-snug">
                    <span className="font-semibold text-zinc-200">{e.label}</span>
                    <span className="text-zinc-500"> — {e.description}</span>
                    {assignments[e.eventType] && assignments[e.eventType] !== channelId && (
                      <span className="text-zinc-600"> (currently {channelName(assignments[e.eventType]!)})</span>
                    )}
                  </span>
                </div>
              ))}
            </div>
          </div>
        ))}

        {unassigned.length > 0 && (
          <p className="text-[11px] text-zinc-600">Not logged anywhere: {unassigned.map(e => e.label).join(", ")}</p>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-2 items-end pt-1">
          <div>
            <p className="text-[11px] text-zinc-600 mb-1 flex items-center gap-1"><Hash className="w-3 h-3" /> Add channel</p>
            <Picker
              options={channelOptions.filter(c => !channelSlots.includes(c.id))}
              value={newChannel} onChange={setNewChannel} placeholder="Pick channel…" />
          </div>
          <button onClick={addChannelSlot} disabled={!newChannel}
            className="flex items-center gap-1.5 px-3 py-2 rounded text-xs font-bold uppercase tracking-wider border border-zinc-600 bg-zinc-800 text-zinc-300 hover:border-indigo-500/60 hover:text-indigo-300 transition-all disabled:opacity-40">
            <Plus className="w-3.5 h-3.5" /> Add
          </button>
        </div>
      </div>
    </Section>
  );
}

// ─── Ticket retention section ──────────────────────────────────────────────────

function TicketRetentionSection({ enabled, onEnabledChange, days, onDaysChange }: {
  enabled: boolean;
  onEnabledChange: (v: boolean) => void;
  days: number;
  onDaysChange: (v: number) => void;
}) {
  return (
    <Section icon={Clock} title="Ticket Transcripts" description="Automatically clear saved ticket transcript content once it's old enough.">
      <FieldRow label="Auto-delete transcripts" hint="Clears transcript content for tickets closed longer ago than the retention period" icon={Clock}>
        <div className="flex items-center gap-3 pt-1.5">
          <Toggle enabled={enabled} onChange={onEnabledChange} />
        </div>
      </FieldRow>
      {enabled && (
        <FieldRow label="Retention period" hint="Days to keep a transcript after the ticket is closed" icon={Clock}>
          <div className="flex items-center gap-2">
            <NumberStepper value={days} onChange={onDaysChange} min={1} max={365} className="w-32" ariaLabel="Ticket transcript retention in days" />
            <span className="text-xs text-zinc-500">days</span>
          </div>
        </FieldRow>
      )}
    </Section>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function Settings() {
  const guildId = useSelectedGuildId();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [roles, setRoles] = useState<DiscordRole[]>([]);
  const [channels, setChannels] = useState<DiscordChannel[]>([]);

  // Draft state for every "simple setting" section - nothing here saves until the single
  // "Save Settings" button below is clicked (Warn Thresholds / Auto-Mentions are exempt: adding or
  // removing a list entry is already a single, deliberate action with its own button, not a value
  // worth "drafting").
  const [verifiedRole, setVerifiedRole] = useState<string | null>(null);
  const [inactiveRole, setInactiveRole] = useState<string | null>(null);
  const [verifEnabled, setVerifEnabled] = useState(false);
  const [ticketRetentionEnabled, setTicketRetentionEnabled] = useState(false);
  const [ticketRetentionDays, setTicketRetentionDays] = useState(90);
  const [managerRoles, setManagerRoles] = useState<ManagerRole[]>([]);
  const [managerRoleIds, setManagerRoleIds] = useState<string[]>([]);
  const [logChannelEntries, setLogChannelEntries] = useState<LogChannelEntry[]>([]);
  const [logChannelAssignments, setLogChannelAssignments] = useState<Record<string, string | null>>({});
  const [logChannelSlots, setLogChannelSlots] = useState<string[]>([]);

  const { save, indicator } = useSave();

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setRoles([]);
    setChannels([]);
    if (!guildId) {
      setError("No server selected. Go back and pick one.");
      setLoading(false);
      return () => { cancelled = true; };
    }
    Promise.all([
      adminApi.getSettings(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
      adminApi.getManagerRoles(guildId),
      adminApi.getLogChannels(guildId),
    ])
      .then(([s, r, c, mr, lc]) => {
        if (cancelled) return;
        setRoles(r);
        setChannels(c);
        setVerifiedRole(s.verified_role_id);
        setInactiveRole(s.inactive_role_id);
        setVerifEnabled(s.verification_enabled);
        setTicketRetentionEnabled(s.ticket_retention_enabled);
        setTicketRetentionDays(s.ticket_retention_days);
        setManagerRoles(mr.roles);
        setManagerRoleIds(mr.managerRoleIds);
        const initialAssignments: Record<string, string | null> = {};
        const initialSlots: string[] = [];
        lc.eventTypes.forEach(e => {
          initialAssignments[e.eventType] = e.channelId;
          if (e.channelId && !initialSlots.includes(e.channelId)) initialSlots.push(e.channelId);
        });
        setLogChannelEntries(lc.eventTypes);
        setLogChannelAssignments(initialAssignments);
        setLogChannelSlots(initialSlots);
      })
      .catch(e => {
        if (!cancelled) setError(apiErrorMessage(e, e?.message ?? "Failed to load"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [guildId]);

  const handleSaveAll = () => {
    if (!guildId) return;
    save(() => Promise.all([
      adminApi.updateSettingsBulk(guildId, {
        verified_role_id: verifiedRole,
        inactive_role_id: inactiveRole,
        verification_enabled: verifEnabled,
        ticket_retention_enabled: ticketRetentionEnabled,
        ticket_retention_days: ticketRetentionDays,
      }),
      adminApi.updateManagerRoles(guildId, managerRoleIds),
      adminApi.updateLogChannels(guildId, logChannelAssignments),
    ]), "Settings saved.");
  };

  return (
    <div className="flex flex-col gap-6 md:pl-64">
      <div className="px-4 sm:px-6 pt-5">
        <h1 className="text-2xl font-bold text-zinc-100 tracking-tight">Settings</h1>
        <p className="text-sm text-zinc-500 mt-0.5">Server configuration — click Save to apply changes.</p>
      </div>

      <div className="px-4 sm:px-6 pb-28">
        {loading && (
          <div className="flex items-center gap-2 text-zinc-500 text-sm">
            <Loader2 className="w-4 h-4 animate-spin" /> Loading…
          </div>
        )}
        {error && (
          <div className="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 text-red-400 text-sm rounded-lg">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />{error}
          </div>
        )}
        {!loading && !error && guildId && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Left column */}
            <div className="flex flex-col gap-4">
              <RolesSection
                roles={roles}
                verifiedRole={verifiedRole} onVerifiedRoleChange={setVerifiedRole}
                inactiveRole={inactiveRole} onInactiveRoleChange={setInactiveRole}
                verifEnabled={verifEnabled} onVerifEnabledChange={setVerifEnabled}
              />
            </div>
            {/* Right column */}
            <div className="flex flex-col gap-4">
              <ManagerRolesSection roles={managerRoles} selected={managerRoleIds} onChange={setManagerRoleIds} />
              <LogChannelsSection
                channels={channels}
                entries={logChannelEntries}
                assignments={logChannelAssignments} setAssignments={setLogChannelAssignments}
                channelSlots={logChannelSlots} setChannelSlots={setLogChannelSlots}
              />
              <TicketRetentionSection
                enabled={ticketRetentionEnabled} onEnabledChange={setTicketRetentionEnabled}
                days={ticketRetentionDays} onDaysChange={setTicketRetentionDays}
              />
            </div>
          </div>
        )}
      </div>

      {!loading && !error && guildId && (
        <div className="fixed bottom-0 left-0 right-0 md:pl-64 border-t border-zinc-800 bg-zinc-950/95 backdrop-blur">
          <div className="flex items-center gap-4 px-4 sm:px-6 py-3">
            <button onClick={handleSaveAll}
              className="flex items-center gap-2 rounded bg-indigo-600 px-6 py-2.5 text-sm font-bold text-white hover:bg-indigo-500 transition-colors">
              <Save className="h-4 w-4" /> Save Settings
            </button>
            {indicator}
          </div>
        </div>
      )}
    </div>
  );
}
