import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { adminApi } from "@/lib/api";
import {
  Loader2, CheckCircle2, AlertCircle,
  Play, SlidersHorizontal, X,
  ShieldAlert, ShieldCheck, Wrench, Lock, Hash, Crown, Users, UserX,
  ToggleLeft, ToggleRight,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useSelectedGuildId, Toggle, CmdSettingsRow, MultiPicker } from "@/components/modules/shared";

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
};


// ─── Constants ────────────────────────────────────────────────────────────────

const CMD_SETTINGS_SCHEMA: Record<string, {
  dmUser?: boolean; ephemeral?: boolean; includeBots?: boolean;
}> = {
  warn:         { ephemeral: true },
  warns:        { ephemeral: true },
  removewarn:   { ephemeral: true },
  clearwarns:   { ephemeral: true },
  exportrole:   { ephemeral: true, includeBots: true },
  wipe:         { dmUser: true, ephemeral: true },
  verify:       { ephemeral: true },
  code:         {},
  manualverify: { ephemeral: true },
  find:         { ephemeral: true },
  mywarns:      { ephemeral: true },
  info:         { ephemeral: true },
  serverinfo:   { ephemeral: true },
  user:         { ephemeral: true },
  say:          { ephemeral: true },
};

const CMD_SETTINGS_DEFAULTS: Record<string, CmdSettingsData> = {
  warn:         { ephemeral: false },
  warns:        { ephemeral: true },
  removewarn:   { ephemeral: false },
  clearwarns:   { ephemeral: false },
  exportrole:   { ephemeral: false, includeBots: false },
  wipe:         { dmUser: false, ephemeral: false },
  verify:       { ephemeral: true },
  code:         {},
  manualverify: { ephemeral: true },
  find:     { ephemeral: true },
  mywarns:      { ephemeral: true },
  info:         { ephemeral: true },
  serverinfo:   { ephemeral: false },
  user:         { ephemeral: false },
  say:          { ephemeral: true },
};

const CATEGORY_ICONS: Record<CmdCategory, React.ElementType> = {
  Moderation:   ShieldAlert,
  Verification: ShieldCheck,
  Utility:      Wrench,
};

const CMD_CATEGORIES: Record<CmdCategory, CmdDef[]> = {
  Moderation: [
    { name: "/warn",          description: "Warn a member with an optional reason. Logged to the warn log channel.", hasSettings: true },
    { name: "/warns",         description: "View all active warnings for a specific user.", hasSettings: true },
    { name: "/removewarn",    description: "Remove a specific warning from a user by its ID.", hasSettings: true },
    { name: "/clearwarns",    description: "Clear all warnings for a user at once.", hasSettings: true },
    { name: "/exportrole",    description: "Export server members with a specific role, with optional role-only filter.", hasSettings: true },
    { name: "/manualverify",  description: "Manually verify a user by Discord ID and email.", hasSettings: true },
    { name: "/user",          description: "Show detailed Discord info, verification status, and warn history for a member.", hasSettings: true },
    { name: "/say",           description: "Send a message as the bot in the current or a chosen channel.", hasSettings: true },
  ],
  Verification: [
    { name: "/verify",   description: "Verify yourself as an active FEI student using your AIS ID.", hasSettings: true },
    { name: "/code",     description: "Enter the verification code received via email to complete verification." },
    { name: "/find", description: "Find a verified user's record by their AIS ID.", hasSettings: true },
    { name: "/mywarns",  description: "Check your own active warnings on this server.", hasSettings: true },
  ],
  Utility: [
    { name: "/info",       description: "Show bot configuration and status information.", hasSettings: true },
    { name: "/serverinfo", description: "Display general information about the current server.", hasSettings: true },
  ],
};

// ─── Command settings modal ───────────────────────────────────────────────────

function CommandSettingsModal({ title, commandKey, guildId, onClose }: {
  title: string;
  commandKey: string;
  guildId: string;
  onClose: () => void;
}) {
  const schema = CMD_SETTINGS_SCHEMA[commandKey] ?? {};
  const defaults = CMD_SETTINGS_DEFAULTS[commandKey] ?? {};

  const [dmUser, setDmUser] = useState(defaults.dmUser ?? false);
  const [ephemeral, setEphemeral] = useState(defaults.ephemeral ?? false);
  const [includeBots, setIncludeBots] = useState(defaults.includeBots ?? false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const savedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [
    guildId,
    commandKey,
    schema.dmUser,
    schema.ephemeral,
    schema.includeBots,
    defaults.dmUser,
    defaults.ephemeral,
    defaults.includeBots,
  ]);

  const save = async () => {
    setSaving(true);
    const data: CmdSettingsData = {};
    if (schema.dmUser)      data.dmUser = dmUser;
    if (schema.ephemeral)   data.ephemeral = ephemeral;
    if (schema.includeBots) data.includeBots = includeBots;
    try {
      await adminApi.saveCommandSettings(guildId, commandKey, data as Record<string, unknown>);
      setSaved(true);
      if (savedTimerRef.current !== null) clearTimeout(savedTimerRef.current);
      savedTimerRef.current = setTimeout(() => setSaved(false), 2000);
    } finally { setSaving(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/70" />
      <div className="relative w-full max-w-lg bg-zinc-900 border border-zinc-700 rounded-xl shadow-2xl overflow-hidden"
        onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-800">
          <h2 className="text-lg font-bold text-zinc-100">{title}</h2>
          <button onClick={onClose} className="text-zinc-500 hover:text-zinc-200 transition-colors"><X className="w-5 h-5" /></button>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 px-6 py-8 text-zinc-500"><Loader2 className="w-4 h-4 animate-spin" /> Loading…</div>
        ) : (
          <div className="px-6 py-5 space-y-5 max-h-[70vh] overflow-y-auto scrollbar-thin">
            {schema.ephemeral && (
              <CmdSettingsRow label="Ephemeral response" hint="Only visible to the user who ran the command">
                <Toggle enabled={ephemeral} onChange={setEphemeral} />
              </CmdSettingsRow>
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
      </div>
    </div>
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
      if (savedTimerRef.current !== null) clearTimeout(savedTimerRef.current);
      savedTimerRef.current = setTimeout(() => setSaved(false), 2000);
    } finally { setSaving(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/70" />
      <div className="relative w-full max-w-lg bg-zinc-900 border border-zinc-700 rounded-xl shadow-2xl overflow-hidden"
        onClick={e => e.stopPropagation()}>
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
      </div>
    </div>
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
  const [modal, setModal] = useState<"auth" | "settings" | null>(null);
  const [toggling, setToggling] = useState(false);

  const handleToggle = async (v: boolean) => {
    if (!guildId || toggling) return;
    setToggling(true);
    onToggle(cmd.name, v);
    try {
      await adminApi.setCommandEnabled(guildId, cmd.name, v);
    } catch {
      onToggle(cmd.name, !v);
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
            className="flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold uppercase tracking-wider bg-indigo-700/80 hover:bg-indigo-600 text-white transition-colors">
            <SlidersHorizontal className="w-3 h-3" /> Settings
          </Link>
        )}
      </div>

      {modal === "auth" && guildId && createPortal(
        <PermissionsModal
          title={`Authorization (${cmd.name})`}
          guildId={guildId}
          commandKey={cmd.name.replace("/", "")}
          roles={roles}
          channels={channels}
          onClose={() => setModal(null)}
          onSaved={onPermsSaved}
        />, document.body
      )}
      {modal === "settings" && guildId && createPortal(
        <CommandSettingsModal
          title={`Settings (${cmd.name})`}
          commandKey={cmd.name.replace("/", "")}
          guildId={guildId}
          onClose={() => setModal(null)}
        />,
        document.body
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
    } catch {
      setCmdStates(oldStates);
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
      if (deployTimerRef.current !== null) clearTimeout(deployTimerRef.current);
      deployTimerRef.current = setTimeout(() => setDeployed(false), 3000);
    } catch (e) { console.error(e); }
    finally { setDeploying(false); }
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
