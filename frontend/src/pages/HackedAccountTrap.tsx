import { useEffect, useRef, useState } from "react";
import { Loader2, CheckCircle2, ShieldAlert } from "lucide-react";
import { cn } from "@/lib/utils";
import { NumberStepper } from "@/components/ui/number-stepper";
import { adminApi, apiErrorMessage, type HackedAccountTrapSettings } from "@/lib/api";
import { useSelectedGuildId, ModuleShell, Toggle, CmdSettingsRow, MultiPicker, ChannelPicker } from "@/components/modules/shared";
import { useToast } from "@/components/ui/toast";

const DEFAULT_TRAP_SETTINGS: HackedAccountTrapSettings = {
  enabled: false,
  trapChannelId: null,
  action: "timeout",
  timeoutMinutes: 1440,
  deleteTriggerMessage: true,
  deleteRecentMessages: true,
  cleanupMinutes: 60,
  exemptRoleIds: [],
  ignoreAdministrators: true,
  dmUser: false,
  dmMessage: "Your account triggered the hacked-account trap in {server}. Please contact a server administrator if this was a mistake.",
  reason: "Hacked account trap triggered",
  incidentChannelEnabled: false,
  incidentChannelCategoryId: null,
  incidentChannelClosedCategoryId: null,
  incidentChannelNameTemplate: "hacked-{user}",
  incidentChannelIncludeUser: false,
  incidentChannelMessage: "Hacked account trap triggered by {user}.",
  incidentChannelPostDmStatus: false,
  incidentChannelTagRoles: false,
  incidentChannelTagRoleIds: [],
};

export function HackedAccountTrapModule() {
  const guildId = useSelectedGuildId();
  const [settings, setSettings] = useState<HackedAccountTrapSettings>(DEFAULT_TRAP_SETTINGS);
  const [spamLogChannelId, setSpamLogChannelId] = useState<string | null>(null);
  const [channels, setChannels] = useState<{ id: string; name: string }[]>([]);
  const [categories, setCategories] = useState<{ id: string; name: string }[]>([]);
  const [roles, setRoles] = useState<{ id: string; name: string }[]>([]);
  const [loading, setLoading] = useState(Boolean(guildId));
  const [saving, setSaving] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const savedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { toast } = useToast();

  useEffect(() => () => {
    if (savedTimerRef.current !== null) clearTimeout(savedTimerRef.current);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(Boolean(guildId));
    setError(null);
    setSettings(DEFAULT_TRAP_SETTINGS);
    setChannels([]);
    setCategories([]);
    setRoles([]);
    setSaved(false);
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getHackedAccountTrap(guildId),
      adminApi.getDiscordTextChannels(guildId),
      adminApi.getDiscordCategories(guildId),
      adminApi.getDiscordRoles(guildId),
      adminApi.getLogChannels(guildId),
    ])
      .then(([data, channelList, categoryList, roleList, logChannels]) => {
        if (cancelled) return;
        setSettings(data);
        setChannels(channelList);
        setCategories(categoryList);
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
      setSaved(true);
      toast("Module settings saved.");
      if (savedTimerRef.current !== null) clearTimeout(savedTimerRef.current);
      savedTimerRef.current = setTimeout(() => setSaved(false), 2000);
    } catch (err: unknown) {
      toast(apiErrorMessage(err, "Failed to save module settings."), "error");
    } finally {
      setSaving(false);
    }
  };

  const toggleModule = async () => {
    const enabled = !settings.enabled;
    if (enabled && !settings.trapChannelId) {
      toast("Choose a trap channel before enabling the module.", "error");
      return;
    }
    if (enabled && !spamLogChannelId) {
      toast("Set an Automod Log channel in Settings → Log Channels before enabling this module.", "error");
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
    <ModuleShell
      title="Hacked Account Trap"
      guildId={guildId}
      action={guildId && !loading ? (
        <button
          type="button"
          onClick={toggleModule}
          disabled={toggling}
          className="flex items-center gap-3 text-sm font-semibold text-zinc-200 transition-colors hover:text-white disabled:cursor-wait disabled:opacity-60"
        >
          <span className={cn(
            "relative h-7 w-12 rounded-full border transition-colors",
            settings.enabled
              ? "border-emerald-400/40 bg-zinc-800"
              : "border-zinc-700 bg-zinc-800",
          )}>
            <span className={cn(
              "absolute top-0.5 flex h-5 w-5 items-center justify-center rounded-full transition-all",
              settings.enabled
                ? "left-6 bg-emerald-400 shadow-[0_0_10px_rgba(52,211,153,0.45)]"
                : "left-0.5 bg-zinc-500",
            )}>
              {toggling && <Loader2 className="h-3 w-3 animate-spin text-zinc-900" />}
            </span>
          </span>
          {settings.enabled ? "Disable Module" : "Enable Module"}
        </button>
      ) : undefined}
    >
      {loading ? (
        <div className="flex items-center gap-2 py-8 text-sm text-zinc-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading settings...
        </div>
      ) : (
        <div className="space-y-5">
          <div className="space-y-5 rounded-xl border border-zinc-700 bg-zinc-900 p-5">
            <div className="space-y-1.5">
              <p className="text-sm font-semibold text-zinc-200">Trap channel</p>
              <p className="text-xs text-zinc-500">Posting any message in this channel triggers the module.</p>
              <ChannelPicker channels={channels} value={settings.trapChannelId} onChange={value => update("trapChannelId", value)} />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <p className="text-sm font-semibold text-zinc-200">Moderation action</p>
                <select value={settings.action} onChange={event => update("action", event.target.value as HackedAccountTrapSettings["action"])}
                  className="w-full rounded border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-200 outline-none focus:border-indigo-500">
                  <option value="timeout">Timeout</option>
                  <option value="kick">Kick</option>
                  <option value="ban">Ban</option>
                </select>
              </div>
              {settings.action === "timeout" && (
                <div className="space-y-1.5">
                  <p className="text-sm font-semibold text-zinc-200">Timeout duration</p>
                  <div className="flex items-center gap-2">
                    <NumberStepper
                      value={settings.timeoutMinutes}
                      onChange={value => update("timeoutMinutes", value)}
                      min={1}
                      max={40320}
                      className="w-full"
                      ariaLabel="Timeout duration in minutes"
                    />
                    <span className="text-xs text-zinc-500">minutes</span>
                  </div>
                </div>
              )}
            </div>

            <div className="space-y-3 border-t border-zinc-800 pt-4">
              <CmdSettingsRow label="Delete triggering message" hint="Remove the message posted in the trap channel">
                <Toggle enabled={settings.deleteTriggerMessage} onChange={value => update("deleteTriggerMessage", value)} />
              </CmdSettingsRow>
              <CmdSettingsRow label="Delete recent messages" hint="Remove the affected account's recent messages across accessible channels">
                <Toggle enabled={settings.deleteRecentMessages} onChange={value => update("deleteRecentMessages", value)} />
              </CmdSettingsRow>
              {settings.deleteRecentMessages && (
                <CmdSettingsRow label="Cleanup period" hint="How far back message cleanup should search">
                  <div className="flex items-center gap-2">
                    <NumberStepper
                      value={settings.cleanupMinutes}
                      onChange={value => update("cleanupMinutes", value)}
                      min={1}
                      max={1440}
                      className="w-36"
                      ariaLabel="Message cleanup period in minutes"
                    />
                    <span className="text-xs text-zinc-500">minutes</span>
                  </div>
                </CmdSettingsRow>
              )}
              <CmdSettingsRow label="Ignore administrators" hint="Administrators will never trigger the trap">
                <Toggle enabled={settings.ignoreAdministrators} onChange={value => update("ignoreAdministrators", value)} />
              </CmdSettingsRow>
            </div>

            <div className="space-y-1.5 border-t border-zinc-800 pt-4">
              <p className="text-sm font-semibold text-zinc-200">Exempt roles</p>
              <p className="text-xs text-zinc-500">Members with any selected role will not trigger the module.</p>
              <MultiPicker options={roles} selected={settings.exemptRoleIds} onChange={value => update("exemptRoleIds", value)} placeholder="Select exempt roles" />
            </div>

            <div className="space-y-3 border-t border-zinc-800 pt-4">
              <CmdSettingsRow label="DM affected user" hint="Attempt to notify the account before applying the action">
                <Toggle enabled={settings.dmUser} onChange={value => update("dmUser", value)} />
              </CmdSettingsRow>
              {settings.dmUser && (
                <textarea value={settings.dmMessage} onChange={event => update("dmMessage", event.target.value)} rows={4}
                  className="w-full resize-none rounded border border-zinc-700 bg-zinc-800 px-3 py-2 font-mono text-sm text-zinc-200 outline-none focus:border-indigo-500"
                  placeholder="Message sent to the affected user" />
              )}
              {settings.dmUser && <p className="text-[11px] text-zinc-600">Variables: <span className="font-mono">{"{user}, {server}"}</span></p>}
            </div>

            <div className="space-y-3 border-t border-zinc-800 pt-4">
              <CmdSettingsRow label="Create incident channel" hint="Create a private channel for this incident when the trap triggers">
                <Toggle enabled={settings.incidentChannelEnabled} onChange={value => update("incidentChannelEnabled", value)} />
              </CmdSettingsRow>

              {settings.incidentChannelEnabled && (
                <div className="space-y-4 rounded-lg border border-zinc-800 bg-zinc-950/40 p-4">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="space-y-1.5">
                      <p className="text-sm font-semibold text-zinc-200">Channel name</p>
                      <input value={settings.incidentChannelNameTemplate} onChange={event => update("incidentChannelNameTemplate", event.target.value)}
                        className="w-full rounded border border-zinc-700 bg-zinc-800 px-3 py-2 font-mono text-sm text-zinc-200 outline-none focus:border-indigo-500"
                        placeholder="hacked-{user}" />
                      <p className="text-[11px] text-zinc-600">Variables: <span className="font-mono">{"{user}, {id}"}</span></p>
                    </div>
                    <div className="space-y-1.5">
                      <p className="text-sm font-semibold text-zinc-200">Category</p>
                      <p className="text-xs text-zinc-500">Where the created channel is placed. Optional.</p>
                      <ChannelPicker channels={categories} value={settings.incidentChannelCategoryId} onChange={value => update("incidentChannelCategoryId", value)} prefix="" placeholder="No category" />
                    </div>
                    <div className="space-y-1.5">
                      <p className="text-sm font-semibold text-zinc-200">Category on close</p>
                      <p className="text-xs text-zinc-500">Where the channel is moved when the ticket is closed. Optional.</p>
                      <ChannelPicker channels={categories} value={settings.incidentChannelClosedCategoryId} onChange={value => update("incidentChannelClosedCategoryId", value)} prefix="" placeholder="Don't move" />
                    </div>
                  </div>

                  <p className="text-xs text-zinc-500">Manager Roles (Settings → Manager Roles) always get View Channel on the created channel.</p>

                  <CmdSettingsRow label="Give affected user access" hint="Grant the triggering user View Channel while the ticket is open; removed on close, restored on reopen">
                    <Toggle enabled={settings.incidentChannelIncludeUser} onChange={value => update("incidentChannelIncludeUser", value)} />
                  </CmdSettingsRow>

                  <div className="space-y-1.5">
                    <p className="text-sm font-semibold text-zinc-200">Channel message</p>
                    <p className="text-xs text-zinc-500">Posted in the created channel when it's opened.</p>
                    <textarea value={settings.incidentChannelMessage} onChange={event => update("incidentChannelMessage", event.target.value)} rows={3}
                      className="w-full resize-none rounded border border-zinc-700 bg-zinc-800 px-3 py-2 font-mono text-sm text-zinc-200 outline-none focus:border-indigo-500"
                      placeholder="Message posted in the created channel" />
                    <p className="text-[11px] text-zinc-600">Variables: <span className="font-mono">{"{user}, {server}"}</span></p>
                  </div>

                  {settings.dmUser && (
                    <CmdSettingsRow label="Post DM status" hint="Post whether the DM to the affected user succeeded or failed">
                      <Toggle enabled={settings.incidentChannelPostDmStatus} onChange={value => update("incidentChannelPostDmStatus", value)} />
                    </CmdSettingsRow>
                  )}

                  <CmdSettingsRow label="Tag roles" hint="Mention selected roles in the created channel">
                    <Toggle enabled={settings.incidentChannelTagRoles} onChange={value => update("incidentChannelTagRoles", value)} />
                  </CmdSettingsRow>
                  {settings.incidentChannelTagRoles && (
                    <MultiPicker options={roles} selected={settings.incidentChannelTagRoleIds} onChange={value => update("incidentChannelTagRoleIds", value)} placeholder="Select roles to tag" />
                  )}
                </div>
              )}
            </div>

            <div className="space-y-1.5 border-t border-zinc-800 pt-4">
              <p className="text-sm font-semibold text-zinc-200">Moderation reason</p>
              <input value={settings.reason} onChange={event => update("reason", event.target.value)}
                className="w-full rounded border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-zinc-200 outline-none focus:border-indigo-500"
                placeholder="Reason shown in Discord's audit log" />
            </div>
          </div>

          {error && <div className="rounded border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">{error}</div>}
          <button onClick={save} disabled={saving}
            className="flex items-center gap-2 rounded bg-indigo-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-500 disabled:opacity-50">
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : saved ? <CheckCircle2 className="h-4 w-4" /> : <ShieldAlert className="h-4 w-4" />}
            {saved ? "Saved!" : "Save Module"}
          </button>
        </div>
      )}
    </ModuleShell>
  );
}
