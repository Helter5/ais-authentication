import { useRef, useState } from "react";
import { adminApi, apiErrorMessage, type ThesisCounterConfig } from "@/lib/api";
import { AlertCircle, CalendarClock, CheckCircle2, Hash, Loader2, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { useToast } from "@/components/ui/toast";
import {
  useSelectedGuildId, ChannelPicker, ModulePageHeader, ConfigSidebar, EmptyConfigSelection, EmojiPickerButton,
} from "@/components/modules/shared";

type Draft = { channel_id: string; label: "BP" | "DP"; target_date: string; name_format: string; today_format: string };

const DEFAULT_DRAFT: Draft = { channel_id: "", label: "BP", target_date: "", name_format: "", today_format: "" };
const DEFAULT_NAME_FORMAT = "{days}-{days_word}-do-{label}";
const DEFAULT_TODAY_FORMAT = "dnes-{label}";

/** Mirrors Discord's own channel-rename input: lowercases and turns spaces into hyphens as you type. */
function sanitizeChannelNameLike(value: string): string {
  return value.replace(/ /g, "-").toLowerCase();
}

/** Text input for a channel-name template: live space-to-hyphen/lowercase like Discord's own
 *  rename box, plus an emoji picker inserted at the cursor (custom Discord emoji don't render in
 *  channel names, so the shared picker's unicode-only mode is used - no guildEmojis passed). */
function TemplateInput({ value, onChange, placeholder }: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);

  const insertEmoji = (emoji: string) => {
    const el = inputRef.current;
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? value.length;
    const next = sanitizeChannelNameLike(value.slice(0, start) + emoji + value.slice(end));
    onChange(next);
    requestAnimationFrame(() => {
      el?.focus();
      el?.setSelectionRange(start + emoji.length, start + emoji.length);
    });
  };

  return (
    <div className="flex items-center gap-2">
      <input ref={inputRef} value={value} onChange={e => onChange(sanitizeChannelNameLike(e.target.value))}
        placeholder={placeholder}
        className="flex-1 min-w-0 px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 font-mono outline-none focus:border-indigo-500 transition-colors" />
      <EmojiPickerButton onSelect={insertEmoji}
        buttonClassName="w-9 h-[38px] flex-shrink-0 flex items-center justify-center bg-zinc-800 border border-zinc-700 rounded hover:border-zinc-500 transition-colors" />
    </div>
  );
}

export function ThesisCounterModule() {
  const guildId = useSelectedGuildId();
  const [enabled, setEnabled] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [configs, setConfigs] = useState<ThesisCounterConfig[]>([]);
  const [selectedId, setSelectedId] = useState<number | "new" | null>(null);
  const [draft, setDraft] = useState<Draft>({ ...DEFAULT_DRAFT });
  const [channels, setChannels] = useState<{ id: string; name: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [confirmDeleteSelected, setConfirmDeleteSelected] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    let cancelled = false;
    setLoading(Boolean(guildId));
    setEnabled(false);
    setConfigs([]);
    setSelectedId(null);
    setDraft({ ...DEFAULT_DRAFT });
    setChannels([]);
    setDeleteId(null);
    if (!guildId) return () => { cancelled = true; };
    Promise.all([
      adminApi.getThesisCounterEnabled(guildId),
      adminApi.getThesisCounters(guildId),
      adminApi.getDiscordTextChannels(guildId),
    ]).then(([e, cfgs, c]) => {
      if (cancelled) return;
      setEnabled(e.enabled);
      setConfigs(cfgs);
      setChannels(c);
    }).catch(error => { if (!cancelled) console.error(error); }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [guildId]);

  const toggleEnabled = async (value: boolean) => {
    if (!guildId) return;
    setToggling(true);
    setEnabled(value);
    try {
      await adminApi.setThesisCounterEnabled(guildId, value);
      toast(value ? "Thesis countdown enabled." : "Thesis countdown disabled.");
    } catch (e: unknown) {
      setEnabled(!value);
      toast(apiErrorMessage(e, "Failed to change module state."), "error");
    } finally {
      setToggling(false);
    }
  };

  const selectCfg = (cfg: ThesisCounterConfig) => {
    setSelectedId(cfg.id);
    setConfirmDeleteSelected(false);
    setDraft({
      channel_id: cfg.channel_id,
      label: cfg.label,
      target_date: cfg.target_date,
      name_format: cfg.name_format ?? "",
      today_format: cfg.today_format ?? "",
    });
  };

  const newCfg = () => {
    setSelectedId("new");
    setConfirmDeleteSelected(false);
    setDraft({ ...DEFAULT_DRAFT });
  };

  const upd = <K extends keyof Draft>(key: K, val: Draft[K]) => setDraft(d => ({ ...d, [key]: val }));

  const save = async () => {
    if (!guildId) return;
    if (!draft.channel_id) { toast("Select a room first.", "error"); return; }
    if (!draft.target_date) { toast("Pick a defense date.", "error"); return; }

    setSaving(true);
    try {
      const nameFormat = draft.name_format.trim() || null;
      const todayFormat = draft.today_format.trim() || null;
      if (selectedId === "new") {
        const result = await adminApi.createThesisCounter(guildId, {
          channel_id: draft.channel_id, label: draft.label, target_date: draft.target_date,
          name_format: nameFormat, today_format: todayFormat,
        });
        setConfigs(prev => [...prev, result]);
        setSelectedId(result.id);
      } else {
        const result = await adminApi.updateThesisCounter(selectedId as number, guildId, {
          channel_id: draft.channel_id, label: draft.label, target_date: draft.target_date, name_format: nameFormat, today_format: todayFormat,
        });
        setConfigs(prev => prev.map(c => c.id === selectedId ? result : c));
      }
      toast("Thesis counter saved.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to save thesis counter."), "error");
    } finally {
      setSaving(false);
    }
  };

  const deleteCfg = async (id: number) => {
    if (!guildId) return;
    try {
      await adminApi.deleteThesisCounter(id, guildId);
      setConfigs(prev => prev.filter(c => c.id !== id));
      if (selectedId === id) setSelectedId(null);
      setDeleteId(null);
      toast("Thesis counter removed, room name restored.");
    } catch (e: unknown) {
      toast(apiErrorMessage(e, "Failed to delete thesis counter."), "error");
    }
  };

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      <ModulePageHeader moduleName="Thesis Countdown" guildId={guildId} loading={loading} enabled={enabled} onToggleEnabled={toggleEnabled} toggling={toggling} />

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
          <ConfigSidebar
            items={configs}
            getKey={cfg => cfg.id}
            selectedKey={selectedId}
            onSelect={selectCfg}
            onNew={newCfg}
            newLabel="New Counter"
            emptyLabel="No thesis counters configured"
            renderTitle={cfg => `${cfg.label} — ${channels.find(c => c.id === cfg.channel_id)?.name ?? cfg.channel_id}`}
            renderSubtitle={cfg => (
              <>
                <Hash className="w-2.5 h-2.5" />
                {cfg.active ? `${cfg.days_remaining} day${cfg.days_remaining === 1 ? "" : "s"} left` : "Inactive (defense day passed)"}
              </>
            )}
            deleteKey={deleteId}
            onRequestDelete={setDeleteId}
            onDelete={cfg => deleteCfg(cfg.id)}
          />

          {selectedId === null ? (
            <EmptyConfigSelection icon={<CalendarClock className="w-10 h-10 mx-auto opacity-30" />} label="Select a counter or create a new one" />
          ) : (
            <div className="flex-1 overflow-y-auto scrollbar-thin p-6 space-y-5 max-w-2xl">
              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <CalendarClock className="w-4 h-4 text-indigo-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Counter</h2>
                </div>
                <div className="px-4 py-4 space-y-4">
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Room</p>
                    <ChannelPicker channels={channels} value={draft.channel_id} onChange={v => upd("channel_id", v ?? "")} />
                    {selectedId !== "new" && (
                      <p className="text-[11px] text-zinc-600 mt-1">
                        Changing the room restores this counter's current room name and moves the countdown there.
                      </p>
                    )}
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Type</p>
                    <div className="grid grid-cols-2 rounded border border-zinc-700 overflow-hidden">
                      {(["BP", "DP"] as const).map(l => (
                        <button key={l} type="button" onClick={() => upd("label", l)}
                          className={cn("py-2 text-xs font-bold uppercase tracking-wider transition-colors",
                            draft.label === l ? "bg-indigo-600 text-white" : "bg-zinc-800 text-zinc-500 hover:text-zinc-300")}>
                          {l}
                        </button>
                      ))}
                    </div>
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">Defense date</p>
                    <input type="date" value={draft.target_date} onChange={e => upd("target_date", e.target.value)}
                      className="w-full px-3 py-2 bg-zinc-800 border border-zinc-700 rounded text-sm text-zinc-200 outline-none focus:border-indigo-500 transition-colors" />
                    <p className="text-[11px] text-zinc-600 mt-1">
                      The room's channel name is renamed daily until the day arrives.
                    </p>
                  </div>
                </div>
              </div>

              <div className="rounded-lg border border-zinc-800 bg-zinc-900">
                <div className="px-4 py-3 border-b border-zinc-800 flex items-center gap-2">
                  <CalendarClock className="w-4 h-4 text-amber-400" />
                  <h2 className="text-sm font-bold text-zinc-100">Channel name format</h2>
                </div>
                <div className="px-4 py-4 space-y-4">
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">While counting down</p>
                    <TemplateInput value={draft.name_format} onChange={v => upd("name_format", v)} placeholder={DEFAULT_NAME_FORMAT} />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-zinc-400 mb-1.5">On the defense day</p>
                    <TemplateInput value={draft.today_format} onChange={v => upd("today_format", v)} placeholder={DEFAULT_TODAY_FORMAT} />
                  </div>
                  <p className="text-[11px] text-zinc-600">
                    Both empty = defaults above. Typing lowercases and turns spaces into hyphens, same as Discord's own channel rename box. Placeholders: <code className="text-zinc-400">{"{days}"}</code>, <code className="text-zinc-400">{"{days_word}"}</code> (den/dni),{" "}
                    <code className="text-zinc-400">{"{label}"}</code> (bp/dp), <code className="text-zinc-400">{"{target_date}"}</code> (dd.MM.yyyy). Any other text and emoji allowed, e.g. <code className="text-zinc-400">🎓-{"{days}"}-{"{days_word}"}-do-{"{label}"}</code>.
                  </p>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <button type="button" onClick={save} disabled={saving}
                  className="flex items-center gap-2 px-4 py-2 rounded bg-indigo-600 hover:bg-indigo-500 text-sm font-bold text-white disabled:opacity-50 transition-colors">
                  {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
                  Save
                </button>
                {selectedId !== "new" && (
                  confirmDeleteSelected ? (
                    <div className="flex items-center gap-2 text-sm">
                      <span className="text-zinc-400">Delete this counter and restore the room's name?</span>
                      <button type="button" onClick={() => deleteCfg(selectedId as number)}
                        className="px-3 py-1.5 rounded bg-red-600 hover:bg-red-500 text-xs font-bold text-white transition-colors">
                        Yes, delete
                      </button>
                      <button type="button" onClick={() => setConfirmDeleteSelected(false)}
                        className="px-3 py-1.5 rounded border border-zinc-700 hover:border-zinc-500 text-xs font-semibold text-zinc-300 transition-colors">
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <button type="button" onClick={() => setConfirmDeleteSelected(true)}
                      className="flex items-center gap-2 px-4 py-2 rounded border border-zinc-700 hover:border-red-500 hover:text-red-400 text-sm font-semibold text-zinc-300 transition-colors">
                      <Trash2 className="w-4 h-4" />
                      Delete
                    </button>
                  )
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
