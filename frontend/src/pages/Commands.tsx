import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { adminApi, apiErrorMessage } from "@/lib/api";
import {
  Loader2, CheckCircle2, AlertCircle,
  Play, SlidersHorizontal, X, Plus, Bell,
  ShieldAlert, ShieldCheck, Wrench, Lock, Hash, Crown, Users, UserX,
  ToggleLeft, ToggleRight, AlertTriangle, ArrowRight, UserMinus, Clock, MessageSquare,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useSelectedGuildId, Toggle, CmdSettingsRow, MultiPicker, LogChannelModal, ChannelPicker } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";
import { ModalOverlay } from "@/components/ui/modal-overlay";
import { NumberStepper } from "@/components/ui/number-stepper";

type WarnThreshold = { warn_limit: number; action: string };

const ACTION_META: Record<string, { label: string; icon: React.ElementType; color: string; bg: string; border: string }> = {
  kick:    { label: "Kick",        icon: UserMinus,     color: "text-orange-300", bg: "bg-orange-500/10", border: "border-orange-500/30" },
  ban:     { label: "Ban",         icon: UserX,         color: "text-red-300",    bg: "bg-red-500/10",    border: "border-red-500/30"    },
  timeout: { label: "Timeout 24h", icon: Clock,         color: "text-amber-300",  bg: "bg-amber-500/10",  border: "border-amber-500/30"  },
  none:    { label: "Warn only",   icon: MessageSquare, color: "text-zinc-400",   bg: "bg-zinc-800",      border: "border-zinc-700"      },
};

// ─── Types ────────────────────────────────────────────────────────────────────

type CmdCategory = "Moderation" | "Verification" | "Utility";

interface CmdDef {
  name: string;
  description: string;
  settingsHref?: string;
  hasSettings?: boolean;
}

type CmdSettingsData = {
  dmUser?: boolean;
  ephemeral?: boolean;
  includeBots?: boolean;
  message?: string;
  allowedRoleIds?: string[];
  approverRoleIds?: string[];
  subcommandEphemeral?: Record<string, boolean>;
};

const DEFAULT_CODE_MESSAGE = "Úspešne overené! Vitaj.";
const DEFAULT_BLOCKED_SUBJECT_MESSAGE = "🚫 Nie je v zozname povolených predmetov: {predmety}";
const DEFAULT_FAQ_MESSAGE = "FAQ nie je nastavené, kontaktuj administrátora.";

/** Per-command label/placeholder/token-hints for the schema.message textarea - it's the same UI, just different content per command. */
const CMD_MESSAGE_FIELD: Record<string, { label: string; tokens: string[] }> = {
  code:         { label: "Success message", tokens: ["{user}", "{server}", "{ais_id}"] },
  pridatpredmet: { label: "Blocked-subject message", tokens: ["{user}", "{server}", "{predmety}"] },
  faq:          { label: "Custom message (optional)", tokens: [] },
};

// ─── Constants ────────────────────────────────────────────────────────────────

/** Commands whose ephemeral behavior differs per subcommand - rendered as a small table in the
 *  Settings modal instead of the single "Ephemeral response" toggle. */
const CMD_SUBCOMMANDS: Record<string, { key: string; label: string }[]> = {
  odpocet: [
    { key: "add", label: "/odpocet add" },
    { key: "list", label: "/odpocet list" },
  ],
  warn: [
    { key: "add", label: "/warn add" },
    { key: "list", label: "/warn list" },
    { key: "remove", label: "/warn remove" },
    { key: "clearall", label: "/warn clearall" },
  ],
};

const CMD_SETTINGS_SCHEMA: Record<string, {
  dmUser?: boolean; ephemeral?: boolean; includeBots?: boolean; message?: boolean;
  allowedRoles?: boolean; approverRoles?: boolean; subcommandEphemeral?: boolean;
}> = {
  warn:         { subcommandEphemeral: true },
  wipe:         { dmUser: true, ephemeral: true },
  verify:       { ephemeral: true },
  code:         { message: true },
  manualverify: { ephemeral: true },
  find:         { ephemeral: true },
  mywarns:      { ephemeral: true },
  info:         { ephemeral: true },
  user:         { ephemeral: true },
  pridatpredmet: { ephemeral: true, message: true, allowedRoles: true, approverRoles: true },
  faq:          { ephemeral: true, message: true },
  odpocet:      { subcommandEphemeral: true },
};

const CMD_SETTINGS_DEFAULTS: Record<string, CmdSettingsData> = {
  warn:         { subcommandEphemeral: { add: false, list: true, remove: false, clearall: false } },
  wipe:         { dmUser: false, ephemeral: false },
  verify:       { ephemeral: true },
  code:         { message: DEFAULT_CODE_MESSAGE },
  manualverify: { ephemeral: true },
  find:     { ephemeral: true },
  mywarns:      { ephemeral: true },
  info:         { ephemeral: true },
  user:         { ephemeral: false },
  pridatpredmet: { ephemeral: true, message: DEFAULT_BLOCKED_SUBJECT_MESSAGE, allowedRoleIds: [], approverRoleIds: [] },
  faq:          { ephemeral: true, message: DEFAULT_FAQ_MESSAGE },
  odpocet:      { subcommandEphemeral: { add: false, list: true } },
};

/** Commands that log to a Discord channel - the "Log Channel" button on the card only shows for these. */
const CMD_LOG_EVENT_TYPES: Record<string, string[]> = {
  warn:         ["WARN_ISSUED", "WARN_REMOVED", "WARNS_CLEARED", "WARN_THRESHOLD_ACTION"],
  manualverify: ["MANUAL_VERIFY_PERFORMED"],
  verify:       ["VERIFY_REQUESTED", "VERIFIED_ROLE_ADDED_WITHOUT_VERIFY", "VERIFIED_USER_REMOVED"],
  code:         ["CODE_CONFIRMED"],
  pridatpredmet: ["SUBJECT_ROLE_PENDING_APPROVAL", "SUBJECT_ROLE_MISSING_ROLE"],
};

/** Commands whose several event types should share one channel picker instead of one row each. */
const CMD_LOG_COMBINED = new Set(["warn", "verify", "pridatpredmet"]);

const CATEGORY_ICONS: Record<CmdCategory, React.ElementType> = {
  Moderation:   ShieldAlert,
  Verification: ShieldCheck,
  Utility:      Wrench,
};

const CMD_CATEGORIES: Record<CmdCategory, CmdDef[]> = {
  Moderation: [
    { name: "/warn",          description: "Manage warnings for a user: add, remove, clearall, list. Settings also configure auto-punishment thresholds and ephemeral response per subcommand.", hasSettings: true },
    { name: "/manualverify",  description: "Manually verify a user by Discord ID and email.", hasSettings: true },
    { name: "/user",          description: "Show detailed Discord info, verification status, and warn history for a member.", hasSettings: true },
  ],
  Verification: [
    { name: "/verify",   description: "Verify yourself as an active FEI student using your AIS ID.", hasSettings: true },
    { name: "/code",     description: "Enter the verification code received via email to complete verification.", hasSettings: true },
    { name: "/find", description: "Find a verified user's record by their AIS ID.", hasSettings: true },
    { name: "/mywarns",  description: "Check your own active warnings on this server.", hasSettings: true },
  ],
  Utility: [
    { name: "/info", description: "Show bot configuration and server information.", hasSettings: true },
    { name: "/pridatpredmet", description: "Self-assign up to 5 subject roles (autocomplete-filtered to the roles allowed in Settings) for a repeated or extra-enrolled subject. First 2 per semester auto-grant, the rest need approval. Refuses to run until allowed subject roles and approvers are both configured below. The per-semester counter resets when a Switch Semester is started (not resumed) on the Semester page - there's no separate manual reset.", hasSettings: true },
    { name: "/odpocet", description: "Countdown to a BP/DP defense date - renames a room's channel name daily (e.g. \"30-dni-do-bp\") until the day arrives, then stops. Manage active counters via the Thesis Countdown module, ephemeral response per subcommand below.", hasSettings: true, settingsHref: "/modules/thesiscounter" },
    { name: "/faq", description: "Ephemeral FAQ reply. The whole message is configured below - write out the commands/info you want students to see.", hasSettings: true },
  ],
};

// ─── Warn thresholds editor (shown inside /warn's settings modal) ────────────

function WarnThresholdsEditor({ guildId }: { guildId: string }) {
  const [thresholds, setThresholds] = useState<WarnThreshold[]>([]);
  const [loading, setLoading] = useState(true);
  const [newLimit, setNewLimit] = useState(1);
  const [newAction, setNewAction] = useState("kick");
  const [adding, setAdding] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    adminApi.getWarnThresholds(guildId).then(setThresholds).finally(() => setLoading(false));
  }, [guildId]);

  const add = async () => {
    const limit = newLimit;
    setAdding(true);
    try {
      await adminApi.addWarnThreshold(guildId, limit, newAction);
      setThresholds(await adminApi.getWarnThresholds(guildId));
      setNewLimit(1);
      toast("Warn threshold added.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to add warn threshold."), "error");
    } finally {
      setAdding(false);
    }
  };

  const remove = async (limit: number) => {
    try {
      await adminApi.removeWarnThreshold(guildId, limit);
      setThresholds(t => t.filter(x => x.warn_limit !== limit));
      toast("Warn threshold removed.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to remove warning threshold."), "error");
    }
  };

  return (
    <div className="rounded-lg border border-zinc-700 overflow-hidden">
      <div className="p-3 bg-zinc-800/50">
        <p className="text-sm font-bold text-zinc-200">Warn Thresholds</p>
        <p className="text-xs text-zinc-500 mt-0.5">Auto-punishment when a user reaches a warn count.</p>
      </div>
      <div className="p-3 border-t border-zinc-700/50 space-y-3">
        {loading ? (
          <Loader2 className="w-4 h-4 animate-spin text-zinc-500" />
        ) : (
          <>
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
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// ─── Command settings modal ───────────────────────────────────────────────────

function CommandSettingsModal({ title, commandKey, guildId, roles, channels, onClose }: {
  title: string;
  commandKey: string;
  guildId: string;
  roles: { id: string; name: string; color?: string }[];
  channels: { id: string; name: string }[];
  onClose: () => void;
}) {
  const schema = CMD_SETTINGS_SCHEMA[commandKey] ?? {};
  const defaults = CMD_SETTINGS_DEFAULTS[commandKey] ?? {};
  const messageField = CMD_MESSAGE_FIELD[commandKey] ?? { label: "Message", tokens: [] };

  const [dmUser, setDmUser] = useState(defaults.dmUser ?? false);
  const [ephemeral, setEphemeral] = useState(defaults.ephemeral ?? false);
  const [includeBots, setIncludeBots] = useState(defaults.includeBots ?? false);
  const [message, setMessage] = useState(defaults.message ?? "");
  const [allowedRoleIds, setAllowedRoleIds] = useState<string[]>(defaults.allowedRoleIds ?? []);
  const [approverRoleIds, setApproverRoleIds] = useState<string[]>(defaults.approverRoleIds ?? []);
  const [subcommandEphemeral, setSubcommandEphemeral] = useState<Record<string, boolean>>(defaults.subcommandEphemeral ?? {});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const savedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const messageRef = useRef<HTMLTextAreaElement>(null);
  const { toast } = useToast();

  useEffect(() => () => {
    if (savedTimerRef.current !== null) clearTimeout(savedTimerRef.current);
  }, []);

  useEffect(() => {
    adminApi.getCommandSettings(guildId, commandKey)
      .then((d: Record<string, unknown>) => {
        const data = d as CmdSettingsData;
        if (schema.dmUser)      setDmUser(data.dmUser ?? defaults.dmUser ?? false);
        if (schema.ephemeral)   setEphemeral(data.ephemeral ?? defaults.ephemeral ?? false);
        if (schema.includeBots) setIncludeBots(data.includeBots ?? defaults.includeBots ?? false);
        if (schema.message)     setMessage(data.message ?? defaults.message ?? "");
        if (schema.allowedRoles) setAllowedRoleIds(data.allowedRoleIds ?? defaults.allowedRoleIds ?? []);
        if (schema.approverRoles) setApproverRoleIds(data.approverRoleIds ?? defaults.approverRoleIds ?? []);
        if (schema.subcommandEphemeral) setSubcommandEphemeral({ ...defaults.subcommandEphemeral, ...data.subcommandEphemeral });
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [
    guildId,
    commandKey,
    schema.dmUser,
    schema.ephemeral,
    schema.includeBots,
    schema.message,
    schema.allowedRoles,
    schema.approverRoles,
    schema.subcommandEphemeral,
    defaults.dmUser,
    defaults.ephemeral,
    defaults.includeBots,
    defaults.message,
    defaults.allowedRoleIds,
    defaults.approverRoleIds,
    defaults.subcommandEphemeral,
  ]);

  /** Inserts a {channel=<id>} token at the cursor (or end, if the textarea isn't focused) - lets one
   *  message reference any number of different channels, each resolved to its own clickable mention. */
  const insertChannelToken = (channelId: string | null) => {
    if (!channelId) return;
    const token = `{channel=${channelId}}`;
    const el = messageRef.current;
    const start = el?.selectionStart ?? message.length;
    const end = el?.selectionEnd ?? message.length;
    const next = message.slice(0, start) + token + message.slice(end);
    setMessage(next);
    requestAnimationFrame(() => {
      el?.focus();
      el?.setSelectionRange(start + token.length, start + token.length);
    });
  };

  const save = async () => {
    setSaving(true);
    const data: CmdSettingsData = {};
    if (schema.dmUser)      data.dmUser = dmUser;
    if (schema.ephemeral)   data.ephemeral = ephemeral;
    if (schema.includeBots) data.includeBots = includeBots;
    if (schema.message)     data.message = message;
    if (schema.allowedRoles) data.allowedRoleIds = allowedRoleIds;
    if (schema.approverRoles) data.approverRoleIds = approverRoleIds;
    if (schema.subcommandEphemeral) data.subcommandEphemeral = subcommandEphemeral;
    try {
      await adminApi.saveCommandSettings(guildId, commandKey, data as Record<string, unknown>);
      setSaved(true);
      toast("Command settings saved.");
      if (savedTimerRef.current !== null) clearTimeout(savedTimerRef.current);
      savedTimerRef.current = setTimeout(() => setSaved(false), 2000);
    } catch {
      toast("Failed to save command settings.", "error");
    } finally { setSaving(false); }
  };

  return (
    <ModalOverlay onClose={onClose} panelClassName="w-full max-w-lg overflow-hidden">
      <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-800">
        <h2 className="text-lg font-bold text-zinc-100">{title}</h2>
        <button onClick={onClose} className="text-zinc-500 hover:text-zinc-200 transition-colors"><X className="w-5 h-5" /></button>
      </div>

      {loading ? (
        <div className="flex items-center gap-2 px-6 py-8 text-zinc-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</div>
      ) : (
        <div className="px-6 py-5 space-y-5 max-h-[70vh] overflow-y-auto scrollbar-thin">
          {commandKey === "warn" && <WarnThresholdsEditor guildId={guildId} />}
          {schema.ephemeral && (
            <CmdSettingsRow label="Ephemeral response" hint="Only visible to the user who ran the command">
              <Toggle enabled={ephemeral} onChange={setEphemeral} />
            </CmdSettingsRow>
          )}
          {schema.subcommandEphemeral && (
            <div>
              <p className="text-sm font-bold text-zinc-200">Ephemeral response</p>
              <p className="text-xs text-zinc-500 mt-0.5 mb-2">Only visible to the user who ran the command - set per subcommand.</p>
              <div className="rounded-lg border border-zinc-700 divide-y divide-zinc-700/50 overflow-hidden">
                {(CMD_SUBCOMMANDS[commandKey] ?? []).map(sc => (
                  <div key={sc.key} className="flex items-center justify-between gap-3 px-3 py-2 bg-zinc-800/60">
                    <span className="text-xs font-mono text-zinc-300">{sc.label}</span>
                    <Toggle
                      enabled={subcommandEphemeral[sc.key] ?? false}
                      onChange={v => setSubcommandEphemeral(s => ({ ...s, [sc.key]: v }))}
                    />
                  </div>
                ))}
              </div>
            </div>
          )}
          {schema.dmUser && (
            <div className="space-y-3">
              <CmdSettingsRow
                label={commandKey === 'wipe' ? 'DM users before wipe' : 'DM the user'}
                hint={commandKey === 'wipe' ? 'Send a DM to each user before removing them' : 'Send a DM to the target user'}>
                <Toggle enabled={dmUser} onChange={setDmUser} />
              </CmdSettingsRow>
            </div>
          )}
          {schema.includeBots && (
            <CmdSettingsRow label="Include bots" hint="Include bot accounts in the results">
              <Toggle enabled={includeBots} onChange={setIncludeBots} />
            </CmdSettingsRow>
          )}
          {schema.message && (
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <p className="text-xs font-semibold text-zinc-400">{messageField.label}</p>
                <div className="w-64">
                  <ChannelPicker channels={channels} value={null} onChange={insertChannelToken} placeholder="+ Insert channel" />
                </div>
              </div>
              <textarea
                ref={messageRef}
                value={message}
                onChange={e => setMessage(e.target.value)}
                rows={3}
                placeholder={defaults.message ?? ""}
                className="w-full resize-none px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors scrollbar-thin"
              />
              <p className="text-[11px] text-zinc-600 mt-1">
                Placeholders: {messageField.tokens.map(t => <span key={t} className="font-mono">{t} </span>)}
                For channels, pick one from "Insert channel" above (as many times/channels as you want) - it inserts a <span className="font-mono">{"{channel=id}"}</span> token
                which renders as a clickable link to that channel, wherever you place it in the text.
              </p>
            </div>
          )}
          {schema.allowedRoles && (
            <div>
              <p className="text-sm font-bold text-zinc-200">Allowed subject roles</p>
              <p className="text-xs text-zinc-500 mt-0.5 mb-2">
                Only these roles can be self-assigned via /pridatpredmet - everything else is blocked. Also filters what the command's autocomplete suggests. Empty = nothing is allowed yet.
              </p>
              <MultiPicker options={roles} selected={allowedRoleIds} onChange={setAllowedRoleIds} placeholder="Select Role" />
            </div>
          )}
          {schema.approverRoles && (
            <div>
              <p className="text-sm font-bold text-zinc-200">Who can approve/reject requests</p>
              <p className="text-xs text-zinc-500 mt-0.5 mb-2">
                Members with one of these roles (plus server Administrators) can approve or reject 3rd+ requests. Empty = only Administrators can.
              </p>
              <MultiPicker options={roles} selected={approverRoleIds} onChange={setApproverRoleIds} placeholder="Select Role" />
            </div>
          )}
          {schema.allowedRoles && schema.approverRoles && (allowedRoleIds.length === 0 || approverRoleIds.length === 0) && (
            <p className="text-xs text-amber-400 bg-amber-400/10 border border-amber-400/20 rounded px-3 py-2">
              /pridatpredmet will refuse to run until both allowed subject roles and approvers are set.
            </p>
          )}
        </div>
      )}

      <div className="px-6 py-4 border-t border-zinc-800 flex items-center gap-3">
        <button onClick={save} disabled={saving || loading}
          className="flex items-center gap-2 px-5 py-2 rounded text-sm font-bold bg-indigo-600 hover:bg-indigo-500 text-white transition-colors disabled:opacity-50">
          {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : saved ? <CheckCircle2 className="w-4 h-4" /> : null}
          {saved ? "Saved!" : "Save"}
        </button>
        <button onClick={onClose} className="px-4 py-2 rounded text-sm text-zinc-400 hover:text-zinc-200 transition-colors">Cancel</button>
      </div>
    </ModalOverlay>
  );
}

// ─── Permissions modal ────────────────────────────────────────────────────────

function PermSection({ label, hint, active, onClear, children }: {
  label: string; hint: string; active: boolean; onClear: () => void; children: React.ReactNode;
}) {
  const [open, setOpen] = useState(active);
  useEffect(() => { if (active) setOpen(true); }, [active]);
  return (
    <div className="rounded-lg border border-zinc-700 overflow-hidden">
      <label className="flex items-center justify-between gap-3 p-3 bg-zinc-800/50 cursor-pointer hover:bg-zinc-800 transition-colors"
        onClick={() => { if (open && active) onClear(); setOpen(v => !v); }}>
        <div>
          <p className="text-sm font-bold text-zinc-200">{label}</p>
          <p className="text-xs text-zinc-500 mt-0.5">{hint}</p>
        </div>
        <div className={cn("w-9 h-5 rounded-full transition-colors flex items-center flex-shrink-0",
          open ? "bg-rose-600" : "bg-zinc-600")}>
          <div className={cn("w-4 h-4 rounded-full bg-white shadow transition-transform mx-0.5",
            open ? "translate-x-4" : "translate-x-0")} />
        </div>
      </label>
      {open && <div className="p-3 border-t border-zinc-700/50">{children}</div>}
    </div>
  );
}

function PermissionsModal({ title, note, guildId, commandKey, commandKeys, roles, channels, onClose, onSaved }: {
  title: string;
  note?: string;
  guildId: string;
  commandKey: string;
  commandKeys?: string[];
  roles: { id: string; name: string; color?: string }[];
  channels: { id: string; name: string }[];
  onClose: () => void;
  onSaved?: () => void;
}) {
  const [allowedChannels, setAllowedChannels] = useState<string[]>([]);
  const [ignoredChannels, setIgnoredChannels] = useState<string[]>([]);
  const [allowedRoles, setAllowedRoles] = useState<string[]>([]);
  const [ignoredRoles, setIgnoredRoles] = useState<string[]>([]);
  const [adminOnly, setAdminOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [mixedPermissions, setMixedPermissions] = useState(false);
  const savedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const permissionKeysKey = (commandKeys ?? [commandKey]).join("\0");
  const isBulkPermissionEdit = Boolean(commandKeys);
  const { toast } = useToast();

  useEffect(() => () => {
    if (savedTimerRef.current !== null) clearTimeout(savedTimerRef.current);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setMixedPermissions(false);
    const keys = permissionKeysKey.split("\0").filter(Boolean);
    if (keys.length === 0) { setLoading(false); return () => { cancelled = true; }; }
    Promise.all(keys.map(key => adminApi.getCommandPermissions(guildId, key)))
      .then(results => {
        if (cancelled) return;
        const normalize = (p: typeof results[number]) => JSON.stringify({
          allowedChannels: p.allowedChannels ?? [],
          ignoredChannels: p.ignoredChannels ?? [],
          allowedRoles: p.allowedRoles ?? [],
          ignoredRoles: p.ignoredRoles ?? [],
          adminOnly: p.adminOnly ?? false,
        });
        const first = results[0];
        const allSame = results.every(result => normalize(result) === normalize(first));
        if (isBulkPermissionEdit && !allSame) {
          setAllowedChannels([]);
          setIgnoredChannels([]);
          setAllowedRoles([]);
          setIgnoredRoles([]);
          setAdminOnly(false);
          setMixedPermissions(true);
          return;
        }
        setAllowedChannels(first.allowedChannels);
        setIgnoredChannels(first.ignoredChannels);
        setAllowedRoles(first.allowedRoles);
        setIgnoredRoles(first.ignoredRoles);
        setAdminOnly(first.adminOnly ?? false);
      })
      .catch(error => { if (!cancelled) console.error(error); })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [guildId, commandKey, permissionKeysKey, isBulkPermissionEdit]);

  const save = async () => {
    setSaving(true);
    const perms = { allowedChannels, ignoredChannels, allowedRoles, ignoredRoles, adminOnly };
    try {
      const keys = commandKeys ?? [commandKey];
      await Promise.all(keys.map(k => adminApi.saveCommandPermissions(guildId, k, perms)));
      setSaved(true);
      setMixedPermissions(false);
      onSaved?.();
      toast("Permissions saved.");
      if (savedTimerRef.current !== null) clearTimeout(savedTimerRef.current);
      savedTimerRef.current = setTimeout(() => setSaved(false), 2000);
    } catch {
      toast("Failed to save permissions.", "error");
    } finally { setSaving(false); }
  };

  return (
    <ModalOverlay onClose={onClose} panelClassName="w-full max-w-lg overflow-hidden">
      <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-800">
        <h2 className="text-lg font-bold text-zinc-100">{title}</h2>
        <button onClick={onClose} className="text-zinc-500 hover:text-zinc-200 transition-colors"><X className="w-5 h-5" /></button>
      </div>

      {loading ? (
        <div className="flex items-center gap-2 px-6 py-8 text-zinc-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</div>
      ) : (
        <div className="px-6 py-5 space-y-4 max-h-[70vh] overflow-y-auto scrollbar-thin">
          {note && <p className="text-xs text-amber-400 bg-amber-400/10 border border-amber-400/20 rounded px-3 py-2">{note}</p>}
          {mixedPermissions && (
            <p className="text-xs text-red-300 bg-red-500/10 border border-red-500/20 rounded px-3 py-2">
              This category currently has mixed per-command permissions. The form starts empty so saving is an explicit overwrite for every command in this category.
            </p>
          )}

          <label className="flex items-center justify-between gap-3 p-3 rounded-lg border border-zinc-700 bg-zinc-800/50 cursor-pointer hover:border-zinc-600 transition-colors">
            <div>
              <p className="text-sm font-bold text-zinc-200">Admin only</p>
              <p className="text-xs text-zinc-500 mt-0.5">Only users with Administrator permission can use this command</p>
            </div>
            <Toggle enabled={adminOnly} onChange={setAdminOnly} />
          </label>

          <PermSection label="Require specific roles" hint="Only members with one of these roles can use this command" active={allowedRoles.length > 0} onClear={() => setAllowedRoles([])}>
            <MultiPicker options={roles} selected={allowedRoles} onChange={setAllowedRoles} placeholder="Select Role" />
          </PermSection>

          <PermSection label="Block specific roles" hint="Members with any of these roles are blocked" active={ignoredRoles.length > 0} onClear={() => setIgnoredRoles([])}>
            <MultiPicker options={roles} selected={ignoredRoles} onChange={setIgnoredRoles} placeholder="Select Role" />
          </PermSection>

          <PermSection label="Allowed channels" hint="Command can only be used in these channels" active={allowedChannels.length > 0} onClear={() => setAllowedChannels([])}>
            <MultiPicker options={channels} selected={allowedChannels} onChange={setAllowedChannels} placeholder="Select Channel" />
          </PermSection>

          <PermSection label="Blocked channels" hint="Command cannot be used in these channels" active={ignoredChannels.length > 0} onClear={() => setIgnoredChannels([])}>
            <MultiPicker options={channels} selected={ignoredChannels} onChange={setIgnoredChannels} placeholder="Select Channel" />
          </PermSection>
        </div>
      )}

      <div className="px-6 py-4 border-t border-zinc-800 flex items-center gap-3">
        <button onClick={save} disabled={saving || loading}
          className="flex items-center gap-2 px-5 py-2 rounded text-sm font-bold bg-emerald-600 hover:bg-emerald-500 text-white transition-colors disabled:opacity-50">
          {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : saved ? <CheckCircle2 className="w-4 h-4" /> : null}
          {saved ? "Saved!" : "Save"}
        </button>
        <button onClick={onClose} className="px-4 py-2 rounded text-sm text-zinc-400 hover:text-zinc-200 transition-colors">Cancel</button>
      </div>
    </ModalOverlay>
  );
}

// ─── Command card ─────────────────────────────────────────────────────────────

function CmdCard({ cmd, guildId, roles, channels, enabled, onToggle, allowedRoleIds, ignoredRoleIds, allowedChannelIds, ignoredChannelIds, adminOnly, onPermsSaved }: {
  cmd: CmdDef;
  guildId: string | null;
  roles: { id: string; name: string; color?: string }[];
  channels: { id: string; name: string }[];
  enabled: boolean;
  onToggle: (name: string, value: boolean) => void;
  allowedRoleIds?: string[];
  ignoredRoleIds?: string[];
  allowedChannelIds?: string[];
  ignoredChannelIds?: string[];
  adminOnly?: boolean;
  onPermsSaved?: () => void;
}) {
  const [modal, setModal] = useState<"auth" | "settings" | "logs" | null>(null);
  const [toggling, setToggling] = useState(false);
  const { toast } = useToast();
  const commandKey = cmd.name.replace("/", "");
  const logEventTypes = CMD_LOG_EVENT_TYPES[commandKey];

  const handleToggle = async (v: boolean) => {
    if (!guildId || toggling) return;
    setToggling(true);
    onToggle(cmd.name, v);
    try {
      await adminApi.setCommandEnabled(guildId, cmd.name, v);
      toast(`${cmd.name} ${v ? "enabled" : "disabled"}.`);
    } catch {
      onToggle(cmd.name, !v);
      toast(`Failed to ${v ? "enable" : "disable"} ${cmd.name}.`, "error");
    } finally { setToggling(false); }
  };

  const hasPerms = adminOnly
    || (allowedRoleIds?.length ?? 0) > 0
    || (ignoredRoleIds?.length ?? 0) > 0
    || (allowedChannelIds?.length ?? 0) > 0
    || (ignoredChannelIds?.length ?? 0) > 0;

  const allowedRoleNames = allowedRoleIds?.map(id => roles.find(r => r.id === id)?.name).filter(Boolean) as string[];
  const ignoredRoleNames = ignoredRoleIds?.map(id => roles.find(r => r.id === id)?.name).filter(Boolean) as string[];
  const allowedChannelNames = allowedChannelIds?.map(id => channels.find(c => c.id === id)?.name).filter(Boolean) as string[];
  const ignoredChannelNames = ignoredChannelIds?.map(id => channels.find(c => c.id === id)?.name).filter(Boolean) as string[];

  return (
    <div className={cn("flex flex-col rounded-lg border bg-zinc-800/50 transition-colors p-4",
      enabled ? "border-zinc-700/60 hover:border-zinc-600" : "border-zinc-800 opacity-60")}>
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex items-center gap-1.5 min-w-0">
          <p className="font-bold text-zinc-100 text-sm font-mono truncate">{cmd.name}</p>
          {hasPerms && (
            <span title="Has permission restrictions" className="flex-shrink-0 flex items-center justify-center w-4 h-4 rounded bg-amber-500/20 border border-amber-500/40">
              <Lock className="w-2.5 h-2.5 text-amber-400" />
            </span>
          )}
        </div>
        <Toggle enabled={enabled} onChange={handleToggle} disabled={!guildId || toggling} />
      </div>
      <p className="text-xs text-zinc-500 leading-relaxed">{cmd.description}</p>
      {hasPerms && (
        <div className="flex items-center gap-1.5 mt-2 flex-wrap">
          {adminOnly && (
            <span className="inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded bg-amber-500/15 border border-amber-500/30 text-amber-300 font-semibold">
              <Crown className="w-3 h-3" /> Admin only
            </span>
          )}
          {allowedRoleNames?.length > 0 && (
            <>
              <Users className="w-3 h-3 text-emerald-400 flex-shrink-0" />
              {allowedRoleNames.map(name => (
                <span key={name} className="text-xs px-1.5 py-0.5 rounded bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 font-mono">
                  @{name}
                </span>
              ))}
            </>
          )}
          {ignoredRoleNames?.length > 0 && (
            <>
              <UserX className="w-3 h-3 text-red-400 flex-shrink-0" />
              {ignoredRoleNames.map(name => (
                <span key={name} className="text-xs px-1.5 py-0.5 rounded bg-red-500/15 border border-red-500/30 text-red-300 font-mono">
                  @{name}
                </span>
              ))}
            </>
          )}
          {allowedChannelNames?.length > 0 && (
            <>
              <Hash className="w-3 h-3 text-emerald-400 flex-shrink-0" />
              {allowedChannelNames.map(name => (
                <span key={name} className="text-xs px-1.5 py-0.5 rounded bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 font-mono">
                  #{name}
                </span>
              ))}
            </>
          )}
          {ignoredChannelNames?.length > 0 && (
            <>
              <Hash className="w-3 h-3 text-red-400 flex-shrink-0" />
              {ignoredChannelNames.map(name => (
                <span key={name} className="text-xs px-1.5 py-0.5 rounded bg-red-500/15 border border-red-500/30 text-red-300 font-mono">
                  #{name}
                </span>
              ))}
            </>
          )}
        </div>
      )}

      <div className="flex gap-2 mt-3 pt-3 border-t border-zinc-700/50 flex-wrap">
        <button onClick={() => setModal("auth")} disabled={!guildId}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider bg-rose-700/80 hover:bg-rose-600 text-white transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
          <ShieldCheck className="w-3 h-3" /> Authorization
        </button>
        {cmd.hasSettings && (
          <button onClick={() => setModal("settings")} disabled={!guildId}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider bg-indigo-700/80 hover:bg-indigo-600 text-white transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
            <SlidersHorizontal className="w-3 h-3" /> Settings
          </button>
        )}
        {cmd.settingsHref && (
          <Link to={cmd.settingsHref}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider bg-zinc-700/80 hover:bg-zinc-600 text-white transition-colors">
            <SlidersHorizontal className="w-3 h-3" /> Manage Countdowns
          </Link>
        )}
        {logEventTypes && (
          <button onClick={() => setModal("logs")} disabled={!guildId}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider bg-zinc-700/80 hover:bg-zinc-600 text-white transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
            <Bell className="w-3 h-3" /> Log Channel
          </button>
        )}
      </div>

      {modal === "logs" && guildId && logEventTypes && (
        <LogChannelModal
          title={`Log Channel (${cmd.name})`}
          eventTypes={logEventTypes}
          guildId={guildId}
          onClose={() => setModal(null)}
          combined={CMD_LOG_COMBINED.has(commandKey)}
        />
      )}

      {modal === "auth" && guildId && (
        <PermissionsModal
          title={`Authorization (${cmd.name})`}
          guildId={guildId}
          commandKey={commandKey}
          roles={roles}
          channels={channels}
          onClose={() => setModal(null)}
          onSaved={onPermsSaved}
        />
      )}
      {modal === "settings" && guildId && (
        <CommandSettingsModal
          title={`Settings (${cmd.name})`}
          commandKey={commandKey}
          guildId={guildId}
          roles={roles}
          channels={channels}
          onClose={() => setModal(null)}
        />
      )}
    </div>
  );
}

function CategoryAuthButton({ activeTab, permsMap, guildId, onClick }: {
  activeTab: CmdCategory;
  permsMap: Map<string, { allowedRoles: string[]; ignoredRoles: string[]; allowedChannels: string[]; ignoredChannels: string[]; adminOnly: boolean }>;
  guildId: string | null;
  onClick: () => void;
}) {
  const catHasPerms = CMD_CATEGORIES[activeTab].some(cmd => permsMap.has(cmd.name.replace("/", "")));
  return (
    <button onClick={onClick} disabled={!guildId}
      title={`${activeTab} category settings`}
      className={cn("flex items-center gap-1.5 mb-1 px-3 py-1.5 rounded text-xs font-bold transition-all disabled:opacity-30 disabled:cursor-not-allowed",
        catHasPerms
          ? "text-amber-400 bg-amber-500/10 border border-amber-500/30 hover:bg-amber-500/20"
          : "text-zinc-400 hover:text-rose-400 hover:bg-zinc-800")}>
      <ShieldCheck className="w-3.5 h-3.5" /> Authorization
      {catHasPerms && <Lock className="w-3 h-3" />}
    </button>
  );
}

// ─── Commands page (/commands) ────────────────────────────────────────────────

export function Commands() {
  const guildId = useSelectedGuildId();
  const [activeTab, setActiveTab] = useState<CmdCategory>("Moderation");
  const [roles, setRoles] = useState<{ id: string; name: string; color: string }[]>([]);
  const [channels, setChannels] = useState<{ id: string; name: string }[]>([]);
  const [categoryModal, setCategoryModal] = useState(false);
  const [cmdStates, setCmdStates] = useState<Record<string, boolean> | null>(null);
  const [permsMap, setPermsMap] = useState<Map<string, { allowedRoles: string[]; ignoredRoles: string[]; allowedChannels: string[]; ignoredChannels: string[]; adminOnly: boolean }>>(new Map());
  const [deploying, setDeploying] = useState(false);
  const [deployed, setDeployed] = useState(false);
  const [togglingAll, setTogglingAll] = useState(false);
  const deployTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { toast } = useToast();

  useEffect(() => () => {
    if (deployTimerRef.current !== null) clearTimeout(deployTimerRef.current);
  }, []);

  const buildPermsMap = (data: { command: string; allowedRoles: string[]; ignoredRoles: string[]; allowedChannels: string[]; ignoredChannels: string[]; adminOnly: boolean }[]) =>
    new Map(data.map(d => [d.command, { allowedRoles: d.allowedRoles, ignoredRoles: d.ignoredRoles, allowedChannels: d.allowedChannels, ignoredChannels: d.ignoredChannels, adminOnly: d.adminOnly }]));

  useEffect(() => {
    let cancelled = false;
    setRoles([]);
    setChannels([]);
    setCmdStates(null);
    setPermsMap(new Map());
    setCategoryModal(false);
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
      adminApi.getCommandStates(guildId),
    ]).then(([r, c, s]) => {
      if (cancelled) return;
      setRoles(r);
      setChannels(c);
      setCmdStates(s);
    }).catch(error => { if (!cancelled) console.error(error); });
    adminApi.getCommandPermissionsSummary(guildId)
      .then(data => { if (!cancelled) setPermsMap(buildPermsMap(data)); })
      .catch(error => { if (!cancelled) console.error(error); });
    return () => { cancelled = true; };
  }, [guildId]);

  const handleToggle = (name: string, value: boolean) => {
    setCmdStates(prev => ({ ...prev, [name]: value }));
  };

  const refreshPerms = () => {
    if (guildId) adminApi.getCommandPermissionsSummary(guildId).then(data => setPermsMap(buildPermsMap(data))).catch(console.error);
  };

  const handleToggleAll = async (value: boolean) => {
    if (!guildId || togglingAll || !cmdStates) return;
    setTogglingAll(true);
    const cmds = CMD_CATEGORIES[activeTab];
    const oldStates = { ...cmdStates };
    const patch: Record<string, boolean> = {};
    cmds.forEach(cmd => { patch[cmd.name] = value; });
    setCmdStates(prev => ({ ...prev, ...patch }));
    try {
      await adminApi.setCommandStatesBulk(guildId, patch);
      toast(`${activeTab} commands ${value ? "enabled" : "disabled"}.`);
    } catch {
      setCmdStates(oldStates);
      toast(`Failed to ${value ? "enable" : "disable"} ${activeTab} commands.`, "error");
    } finally {
      setTogglingAll(false);
    }
  };

  const deployToDiscord = async () => {
    if (!guildId || deploying) return;
    setDeploying(true);
    try {
      await adminApi.deployCommands(guildId);
      setDeployed(true);
      toast("Commands deployed to Discord.");
      if (deployTimerRef.current !== null) clearTimeout(deployTimerRef.current);
      deployTimerRef.current = setTimeout(() => setDeployed(false), 3000);
    } catch (e) {
      console.error(e);
      toast("Failed to deploy commands to Discord.", "error");
    } finally { setDeploying(false); }
  };

  const TABS = Object.keys(CMD_CATEGORIES) as CmdCategory[];

  return (
    <div className="flex flex-col gap-0 md:pl-64">
      <div className="px-4 sm:px-6 pt-5 pb-4 border-b border-zinc-800">
        <h1 className="text-2xl font-bold text-zinc-100 tracking-tight">Commands</h1>
      </div>

      <div className="px-4 sm:px-6 pt-4 border-b border-zinc-800 flex items-center justify-between">
        <div className="flex gap-1">
          {TABS.map(tab => {
            const TabIcon = CATEGORY_ICONS[tab];
            return (
              <button key={tab} onClick={() => { setActiveTab(tab); setCategoryModal(false); }}
                className={cn("flex items-center gap-1.5 px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-all -mb-px",
                  activeTab === tab ? "border-rose-400 text-rose-400" : "border-transparent text-zinc-500 hover:text-zinc-300")}>
                <TabIcon className="w-3.5 h-3.5" />{tab}
              </button>
            );
          })}
        </div>
        <div className="flex items-center gap-2">
          {(() => {
            const allEnabled = cmdStates !== null && CMD_CATEGORIES[activeTab].every(cmd => cmdStates[cmd.name] !== false);
            return (
              <button
                onClick={() => handleToggleAll(!allEnabled)}
                disabled={!guildId || togglingAll || cmdStates === null}
                title={allEnabled ? `Disable all ${activeTab} commands` : `Enable all ${activeTab} commands`}
                className="flex items-center gap-1.5 mb-1 px-3 py-1.5 rounded text-xs font-bold transition-all disabled:opacity-30 disabled:cursor-not-allowed text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800">
                {togglingAll
                  ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  : allEnabled
                    ? <ToggleRight className="w-3.5 h-3.5 text-emerald-400" />
                    : <ToggleLeft className="w-3.5 h-3.5" />}
                {allEnabled ? "Disable All" : "Enable All"}
              </button>
            );
          })()}
          <CategoryAuthButton activeTab={activeTab} permsMap={permsMap} guildId={guildId} onClick={() => setCategoryModal(true)} />
          <button onClick={deployToDiscord} disabled={!guildId || deploying}
            title="Sync command visibility settings to Discord for this server"
            className="flex items-center gap-1.5 mb-1 px-3 py-1.5 rounded text-xs font-bold transition-all disabled:opacity-30 disabled:cursor-not-allowed text-indigo-400 hover:text-indigo-300 hover:bg-zinc-800">
            {deploying ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : deployed ? <CheckCircle2 className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
            {deployed ? "Synced!" : "Sync Visibility"}
          </button>
        </div>
      </div>

      <div className="px-4 sm:px-6 py-5">
        {!guildId && (
          <div className="flex items-center gap-2 text-zinc-500 text-sm p-4 bg-zinc-800/40 border border-zinc-700 rounded-lg mb-4">
            <AlertCircle className="w-4 h-4" /> No server selected. <Link to="/select-server" className="text-indigo-400 hover:text-indigo-300 underline ml-1">Pick one</Link>.
          </div>
        )}
        {cmdStates === null && guildId ? (
          <div className="flex items-center gap-2 text-zinc-500 text-sm py-4">
            <Loader2 className="w-4 h-4 animate-spin" /> Loading…
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {CMD_CATEGORIES[activeTab].map(cmd => {
              const key = cmd.name.replace("/", "");
              const cmdPerms = permsMap.get(key);
              return (
                <CmdCard key={cmd.name} cmd={cmd} guildId={guildId || null} roles={roles} channels={channels}
                  enabled={cmdStates ? cmdStates[cmd.name] !== false : true}
                  onToggle={handleToggle}
                  allowedRoleIds={cmdPerms?.allowedRoles ?? []}
                  ignoredRoleIds={cmdPerms?.ignoredRoles ?? []}
                  allowedChannelIds={cmdPerms?.allowedChannels ?? []}
                  ignoredChannelIds={cmdPerms?.ignoredChannels ?? []}
                  adminOnly={cmdPerms?.adminOnly ?? false}
                  onPermsSaved={refreshPerms} />
              );
            })}
          </div>
        )}
      </div>

      {categoryModal && guildId && (
        <PermissionsModal
          title={`Additional Permissions (${activeTab})`}
          note={`This will overwrite all existing ${activeTab} command permissions.`}
          guildId={guildId}
          commandKey=""
          commandKeys={CMD_CATEGORIES[activeTab].map(cmd => cmd.name.replace("/", ""))}
          roles={roles}
          channels={channels}
          onClose={() => setCategoryModal(false)}
          onSaved={refreshPerms}
        />
      )}
    </div>
  );
}
