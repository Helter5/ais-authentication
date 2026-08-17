import { useEffect, useState } from "react";
import { Loader2, CheckCircle2, AlertCircle, ShieldAlert, Trash2, MessageSquare, Radio } from "lucide-react";
import { adminApi, apiErrorMessage, type HackedAccountTrapSettings, type DeleteMessageHistorySeconds } from "@/lib/api";
import { useSelectedGuildId, Toggle, CmdSettingsRow, MultiPicker, ChannelPicker, ModulePageHeader } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";

const DELETE_MESSAGE_HISTORY_OPTIONS: { value: DeleteMessageHistorySeconds; label: string }[] = [
  { value: 3600, label: "Previous Hour" },
  { value: 21600, label: "Previous 6 Hours" },
  { value: 43200, label: "Previous 12 Hours" },
  { value: 86400, label: "Previous 24 Hours" },
  { value: 259200, label: "Previous 3 Days" },
  { value: 604800, label: "Previous 7 Days" },
];

const DEFAULT_TRAP_SETTINGS: HackedAccountTrapSettings = {
  enabled: false,
  trapChannelId: null,
  deleteTriggerMessage: true,
  ignoreAdministrators: true,
  exemptRoleIds: [],
  deleteMessageHistory: false,
  deleteMessageHistorySeconds: 3600,
  dmUser: false,
  dmMessage: "Your account triggered the hacked-account trap in {server}. Please contact a server administrator if this was a mistake.",
  reason: "Hacked account trap triggered",
};

export function HackedAccountTrapModule() {
  const guildId = useSelectedGuildId();
  const [settings, setSettings] = useState<HackedAccountTrapSettings>(DEFAULT_TRAP_SETTINGS);
  const [spamLogChannelId, setSpamLogChannelId] = useState<string | null>(null);
  const [channels, setChannels] = useState<{ id: string; name: string }[]>([]);
  const [roles, setRoles] = useState<{ id: string; name: string }[]>([]);
  const [loading, setLoading] = useState(Boolean(guildId));
  const [saving, setSaving] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    let cancelled = false;
    setLoading(Boolean(guildId));
    setError(null);
    setSettings(DEFAULT_TRAP_SETTINGS);
    setChannels([]);
    setRoles([]);
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getHackedAccountTrap(guildId),
      adminApi.getDiscordTextChannels(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getLogChannels(guildId),
    ])
      .then(([data, channelList, roleList, logChannels]) => {
        if (cancelled) return;
        setSettings(data);
        setChannels(channelList);
        setRoles(roleList);
        setSpamLogChannelId(
          logChannels.eventTypes.find(e => e.eventType === "HACKED_ACCOUNT_TRAP_TRIGGERED")?.channelId ?? null);
      })
      .catch(err => {
        if (!cancelled) setError(apiErrorMessage(err, "Failed to load module settings."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [guildId]);

  const update = <K extends keyof HackedAccountTrapSettings>(key: K, value: HackedAccountTrapSettings[K]) => {
    setSettings(current => ({ ...current, [key]: value }));
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      const next = await adminApi.saveHackedAccountTrap(guildId, settings);
      setSettings(next);
      toast("Module settings saved.");
    } catch (err: unknown) {
      toast(apiErrorMessage(err, "Failed to save module settings."), "error");
    } finally {
      setSaving(false);
    }
  };

  const toggleModule = async (enabled: boolean) => {
    if (enabled && !settings.trapChannelId) {
      toast("Choose a trap channel before enabling the module.", "error");
      return;
    }
    if (enabled && !spamLogChannelId) {
      toast("Set a Log Channel for this module on the Modules page before enabling it.", "error");
      return;
    }
    setToggling(true);
    setSettings(prev => ({ ...prev, enabled }));
    try {
      const next = await adminApi.saveHackedAccountTrap(guildId, { ...settings, enabled });
      setSettings(next);
      toast(enabled ? "Module enabled." : "Module disabled.");
    } catch (err: unknown) {
      setSettings(prev => ({ ...prev, enabled: !enabled }));
      toast(apiErrorMessage(err, "Failed to change module state."), "error");
    } finally {
      setToggling(false);
    }
  };

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      <ModulePageHeader moduleName="Hacked Account Trap" guildId={guildId} loading={loading}
        enabled={settings.enabled} onToggleEnabled={toggleModule} toggling={toggling} />

      {!guildId ? (
        <div className="flex items-center gap-2 p-6 text-zinc-500 text-sm">
          <AlertCircle className="w-4 h-4" /> No server selected.
        </div>
      ) : loading ? (
        <div className="flex items-center gap-2 p-6 text-zinc-500 text-sm">
          <Loader2 className="w-4 h-4 animate-spin" /> Loading…
        </div>
      ) : (
        <div className="p-6 max-w-2xl space-y-5">

          <div className="rounded-lg border border-zinc-800 bg-zinc-900">
            <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
              <Radio className="w-4 h-4 text-indigo-400" />
              <div>
                <h2 className="text-sm font-bold text-zinc-100">Trigger</h2>
                <p className="text-xs text-zinc-500 mt-0.5">Posting any message in the trap channel bans the author permanently.</p>
              </div>
            </div>
            <div className="px-4 py-4 space-y-4">
              <div>
                <p className="text-xs font-semibold text-zinc-400 mb-1.5">Trap channel</p>
                <ChannelPicker channels={channels} value={settings.trapChannelId} onChange={value => update("trapChannelId", value)} />
              </div>

              <CmdSettingsRow label="Ignore administrators" hint="Administrators will never trigger the trap">
                <Toggle enabled={settings.ignoreAdministrators} onChange={value => update("ignoreAdministrators", value)} />
              </CmdSettingsRow>

              <CmdSettingsRow label="Delete message history" hint="Also delete the banned account's recent messages, using Discord's own ban duration options">
                <Toggle enabled={settings.deleteMessageHistory} onChange={value => update("deleteMessageHistory", value)} />
              </CmdSettingsRow>
              {settings.deleteMessageHistory && (
                <div>
                  <p className="text-xs font-semibold text-zinc-400 mb-1.5">Delete messages from</p>
                  <select value={settings.deleteMessageHistorySeconds}
                    onChange={event => update("deleteMessageHistorySeconds", Number(event.target.value) as DeleteMessageHistorySeconds)}
                    className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors">
                    {DELETE_MESSAGE_HISTORY_OPTIONS.map(option => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </div>
              )}

              <div>
                <p className="text-xs font-semibold text-zinc-400 mb-1.5">Exempt roles</p>
                <MultiPicker options={roles} selected={settings.exemptRoleIds} onChange={value => update("exemptRoleIds", value)} placeholder="Select exempt roles" />
                <p className="text-[11px] text-zinc-600 mt-1">Members with any selected role will not trigger the module.</p>
              </div>
            </div>
          </div>

          <div className="rounded-lg border border-zinc-800 bg-zinc-900">
            <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
              <Trash2 className="w-4 h-4 text-amber-400" />
              <h2 className="text-sm font-bold text-zinc-100">Trigger Message</h2>
            </div>
            <div className="px-4 py-4">
              <CmdSettingsRow label="Delete triggering message" hint="Remove the message posted in the trap channel">
                <Toggle enabled={settings.deleteTriggerMessage} onChange={value => update("deleteTriggerMessage", value)} />
              </CmdSettingsRow>
            </div>
          </div>

          <div className="rounded-lg border border-zinc-800 bg-zinc-900">
            <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
              <MessageSquare className="w-4 h-4 text-emerald-400" />
              <h2 className="text-sm font-bold text-zinc-100">Notify User</h2>
            </div>
            <div className="px-4 py-4 space-y-3">
              <CmdSettingsRow label="DM affected user" hint="Attempt to notify the account before applying the action">
                <Toggle enabled={settings.dmUser} onChange={value => update("dmUser", value)} />
              </CmdSettingsRow>
              {settings.dmUser && (
                <>
                  <textarea value={settings.dmMessage} onChange={event => update("dmMessage", event.target.value)} rows={4}
                    className="w-full resize-none px-3 py-2 bg-zinc-800 border border-zinc-700 rounded font-mono text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors scrollbar-thin"
                    placeholder="Message sent to the affected user" />
                  <p className="text-[11px] text-zinc-600">Variables: <span className="font-mono">{"{user}, {server}"}</span></p>
                </>
              )}
            </div>
          </div>

          <div className="rounded-lg border border-zinc-800 bg-zinc-900">
            <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
              <ShieldAlert className="w-4 h-4 text-zinc-400" />
              <h2 className="text-sm font-bold text-zinc-100">Audit Log</h2>
            </div>
            <div className="px-4 py-4">
              <p className="text-xs font-semibold text-zinc-400 mb-1.5">Moderation reason</p>
              <input value={settings.reason} onChange={event => update("reason", event.target.value)}
                className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors"
                placeholder="Reason shown in Discord's audit log" />
            </div>
          </div>

          {error && (
            <div className="flex items-center gap-2 rounded border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />{error}
            </div>
          )}

          <button type="button" onClick={save} disabled={saving}
            className="flex items-center gap-2 px-4 py-2 rounded bg-indigo-600 hover:bg-indigo-500 text-sm font-bold text-white disabled:opacity-50 transition-colors">
            {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
            Save
          </button>

        </div>
      )}
    </div>
  );
}
