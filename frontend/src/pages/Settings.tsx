import { useEffect, useRef, useState } from "react";
import { adminApi } from "@/lib/api";
import {
  Loader2, CheckCircle2, AlertCircle, Search, X, Plus,
  ChevronDown, Shield, Bell, AlertTriangle, Info,
  Hash, AtSign, UserCheck, UserMinus, UserX, Clock, MessageSquare, ArrowRight, Users, Save, FileText,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { NumberStepper } from "@/components/ui/number-stepper";
import { Toggle, useSelectedGuildId } from "@/components/modules/shared";

// ─── types ────────────────────────────────────────────────────────────────────

type DiscordRole    = { id: string; name: string; color: string; position: number };
type DiscordChannel = { id: string; name: string; position: number };
type GuildSettings  = Awaited<ReturnType<typeof adminApi.getSettings>>;
type WarnThreshold  = { warn_limit: number; action: string };
type AutoMention    = { channel_id: string; role_id: string; enabled: boolean };

const ACTION_META: Record<string, { label: string; icon: React.ElementType; color: string; bg: string; border: string }> = {
  kick:    { label: "Kick",        icon: UserMinus,     color: "text-orange-300", bg: "bg-orange-500/10", border: "border-orange-500/30" },
  ban:     { label: "Ban",         icon: UserX,         color: "text-red-300",    bg: "bg-red-500/10",    border: "border-red-500/30"    },
  timeout: { label: "Timeout 24h", icon: Clock,         color: "text-amber-300",  bg: "bg-amber-500/10",  border: "border-amber-500/30"  },
  none:    { label: "Warn only",   icon: MessageSquare, color: "text-zinc-400",   bg: "bg-zinc-800",      border: "border-zinc-700"      },
};

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

function FieldRow({ label, hint, icon: Icon, info, children }: {
  label: string;
  hint?: string;
  icon?: React.ElementType;
  info?: { source: string; description: string }[];
  children: React.ReactNode;
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-[160px_1fr] gap-2 items-start">
      <div className="pt-2">
        <p className="flex items-center gap-1.5 text-xs font-semibold text-zinc-300">
          {Icon && <Icon className="w-3.5 h-3.5 text-zinc-500" />}
          {label}
          {info && <InfoTooltip items={info} />}
        </p>
        {hint && <p className="text-[11px] text-zinc-600 mt-0.5">{hint}</p>}
      </div>
      <div>{children}</div>
    </div>
  );
}

// ─── Log channel "what writes here" info tooltip ──────────────────────────────

const LOG_CHANNEL_PRODUCERS: Record<string, { source: string; description: string }[]> = {
  verification: [
    { source: "/verify", description: "Successful verification" },
    { source: "/manualverify", description: "Manual verification by an admin" },
    { source: "/code", description: "Verification via redemption code" },
    { source: "Member update watcher", description: "Suspicious verified-role changes" },
    { source: "Member leave watcher", description: "Removed verification record on leave" },
    { source: "Sync service", description: "Periodic database cleanup" },
  ],
  moderation: [
    { source: "/warn", description: "New warning issued" },
    { source: "/removewarn", description: "Warning removed" },
    { source: "/clearwarns", description: "All warnings cleared" },
  ],
  automod: [
    { source: "Hacked Account Trap module", description: "Trap triggered (timeout/kick/ban)" },
    { source: "/warn", description: "Automatic action when a warn threshold is reached" },
  ],
  wipe: [
    { source: "Wipe page", description: "Recap report after a wipe run" },
  ],
  semester: [
    { source: "Semester Switch page", description: "Recap report after a semester switch" },
  ],
  transcript: [
    { source: "Hacked Account Trap module", description: "Incident ticket transcript, saved when its Transcript button is clicked" },
  ],
};

function InfoTooltip({ items }: { items: { source: string; description: string }[] }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function h(e: MouseEvent) { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); }
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  return (
    <div ref={ref} className="relative inline-flex">
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        className="flex h-3.5 w-3.5 items-center justify-center rounded-full text-zinc-600 hover:text-indigo-300 transition-colors"
        aria-label="What logs to this channel"
      >
        <Info className="h-3.5 w-3.5" />
      </button>
      {open && (
        <div className="absolute z-50 left-0 top-full mt-1.5 w-64 rounded-lg border border-zinc-700 bg-zinc-800 p-3 shadow-xl">
          <p className="mb-1.5 text-[10px] font-bold uppercase tracking-wider text-zinc-500">Writes to this channel</p>
          <ul className="space-y-1.5">
            {items.map(item => (
              <li key={item.source} className="text-xs leading-snug">
                <span className="font-semibold text-zinc-200">{item.source}</span>
                <span className="text-zinc-500"> — {item.description}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

// ─── Save feedback ────────────────────────────────────────────────────────────

function useSave() {
  const [state, setState] = useState<"idle" | "saving" | "saved" | "error">("idle");
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const save = async (fn: () => Promise<unknown>) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setState("saving");
    try {
      await fn();
      setState("saved");
      timerRef.current = setTimeout(() => setState("idle"), 2000);
    } catch {
      setState("error");
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

function RolesSection({ guildId, settings, roles }: { guildId: string; settings: GuildSettings; roles: DiscordRole[] }) {
  const [verifiedRole, setVerifiedRole] = useState(settings.verified_role_id);
  const [inactiveRole, setInactiveRole] = useState(settings.inactive_role_id);
  const [verifEnabled, setVerifEnabled] = useState(settings.verification_enabled);
  const { save, indicator } = useSave();
  const { save: saveVerif, indicator: verifIndicator } = useSave();

  useEffect(() => {
    setVerifiedRole(settings.verified_role_id);
    setInactiveRole(settings.inactive_role_id);
    setVerifEnabled(settings.verification_enabled);
  }, [settings.verified_role_id, settings.inactive_role_id, settings.verification_enabled]);

  const colorMap = Object.fromEntries(roles.map(r => [r.id, r.color === "#000000" ? "#6b7280" : r.color]));

  const handleVerified = (id: string | null) => {
    setVerifiedRole(id);
    save(() => adminApi.updateSetting(guildId, "verified_role_id", id));
  };

  const handleInactive = (id: string | null) => {
    setInactiveRole(id);
    save(() => adminApi.updateSetting(guildId, "inactive_role_id", id));
  };

  const handleVerifEnabled = (v: boolean) => {
    setVerifEnabled(v);
    saveVerif(() => adminApi.updateSetting(guildId, "verification_enabled", v));
  };

  return (
    <Section icon={Shield} title="Verification & Roles" description="Enable verification and configure roles for verified and inactive members.">
      <FieldRow label="Verification" hint="Disable to block /verify and /code" icon={UserCheck}>
        <div className="flex items-center gap-3 pt-1.5">
          <Toggle enabled={verifEnabled} onChange={handleVerifEnabled} />
          {verifIndicator}
        </div>
      </FieldRow>
      <div className="flex justify-end">{indicator}</div>
      <FieldRow label="Verified Role" hint="Assigned on successful verification" icon={UserCheck}>
        <Picker options={roles} value={verifiedRole} onChange={handleVerified} placeholder="Select a role…" colorMap={colorMap} prefix="@" />
      </FieldRow>
      <FieldRow label="Inactive Role" hint="Assigned by /wipe to inactive students" icon={UserMinus}>
        <Picker options={roles} value={inactiveRole} onChange={handleInactive} placeholder="Select a role…" colorMap={colorMap} prefix="@" />
      </FieldRow>
    </Section>
  );
}

// ─── Warn Thresholds section ──────────────────────────────────────────────────

function WarnThresholdsSection({ guildId }: { guildId: string }) {
  const [thresholds, setThresholds] = useState<WarnThreshold[]>([]);
  const [loading, setLoading] = useState(true);
  const [newLimit, setNewLimit] = useState(1);
  const [newAction, setNewAction] = useState("kick");
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminApi.getWarnThresholds(guildId).then(setThresholds).finally(() => setLoading(false));
  }, [guildId]);

  const add = async () => {
    const limit = newLimit;
    setAdding(true);
    setError(null);
    try {
      await adminApi.addWarnThreshold(guildId, limit, newAction);
      setThresholds(await adminApi.getWarnThresholds(guildId));
      setNewLimit(1);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } } };
      setError(err.response?.data?.error ?? "Failed to add");
    } finally {
      setAdding(false);
    }
  };

  const remove = async (limit: number) => {
    try {
      await adminApi.removeWarnThreshold(guildId, limit);
      setThresholds(t => t.filter(x => x.warn_limit !== limit));
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } } };
      setError(err.response?.data?.error ?? "Failed to remove warning threshold");
    }
  };

  return (
    <Section icon={AlertTriangle} title="Warn Thresholds" description="Auto-punishment when a user reaches a warn count.">
      {loading ? (
        <Loader2 className="w-4 h-4 animate-spin text-zinc-500" />
      ) : (
        <div className="space-y-3">
          {thresholds.length === 0 ? (
            <p className="text-xs text-zinc-600">No thresholds configured.</p>
          ) : (
            <div className="space-y-1">
              {thresholds.map(t => {
                const meta = ACTION_META[t.action] ?? { label: t.action, icon: AlertTriangle, color: "text-zinc-400", bg: "bg-zinc-800", border: "border-zinc-700" };
                const ActionIcon = meta.icon;
                return (
                  <div key={t.warn_limit} className="flex items-center justify-between gap-3 px-3 py-2 bg-zinc-800/60 border border-zinc-700 rounded">
                    <div className="flex items-center gap-2">
                      <span className="inline-flex items-center gap-1 bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-xs font-bold px-2 py-0.5 rounded">
                        <AlertTriangle className="w-3 h-3" /> {t.warn_limit} warns
                      </span>
                      <ArrowRight className="w-3.5 h-3.5 text-zinc-600" />
                      <span className={cn("inline-flex items-center gap-1 text-xs font-bold px-2 py-0.5 rounded border", meta.color, meta.bg, meta.border)}>
                        <ActionIcon className="w-3 h-3" /> {meta.label}
                      </span>
                    </div>
                    <button onClick={() => remove(t.warn_limit)} className="text-zinc-600 hover:text-red-400 transition-colors flex-shrink-0">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          <div className="flex gap-2 items-center flex-wrap pt-1">
            <NumberStepper
              value={newLimit}
              onChange={setNewLimit}
              min={1}
              max={20}
              className="h-9 w-32"
              ariaLabel="Warning threshold"
            />
            <select value={newAction} onChange={e => setNewAction(e.target.value)}
              className="bg-zinc-800 border border-zinc-700 rounded px-3 py-1.5 text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors">
              {Object.entries(ACTION_META).map(([v, m]) => <option key={v} value={v}>{m.label}</option>)}
            </select>
            <button onClick={add} disabled={adding}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider border border-zinc-600 bg-zinc-800 text-zinc-300 hover:border-indigo-500/60 hover:text-indigo-300 transition-all disabled:opacity-40">
              {adding ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
              Add
            </button>
            {error && <span className="text-xs text-red-400">{error}</span>}
          </div>
        </div>
      )}
    </Section>
  );
}

// ─── Auto-Mentions section ────────────────────────────────────────────────────

function AutoMentionsSection({ guildId, roles, channels }: { guildId: string; roles: DiscordRole[]; channels: DiscordChannel[] }) {
  const [mentions, setMentions] = useState<AutoMention[]>([]);
  const [loading, setLoading] = useState(true);
  const [newChannel, setNewChannel] = useState<string | null>(null);
  const [newRole, setNewRole] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const colorMap = Object.fromEntries(roles.map(r => [r.id, r.color === "#000000" ? "#6b7280" : r.color]));

  useEffect(() => {
    adminApi.getAutoMentions(guildId).then(setMentions).finally(() => setLoading(false));
  }, [guildId]);

  const add = async () => {
    if (!newChannel || !newRole) return;
    setAdding(true);
    setError(null);
    try {
      await adminApi.addAutoMention(guildId, newChannel, newRole);
      setMentions(await adminApi.getAutoMentions(guildId));
      setNewChannel(null);
      setNewRole(null);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: string } } };
      setError(err.response?.data?.error ?? "Failed to add");
    } finally {
      setAdding(false);
    }
  };

  const toggle = async (channelId: string) => {
    const res = await adminApi.toggleAutoMention(guildId, channelId);
    setMentions(m => m.map(x => x.channel_id === channelId ? { ...x, enabled: res.enabled } : x));
  };

  const remove = async (channelId: string) => {
    await adminApi.removeAutoMention(guildId, channelId);
    setMentions(m => m.filter(x => x.channel_id !== channelId));
  };

  const channelName = (id: string) => channels.find(c => c.id === id)?.name ?? id;
  const roleName    = (id: string) => roles.find(r => r.id === id)?.name ?? id;
  const roleColor   = (id: string) => { const c = roles.find(r => r.id === id)?.color; return c === "#000000" ? "#6b7280" : c ?? "#6b7280"; };

  return (
    <Section icon={Bell} title="Auto-Mentions" description="Bot auto-mentions a role when a message is posted in a channel.">
      {loading ? (
        <Loader2 className="w-4 h-4 animate-spin text-zinc-500" />
      ) : (
        <div className="space-y-3">
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
                    <button onClick={() => toggle(m.channel_id)}
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

          <div className="grid grid-cols-1 sm:grid-cols-[1fr_1fr_auto] gap-2 items-end pt-1">
            <div>
              <p className="text-[11px] text-zinc-600 mb-1 flex items-center gap-1"><Hash className="w-3 h-3" /> Channel</p>
              <Picker options={channels} value={newChannel} onChange={setNewChannel} placeholder="Pick channel…" prefix="#" />
            </div>
            <div>
              <p className="text-[11px] text-zinc-600 mb-1 flex items-center gap-1"><AtSign className="w-3 h-3" /> Role to mention</p>
              <Picker options={roles} value={newRole} onChange={setNewRole} placeholder="Pick role…" colorMap={colorMap} prefix="@" />
            </div>
            <button onClick={add} disabled={adding || !newChannel || !newRole}
              className="flex items-center gap-1.5 px-3 py-2 rounded text-xs font-bold uppercase tracking-wider border border-zinc-600 bg-zinc-800 text-zinc-300 hover:border-indigo-500/60 hover:text-indigo-300 transition-all disabled:opacity-40">
              {adding ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
              Add
            </button>
          </div>
          {error && <p className="text-xs text-red-400">{error}</p>}
        </div>
      )}
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

function ManagerRolesSection({ guildId }: { guildId: string }) {
  const [roles, setRoles] = useState<ManagerRole[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const { save, indicator } = useSave();

  useEffect(() => {
    adminApi.getManagerRoles(guildId)
      .then(r => { setRoles(r.roles); setSelected(r.managerRoleIds); })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [guildId]);

  const handleSave = () => save(() => adminApi.updateManagerRoles(guildId, selected));

  return (
    <Section
      icon={Users}
      title="Manager Roles"
      description="Roles allowed to log in and use the standard server-management pages."
    >
      {loading ? (
        <div className="flex items-center gap-2 text-sm text-zinc-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</div>
      ) : (
        <div className="space-y-3">
          <p className="rounded border border-indigo-500/20 bg-indigo-500/10 px-3 py-2 text-xs leading-relaxed text-indigo-200">
            Managers can sign in and access Dashboard, Codes, Users Directory, Semester, and Logs.
            They cannot access the Super Admin area: Admin, Settings, Modules, Commands, Wipe, or Docker Logs.
          </p>
          <ManagerRolePicker roles={roles} value={selected} onChange={setSelected} />
          <div className="flex items-center gap-3">
            <button onClick={handleSave}
              className="flex items-center gap-2 rounded bg-indigo-600 px-4 py-2 text-xs font-bold text-white hover:bg-indigo-500 disabled:opacity-50">
              <Save className="h-4 w-4" /> Save Manager Roles
            </button>
            {indicator}
          </div>
        </div>
      )}
    </Section>
  );
}

function RecapLogsSection({ guildId, channels }: { guildId: string; channels: DiscordChannel[] }) {
  const [wipeChannelId, setWipeChannelId] = useState<string>("");
  const [semesterChannelId, setSemesterChannelId] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [health, setHealth] = useState<Record<'wipe' | 'semester', { ok: boolean; configured: boolean; error: string | null }> | null>(null);
  const { save, indicator } = useSave();

  useEffect(() => {
    adminApi.getRecapChannels(guildId)
      .then(r => {
        setWipeChannelId(r.wipeChannelId ?? "");
        setSemesterChannelId(r.semesterChannelId ?? "");
        setHealth(r.health);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [guildId]);

  const handleSave = () =>
    save(() => adminApi.setRecapChannels(guildId, wipeChannelId || null, semesterChannelId || null));

  const chanOpts = channels.map(c => ({ id: c.id, name: `#${c.name}` }));

  return (
    <Section icon={FileText} title="Recap Log Channels" description="Where wipe and semester switch reports are sent after completion. Required to access those tools.">
      {loading ? (
        <div className="flex items-center gap-2 text-sm text-zinc-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</div>
      ) : (
        <div className="space-y-3">
          {health && Object.entries(health).filter(([, item]) => item.configured && !item.ok).map(([type, item]) => (
            <div key={type} className="flex items-start gap-2 rounded border border-amber-500/20 bg-amber-500/10 px-3 py-2 text-xs text-amber-300">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
              <span><strong className="capitalize">{type} recap:</strong> {item.error}</span>
            </div>
          ))}
          <FieldRow label="Wipe Log" icon={Hash} info={LOG_CHANNEL_PRODUCERS.wipe} hint="Required to access the Wipe page">
            <Picker options={chanOpts} value={wipeChannelId} onChange={id => setWipeChannelId(id ?? "")} placeholder="Select channel…" />
          </FieldRow>
          <FieldRow label="Semester Log" icon={Hash} info={LOG_CHANNEL_PRODUCERS.semester} hint="Required to access the Semester Switch page">
            <Picker options={chanOpts} value={semesterChannelId} onChange={id => setSemesterChannelId(id ?? "")} placeholder="Select channel…" />
          </FieldRow>
          <div className="flex items-center gap-3 pt-1">
            <button onClick={handleSave}
              className="flex items-center gap-2 rounded bg-indigo-600 px-4 py-2 text-xs font-bold text-white hover:bg-indigo-500">
              <Save className="h-4 w-4" /> Save
            </button>
            {indicator}
          </div>
        </div>
      )}
    </Section>
  );
}

function LogChannelsSection({ guildId, settings, channels }: {
  guildId: string;
  settings: GuildSettings;
  channels: DiscordChannel[];
}) {
  const [verificationLog, setVerificationLog] = useState(settings.log_channel_id ?? "");
  const [moderationLog, setModerationLog] = useState(settings.warn_log_channel_id ?? "");
  const [automodLog, setAutomodLog] = useState(settings.spam_log_channel_id ?? "");
  const [transcriptLog, setTranscriptLog] = useState(settings.transcript_log_channel_id ?? "");
  const { save, indicator } = useSave();
  const channelOptions = channels.map(channel => ({ id: channel.id, name: `#${channel.name}` }));

  useEffect(() => {
    setVerificationLog(settings.log_channel_id ?? "");
    setModerationLog(settings.warn_log_channel_id ?? "");
    setAutomodLog(settings.spam_log_channel_id ?? "");
    setTranscriptLog(settings.transcript_log_channel_id ?? "");
  }, [settings.log_channel_id, settings.warn_log_channel_id, settings.spam_log_channel_id, settings.transcript_log_channel_id]);

  const handleSave = () => save(async () => {
    await adminApi.updateSettingsBulk(guildId, {
      log_channel_id: verificationLog || null,
      warn_log_channel_id: moderationLog || null,
      spam_log_channel_id: automodLog || null,
      transcript_log_channel_id: transcriptLog || null,
    });
  });

  return (
    <Section icon={Bell} title="Log Channels" description="Central destinations for operational Discord logs. Command execution history remains available under Logs → Commands.">
      <div className="space-y-4">
        {Object.entries(settings.log_channel_health).filter(([, item]) => item.configured && !item.ok).map(([type, item]) => (
          <div key={type} className="flex items-start gap-2 rounded border border-amber-500/20 bg-amber-500/10 px-3 py-2 text-xs text-amber-300">
            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
            <span><strong className="capitalize">{type} log:</strong> {item.error}</span>
          </div>
        ))}
        <FieldRow
          label="Verification Log"
          icon={UserCheck}
          info={LOG_CHANNEL_PRODUCERS.verification}>
          <Picker options={channelOptions} value={verificationLog} onChange={id => setVerificationLog(id ?? "")} placeholder="Select channel…" />
        </FieldRow>
        <FieldRow
          label="Moderation Log"
          icon={Shield}
          info={LOG_CHANNEL_PRODUCERS.moderation}>
          <Picker options={channelOptions} value={moderationLog} onChange={id => setModerationLog(id ?? "")} placeholder="Select channel…" />
        </FieldRow>
        <FieldRow
          label="Automod Log"
          icon={AlertTriangle}
          info={LOG_CHANNEL_PRODUCERS.automod}>
          <Picker options={channelOptions} value={automodLog} onChange={id => setAutomodLog(id ?? "")} placeholder="Select channel…" />
        </FieldRow>
        <FieldRow
          label="Transcript Log"
          icon={FileText}
          info={LOG_CHANNEL_PRODUCERS.transcript}>
          <Picker options={channelOptions} value={transcriptLog} onChange={id => setTranscriptLog(id ?? "")} placeholder="Select channel…" />
        </FieldRow>
        <div className="flex items-center gap-3 pt-1">
          <button onClick={handleSave}
            className="flex items-center gap-2 rounded bg-indigo-600 px-4 py-2 text-xs font-bold text-white hover:bg-indigo-500">
            <Save className="h-4 w-4" /> Save Log Channels
          </button>
          {indicator}
        </div>
      </div>
    </Section>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function Settings() {
  const guildId = useSelectedGuildId();
  const [settings, setSettings] = useState<GuildSettings | null>(null);
  const [roles, setRoles] = useState<DiscordRole[]>([]);
  const [channels, setChannels] = useState<DiscordChannel[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setSettings(null);
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
    ])
      .then(([s, r, c]) => {
        if (cancelled) return;
        setSettings(s);
        setRoles(r);
        setChannels(c);
      })
      .catch(e => {
        if (!cancelled) setError(e?.response?.data?.error ?? e?.message ?? "Failed to load");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [guildId]);

  return (
    <div className="flex flex-col gap-6 md:pl-64">
      <div className="px-4 sm:px-6 pt-5">
        <h1 className="text-2xl font-bold text-zinc-100 tracking-tight">Settings</h1>
        <p className="text-sm text-zinc-500 mt-0.5">Server configuration — changes save instantly.</p>
      </div>

      <div className="px-4 sm:px-6 pb-8">
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
        {!loading && settings && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Left column */}
            <div className="flex flex-col gap-4">
              <RolesSection guildId={guildId} settings={settings} roles={roles} />
              <WarnThresholdsSection guildId={guildId} />
              <AutoMentionsSection guildId={guildId} roles={roles} channels={channels} />
            </div>
            {/* Right column */}
            <div className="flex flex-col gap-4">
              <ManagerRolesSection guildId={guildId} />
              <LogChannelsSection guildId={guildId} settings={settings} channels={channels} />
              <RecapLogsSection guildId={guildId} channels={channels} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
