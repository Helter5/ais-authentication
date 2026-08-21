import { useEffect, useMemo, useState, type ElementType, type ReactNode } from "react";
import {
  AlertCircle, Bot, Command, Gauge, Info, Loader2, LogIn, Settings2,
  ShieldAlert, TriangleAlert, Search, ChevronLeft, ChevronRight, X, UserCheck,
} from "lucide-react";
import { adminApi, apiErrorMessage, type AuditLog } from "@/lib/api";
import { cn } from "@/lib/utils";
import { useSelectedGuildId } from "@/components/modules/shared";
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";

type Tab = "dashboard" | "logins" | "warnings" | "automod" | "verification" | "commands";
type AccessLog = Awaited<ReturnType<typeof adminApi.getAccessLogs>>[number];
type WarningLog = Awaited<ReturnType<typeof adminApi.getAdminWarnings>>[number];

const TABS: { id: Tab; label: string; icon: ElementType }[] = [
  { id: "dashboard", label: "Dashboard", icon: Gauge },
  { id: "logins", label: "Logins", icon: LogIn },
  { id: "warnings", label: "Warnings", icon: TriangleAlert },
  { id: "automod", label: "Automod", icon: ShieldAlert },
  { id: "verification", label: "Verification", icon: UserCheck },
  { id: "commands", label: "Commands", icon: Command },
];

const AUTOMOD_LOGGED_ACTIONS = [
  "Hacked Account Trap triggers, message cleanup, and timeout/kick/ban actions",
  "Warning-threshold bans, kicks, and timeouts",
  "Failed or partially completed automatic actions, including the error",
];

const PAGE_SIZE = 25;

function fmt(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "medium" });
}

function EmptyState({ label }: { label: string }) {
  return (
    <div className="flex min-h-48 items-center justify-center rounded-b-xl border border-t-0 border-zinc-800 bg-zinc-950/30">
      <div className="text-center">
        <Bot className="mx-auto h-8 w-8 text-zinc-700" />
        <p className="mt-3 text-sm font-medium text-zinc-500">No {label.toLowerCase()} found</p>
      </div>
    </div>
  );
}

function TableShell({ headers, children, empty, emptyLabel }: {
  headers: ReactNode[];
  children: ReactNode;
  empty: boolean;
  emptyLabel: string;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px] text-sm">
        <thead>
          <tr className="border border-zinc-700 bg-zinc-800">
            {headers.map((header, index) => (
              <th key={index} className="border-r border-zinc-700 px-4 py-3 text-left text-xs font-bold uppercase tracking-wider text-zinc-300 last:border-r-0">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        {!empty && <tbody className="divide-y divide-zinc-800">{children}</tbody>}
      </table>
      {empty && <EmptyState label={emptyLabel} />}
    </div>
  );
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Looks up a Discord snowflake (role/channel/category id) by whatever the guild's roles/channels
 *  fetch resolved it to, or null if it's not one of those (a user id, or the guild's already gone). */
type IdResolver = (id: string) => string | null;
const NO_RESOLVER: IdResolver = () => null;

const SNOWFLAKE = /^\d{15,20}$/;
const CHANNEL_TOKEN = /\{channel=(\d{15,20})\}/g;

/** Resolves a bare snowflake string to its role/channel name, and any {channel=<id>} tokens
 *  embedded in message templates to "#name" - instead of showing raw Discord IDs nobody can read. */
function resolveText(text: string, resolve: IdResolver): string {
  const withChannels = text.replace(CHANNEL_TOKEN, (_match, id: string) => `#${resolve(id) ?? id}`);
  if (SNOWFLAKE.test(withChannels)) {
    const name = resolve(withChannels);
    if (name) return name;
  }
  return withChannels;
}

// "trapChannelId" -> "Trap channel ID", "adminOnly" -> "Admin only"
function humanizeKey(key: string): string {
  const spaced = key
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_-]/g, " ")
    .toLowerCase()
    .replace(/\bids\b/g, "IDs")
    .replace(/\bid\b/g, "ID")
    .replace(/\bdm\b/g, "DM");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function isBooleanMap(value: Record<string, unknown>): boolean {
  const values = Object.values(value);
  return values.length > 0 && values.every(v => typeof v === "boolean");
}

// A flat map of name -> enabled/disabled (e.g. bulk command toggles) reads better
// as two grouped lists than as raw {"/warn":false,"/warns":true,...} JSON.
function formatObject(value: Record<string, unknown>, resolve: IdResolver): string {
  if (isBooleanMap(value)) {
    const enabled = Object.keys(value).filter(k => value[k] === true);
    const disabled = Object.keys(value).filter(k => value[k] === false);
    return [
      enabled.length > 0 ? `enabled: ${enabled.join(", ")}` : null,
      disabled.length > 0 ? `disabled: ${disabled.join(", ")}` : null,
    ].filter(Boolean).join(" · ");
  }
  return Object.entries(value).map(([k, v]) => `${humanizeKey(k)}: ${formatScalar(v, resolve)}`).join(", ");
}

function formatScalar(value: unknown, resolve: IdResolver = NO_RESOLVER): string {
  if (value === null || value === undefined || value === "") return "none";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (Array.isArray(value)) return value.length === 0 ? "none" : value.map(v => formatScalar(v, resolve)).join(", ");
  if (isPlainObject(value)) return formatObject(value, resolve);
  if (typeof value === "string") return resolveText(value, resolve);
  return String(value);
}

function DetailValue({ value, resolve = NO_RESOLVER }: { value: unknown; resolve?: IdResolver }) {
  if (value === null || value === undefined || value === "") return <span className="text-zinc-600">-</span>;
  return <>{formatScalar(value, resolve)}</>;
}

function isScalarArray(value: unknown[]): boolean {
  return value.every(v => !isPlainObject(v) && !Array.isArray(v));
}

// Best-effort identity for matching an array item across a before/after pair, so a diff can say
// "this one plan's steps changed" instead of "the whole plans array changed". Falls back to
// position when nothing id-like is present (fine for ordered lists like steps).
function itemKey(item: unknown, index: number): string {
  if (isPlainObject(item)) {
    const candidate = item.id ?? item.channelId ?? item.name ?? item.channelName;
    if (typeof candidate === "string" || typeof candidate === "number") return String(candidate);
  }
  return `#${index}`;
}

function itemLabel(item: unknown): string | null {
  if (!isPlainObject(item)) return null;
  const candidate = item.name ?? item.channelName;
  return typeof candidate === "string" ? candidate : null;
}

// Whether a value needs its own block below the label (nested object, or array of objects) as
// opposed to a short "Label: value" that can sit on one line - so a plain scalar field never gets
// split across two lines just because it happens to live next to a big nested one.
function isBlockValue(value: unknown): boolean {
  if (Array.isArray(value)) return value.length > 0 && !isScalarArray(value);
  if (isPlainObject(value)) return !isBooleanMap(value);
  return false;
}

/** One "Label: value" line - inline when short, label-above-block when the value is itself a
 *  nested structure (so a plan/step list gets its own indented block instead of being crammed
 *  onto the same line as its label). */
function FieldRow({ label, block, children }: { label: string; block: boolean; children: ReactNode }) {
  if (block) {
    return (
      <div>
        <p className="text-zinc-500">{label}:</p>
        {children}
      </div>
    );
  }
  return (
    <div className="text-zinc-400">
      <span className="text-zinc-500">{label}: </span>
      <span className="text-zinc-300">{children}</span>
    </div>
  );
}

/** Renders one JSON-ish value as an indented structure - nested objects/arrays get their own
 *  rows instead of being flattened into one long comma-joined string. */
function ValueView({ value, resolve = NO_RESOLVER }: { value: unknown; resolve?: IdResolver }) {
  if (value === null || value === undefined || value === "") return <span className="text-zinc-600">none</span>;
  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="text-zinc-600">none</span>;
    if (isScalarArray(value)) return <>{formatScalar(value, resolve)}</>;
    return (
      <div className="space-y-1">
        {value.map((item, i) => (
          <div key={itemKey(item, i)} className="rounded border border-zinc-800/80 bg-zinc-900/40 px-2 py-1">
            {itemLabel(item) && <p className="mb-0.5 font-semibold text-zinc-300">{itemLabel(item)}</p>}
            <ValueView value={item} resolve={resolve} />
          </div>
        ))}
      </div>
    );
  }
  if (isPlainObject(value)) {
    if (isBooleanMap(value)) return <>{formatObject(value, resolve)}</>;
    return (
      <div className="space-y-0.5">
        {Object.entries(value).map(([k, v]) => (
          <FieldRow key={k} label={humanizeKey(k)} block={isBlockValue(v)}><ValueView value={v} resolve={resolve} /></FieldRow>
        ))}
      </div>
    );
  }
  return <>{formatScalar(value, resolve)}</>;
}

/** null/undefined/""/[] all mean "nothing set" - collapsing them to one canonical shape before
 *  comparing before/after keeps a field that went from unset to unset-a-different-way (e.g.
 *  `null` -> `[]`) from showing up as a no-op "none → none" diff line. */
function normalizeEmpty(value: unknown): unknown {
  if (value === undefined || value === null || value === "") return null;
  if (Array.isArray(value) && value.length === 0) return null;
  return value;
}

/** Long or multi-line values (message templates, embedded {channel=id} tokens) read better as a
 *  stacked removed/added block than crammed inline after a "→", where the old and new text run
 *  together with nothing separating them. */
function isLongText(text: string): boolean {
  return text.length > 40 || text.includes("\n");
}

/** Deep before/after diff - recurses into nested objects and matches array items by
 *  {@link itemKey} so only what actually changed is shown, instead of re-dumping an entire
 *  nested plan/step structure because one field two levels down changed. Returns null when
 *  the two values are equal (caller skips rendering anything for that branch). */
function DiffView({ before, after, resolve = NO_RESOLVER }: { before: unknown; after: unknown; resolve?: IdResolver }): ReactNode {
  if (JSON.stringify(normalizeEmpty(before)) === JSON.stringify(normalizeEmpty(after))) return null;

  if (Array.isArray(before) && Array.isArray(after)) {
    if (isScalarArray(before) && isScalarArray(after)) {
      return <>{formatScalar(before, resolve)} <span className="text-zinc-600">→</span> {formatScalar(after, resolve)}</>;
    }
    const beforeMap = new Map(before.map((v, i) => [itemKey(v, i), v]));
    const afterMap = new Map(after.map((v, i) => [itemKey(v, i), v]));
    const rows: ReactNode[] = [];
    new Set([...beforeMap.keys(), ...afterMap.keys()]).forEach(key => {
      const hasBefore = beforeMap.has(key);
      const hasAfter = afterMap.has(key);
      const label = itemLabel(hasAfter ? afterMap.get(key) : beforeMap.get(key));
      if (hasBefore && hasAfter) {
        const inner = DiffView({ before: beforeMap.get(key), after: afterMap.get(key), resolve });
        if (inner) rows.push(
          <div key={key} className="rounded border border-zinc-800/80 bg-zinc-900/40 px-2 py-1">
            {label && <p className="mb-0.5 font-semibold text-zinc-300">{label}</p>}
            {inner}
          </div>
        );
      } else if (hasBefore) {
        rows.push(
          <div key={key} className="rounded border border-red-500/20 bg-red-500/5 px-2 py-1 text-red-300/90">
            <p className="mb-0.5 font-semibold">− Removed{label ? `: ${label}` : ""}</p>
            <ValueView value={beforeMap.get(key)} resolve={resolve} />
          </div>
        );
      } else {
        rows.push(
          <div key={key} className="rounded border border-emerald-500/20 bg-emerald-500/5 px-2 py-1 text-emerald-300/90">
            <p className="mb-0.5 font-semibold">+ Added{label ? `: ${label}` : ""}</p>
            <ValueView value={afterMap.get(key)} resolve={resolve} />
          </div>
        );
      }
    });
    return rows.length > 0 ? <div className="space-y-1">{rows}</div> : null;
  }

  if (isPlainObject(before) && isPlainObject(after)) {
    const rows: ReactNode[] = [];
    new Set([...Object.keys(before), ...Object.keys(after)]).forEach(key => {
      const inner = DiffView({ before: before[key], after: after[key], resolve });
      if (inner) rows.push(
        <FieldRow key={key} label={humanizeKey(key)} block={isBlockValue(before[key]) || isBlockValue(after[key])}>
          {inner}
        </FieldRow>
      );
    });
    return rows.length > 0 ? <div className="space-y-0.5">{rows}</div> : null;
  }

  const beforeText = formatScalar(before, resolve);
  const afterText = formatScalar(after, resolve);
  if (isLongText(beforeText) || isLongText(afterText)) {
    return (
      <div className="space-y-1">
        <p className="whitespace-pre-wrap break-words rounded border border-red-500/20 bg-red-500/5 px-2 py-1 text-red-300/90">− {beforeText}</p>
        <p className="whitespace-pre-wrap break-words rounded border border-emerald-500/20 bg-emerald-500/5 px-2 py-1 text-emerald-300/90">+ {afterText}</p>
      </div>
    );
  }
  return <>{beforeText} <span className="text-zinc-600">→</span> {afterText}</>;
}

function DetailsView({ details, resolve = NO_RESOLVER }: { details: Record<string, unknown>; resolve?: IdResolver }) {
  const { before, after, ...rest } = details;
  const hasChange = "before" in details || "after" in details;
  const restEntries = Object.entries(rest);
  const changeNode = hasChange ? <DiffView before={before} after={after} resolve={resolve} /> : null;

  if (restEntries.length === 0 && !hasChange) return null;
  return (
    <div className="space-y-1">
      {restEntries.map(([k, v]) => (
        <FieldRow key={k} label={humanizeKey(k)} block={isBlockValue(v)}><ValueView value={v} resolve={resolve} /></FieldRow>
      ))}
      {hasChange && (
        changeNode
          ? <div className={cn("space-y-1", restEntries.length > 0 && "mt-1.5 border-t border-zinc-800/60 pt-1.5")}>{changeNode}</div>
          : <p className="italic text-zinc-600">no changes</p>
      )}
    </div>
  );
}

// ── Pagination ────────────────────────────────────────────────────────────────

function Pagination({ page, total, onChange }: { page: number; total: number; onChange: (p: number) => void }) {
  const totalPages = Math.ceil(total / PAGE_SIZE);
  if (totalPages <= 1) return null;

  const start = (page - 1) * PAGE_SIZE + 1;
  const end = Math.min(page * PAGE_SIZE, total);

  const pages: (number | "…")[] = [];
  if (totalPages <= 7) {
    for (let i = 1; i <= totalPages; i++) pages.push(i);
  } else {
    pages.push(1);
    if (page > 3) pages.push("…");
    for (let i = Math.max(2, page - 1); i <= Math.min(totalPages - 1, page + 1); i++) pages.push(i);
    if (page < totalPages - 2) pages.push("…");
    pages.push(totalPages);
  }

  return (
    <div className="flex items-center justify-between border border-t-0 border-zinc-800 bg-zinc-950/30 px-4 py-3 rounded-b-xl">
      <span className="text-xs text-zinc-500">{start}–{end} of {total}</span>
      <div className="flex items-center gap-1">
        <button onClick={() => onChange(page - 1)} disabled={page === 1}
          className="flex h-7 w-7 items-center justify-center rounded border border-zinc-700 bg-zinc-800 text-zinc-400 transition-colors hover:border-zinc-500 hover:text-zinc-200 disabled:cursor-not-allowed disabled:opacity-30">
          <ChevronLeft className="h-3.5 w-3.5" />
        </button>
        {pages.map((p, i) =>
          p === "…" ? (
            <span key={`ellipsis-${i}`} className="flex h-7 w-7 items-center justify-center text-xs text-zinc-600">…</span>
          ) : (
            <button key={p} onClick={() => onChange(p)}
              className={cn(
                "flex h-7 w-7 items-center justify-center rounded border text-xs font-semibold transition-colors",
                p === page
                  ? "border-rose-500 bg-rose-600 text-white"
                  : "border-zinc-700 bg-zinc-800 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200"
              )}>
              {p}
            </button>
          )
        )}
        <button onClick={() => onChange(page + 1)} disabled={page === totalPages}
          className="flex h-7 w-7 items-center justify-center rounded border border-zinc-700 bg-zinc-800 text-zinc-400 transition-colors hover:border-zinc-500 hover:text-zinc-200 disabled:cursor-not-allowed disabled:opacity-30">
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

function SearchBar({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <div className="relative flex-1 max-w-sm">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-500 pointer-events-none" />
      <input
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full rounded-lg border border-zinc-700 bg-zinc-800 pl-9 pr-8 py-2 text-xs text-zinc-200 placeholder-zinc-600 outline-none focus:border-indigo-500 transition-colors"
      />
      {value && (
        <button onClick={() => onChange("")} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-zinc-300 transition-colors">
          <X className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  );
}

function FilterSelect({ value, onChange, options }: {
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)}
      className="rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-xs text-zinc-200 outline-none focus:border-indigo-500 transition-colors cursor-pointer">
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

// ── Table components ──────────────────────────────────────────────────────────

function LoginsTable({ entries }: { entries: AccessLog[] }) {
  return (
    <TableShell headers={["Time", "User", "IP", "Action"]} empty={entries.length === 0} emptyLabel="login logs">
      {entries.map(log => (
        <tr key={log.id} className="bg-zinc-950/30 hover:bg-zinc-900/70">
          <td className="whitespace-nowrap px-4 py-3 text-xs text-zinc-400">{fmt(log.created_at)}</td>
          <td className="px-4 py-3">
            <p className="font-medium text-zinc-200">{log.username}</p>
            <p className="font-mono text-[11px] text-zinc-600">{log.discord_id}</p>
          </td>
          <td className="px-4 py-3 font-mono text-xs text-zinc-500">{log.ip ?? "—"}</td>
          <td className="px-4 py-3 text-zinc-300">
            <span className="inline-flex items-center gap-1.5">
              <LogIn className="h-3.5 w-3.5 text-emerald-400" />
              {log.action === "login" ? "Logged into dashboard" : log.action}
            </span>
          </td>
        </tr>
      ))}
    </TableShell>
  );
}

function DashboardTable({ entries, resolve }: { entries: AuditLog[]; resolve: IdResolver }) {
  return (
    <TableShell headers={["Time", "User", "Action", "Details"]} empty={entries.length === 0} emptyLabel="dashboard logs">
      {entries.map(log => (
        <tr key={log.id} className="bg-zinc-950/30 align-top hover:bg-zinc-900/70">
          <td className="whitespace-nowrap px-4 py-3 text-xs text-zinc-400">{fmt(log.created_at)}</td>
          <td className="px-4 py-3">
            <p className="font-medium text-zinc-200">{log.username ?? "Unknown user"}</p>
            <p className="font-mono text-[11px] text-zinc-600">{log.user_id ?? "-"}</p>
          </td>
          <td className="px-4 py-3 font-medium text-indigo-300">
            <span className="inline-flex items-center gap-1.5">
              <Settings2 className="h-3.5 w-3.5" /> {log.action}
            </span>
          </td>
          <td className="max-w-sm px-4 py-3 text-xs text-zinc-500 break-words">
            {(log.channel_name || log.channel_id) && (
              <p className="mb-1">#{log.channel_name ?? log.channel_id}</p>
            )}
            {log.details && <DetailsView details={log.details} resolve={resolve} />}
          </td>
        </tr>
      ))}
    </TableShell>
  );
}

function cleanReason(reason: string): string {
  return reason
    .replace(/<#(\d+)>/g, "#$1")
    .replace(/<@!?(\d+)>/g, "@$1")
    .replace(/<@&(\d+)>/g, "@role:$1")
    .trim();
}

function WarningsTable({ logs, warnCounts }: { logs: WarningLog[]; warnCounts: Record<string, number> }) {
  return (
    <TableShell headers={["Time", "Warning ID", "User", "Moderator", "Reason"]} empty={logs.length === 0} emptyLabel="warnings">
      {logs.map(log => (
        <tr key={log.id} className="bg-zinc-950/30 align-top hover:bg-zinc-900/70">
          <td className="whitespace-nowrap px-4 py-3 text-xs text-zinc-400">{fmt(log.created_at)}</td>
          <td className="whitespace-nowrap px-4 py-3 font-mono text-xs font-bold text-amber-400">#{log.id}</td>
          <td className="px-4 py-3">
            <span className="font-medium text-zinc-200">{log.target_name ?? "Unknown user"}</span>
            {warnCounts[log.discord_id] > 1 && (
              <span className="ml-2 rounded px-1.5 py-0.5 text-[11px] font-bold bg-amber-500/15 text-amber-400">
                {warnCounts[log.discord_id]}×
              </span>
            )}
          </td>
          <td className="px-4 py-3 text-zinc-300">{log.moderator_name ?? "Unknown moderator"}</td>
          <td className="max-w-md whitespace-pre-wrap px-4 py-3 text-zinc-300">{cleanReason(log.reason)}</td>
        </tr>
      ))}
    </TableShell>
  );
}

type RawOption = { name: string; type: number; value?: string; options?: RawOption[] };

function formatArgs(options: RawOption[]): string {
  if (!options || options.length === 0) return "";
  return options.map(opt => {
    if (opt.options && opt.options.length > 0) {
      const sub = opt.options.filter(o => o.value !== undefined).map(o => `${o.name}: ${o.value}`).join(" · ");
      return sub ? `${opt.name} · ${sub}` : opt.name;
    }
    return opt.value !== undefined ? `${opt.name}: ${opt.value}` : opt.name;
  }).join(" · ");
}

function ActionAuditTable({ logs, label }: { logs: AuditLog[]; label: string }) {
  return (
    <TableShell headers={["Time", "Affected User", "Channel", "Action", "Trigger", "Result"]} empty={logs.length === 0} emptyLabel={`${label} logs`}>
      {logs.map(log => {
        const status = String(log.details?.status ?? "completed");
        const trigger = log.details?.trigger ?? log.details?.reason ?? log.details?.message;
        const result = log.details?.result ?? (log.details?.timedOut ? `Timed out for 24 hours; ${String(log.details?.messagesDeleted ?? 0)} messages deleted` : null);
        const error = log.details?.error ? String(log.details.error) : null;
        return (
          <tr key={log.id} className="bg-zinc-950/30 align-top hover:bg-zinc-900/70">
            <td className="whitespace-nowrap px-4 py-3 text-xs text-zinc-400">{fmt(log.created_at)}</td>
            <td className="px-4 py-3">
              <p className="font-medium text-zinc-200">{log.username ?? "System"}</p>
              <p className="font-mono text-[11px] text-zinc-600">{log.user_id ?? "-"}</p>
            </td>
            <td className="px-4 py-3 text-zinc-400">
              {(log.channel_name || log.channel_id)
                ? <span>#{log.channel_name ?? log.channel_id}</span>
                : <span className="text-zinc-600">—</span>}
            </td>
            <td className="px-4 py-3 font-semibold text-red-400">{log.action}</td>
            <td className="max-w-sm whitespace-pre-wrap break-words px-4 py-3 text-zinc-400">
              <DetailValue value={trigger} />
            </td>
            <td className="max-w-sm px-4 py-3 text-xs text-zinc-400">
              <span className={cn(
                "rounded px-2 py-1 text-[11px] font-bold uppercase",
                status === "success" ? "bg-emerald-500/10 text-emerald-400"
                  : status === "failed" ? "bg-red-500/10 text-red-400"
                  : "bg-amber-500/10 text-amber-400",
              )}>
                {status}
              </span>
              <p className="mt-2 whitespace-pre-wrap break-words"><DetailValue value={result} /></p>
              {error && <p className="mt-1 whitespace-pre-wrap break-words text-red-400">{error}</p>}
            </td>
          </tr>
        );
      })}
    </TableShell>
  );
}

function CommandsTable({ logs }: { logs: AuditLog[] }) {
  return (
    <TableShell headers={["Time", "User", "Channel", "Command", "Status", "Duration"]} empty={logs.length === 0} emptyLabel="command logs">
      {logs.map(log => {
        const status = String(log.details?.status ?? "unknown");
        const error = log.details?.error ? String(log.details.error) : null;
        const blockedReason = log.details?.blockedReason ? String(log.details.blockedReason) : null;
        const args = log.details?.options ? formatArgs(log.details.options as RawOption[]) : "";
        return (
          <tr key={log.id} className="bg-zinc-950/30 hover:bg-zinc-900/70">
            <td className="whitespace-nowrap px-4 py-3 text-xs text-zinc-400">{fmt(log.created_at)}</td>
            <td className="px-4 py-3">
              <p className="font-medium text-zinc-200">{log.username ?? "Unknown user"}</p>
              <p className="font-mono text-[11px] text-zinc-600">{log.user_id}</p>
            </td>
            <td className="px-4 py-3 text-zinc-400">
              {(log.channel_name || log.channel_id)
                ? <span>#{log.channel_name ?? log.channel_id ?? "unknown"}</span>
                : <span className="text-zinc-600">—</span>}
            </td>
            <td className="px-4 py-3">
              <p className="font-mono font-semibold text-indigo-300">{log.action}</p>
              {args && <p className="mt-0.5 text-[11px] text-zinc-500">{args}</p>}
            </td>
            <td className="px-4 py-3">
              <span className={cn(
                "rounded px-2 py-1 text-[11px] font-bold uppercase",
                status === "success" ? "bg-emerald-500/10 text-emerald-400"
                  : status === "blocked" ? "bg-amber-500/10 text-amber-400"
                  : "bg-red-500/10 text-red-400",
              )}>
                {status}
              </span>
              {error && <p className="mt-1 max-w-xs text-xs text-red-400">{error}</p>}
              {blockedReason && <p className="mt-1 max-w-xs text-xs text-amber-400">{blockedReason}</p>}
            </td>
            <td className="px-4 py-3 text-xs text-zinc-500">
              <DetailValue value={log.details?.durationMs !== undefined ? `${log.details.durationMs} ms` : null} />
            </td>
          </tr>
        );
      })}
    </TableShell>
  );
}

/** Role/channel/category id -> name, for resolving the raw Discord snowflakes that show up inside
 *  audit log details (allowlists, log-channel routing, {channel=id} message tokens) into something
 *  readable. IDs are unique across all three kinds within a guild, so one merged map is safe. */
function useIdNameMap(guildId: string | null): IdResolver {
  const [map, setMap] = useState<Map<string, string>>(new Map());
  useEffect(() => {
    if (!guildId) { setMap(new Map()); return; }
    let cancelled = false;
    Promise.all([
      adminApi.getDiscordRoles(guildId),
      adminApi.getDiscordTextChannels(guildId),
      adminApi.getDiscordCategories(guildId),
    ]).then(([roles, channels, categories]) => {
      if (cancelled) return;
      const next = new Map<string, string>();
      [...roles, ...channels, ...categories].forEach(item => next.set(item.id, item.name));
      setMap(next);
    }).catch(() => { /* best-effort - falls back to raw IDs */ });
    return () => { cancelled = true; };
  }, [guildId]);
  return useMemo(() => (id: string) => map.get(id) ?? null, [map]);
}

// ── Main page ─────────────────────────────────────────────────────────────────

export function Logs() {
  const guildId = useSelectedGuildId();
  const resolve = useIdNameMap(guildId);
  const [activeTab, setActiveTab] = useState<Tab>("dashboard");
  const [accessLogs, setAccessLogs] = useState<AccessLog[]>([]);
  const [dashboardAuditLogs, setDashboardAuditLogs] = useState<AuditLog[]>([]);
  const [warningLogs, setWarningLogs] = useState<WarningLog[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [automodInfoOpen, setAutomodInfoOpen] = useState(false);

  const [search, setSearch] = useState("");
  const [filterStatus, setFilterStatus] = useState("all");
  const [page, setPage] = useState(1);
  const [showAll, setShowAll] = useState(false);

  useEffect(() => {
    setSearch("");
    setFilterStatus("all");
    setPage(1);
    setShowAll(false);
  }, [activeTab]);

  useEffect(() => { setPage(1); }, [search, filterStatus]);

  useEffect(() => {
    let cancelled = false;
    if (!guildId) {
      setAccessLogs([]);
      setDashboardAuditLogs([]);
      setWarningLogs([]);
      setAuditLogs([]);
      setLoading(false);
      return () => { cancelled = true; };
    }
    setLoading(true);
    setError(null);
    setAccessLogs([]);
    setDashboardAuditLogs([]);
    setWarningLogs([]);
    setAuditLogs([]);
    let request: Promise<unknown>;
    if (activeTab === "dashboard") {
      request = adminApi.getAuditLogs("dashboard", guildId, 1000).then(data => {
        if (!cancelled) setDashboardAuditLogs(data);
      });
    } else if (activeTab === "logins") {
      request = adminApi.getAccessLogs(guildId, 1000).then(data => {
        if (!cancelled) setAccessLogs(data);
      });
    } else if (activeTab === "warnings") {
      request = adminApi.getAdminWarnings(guildId).then(data => {
        if (!cancelled) setWarningLogs(data);
      });
    } else {
      request = adminApi.getAuditLogs(activeTab, guildId, 1000).then(data => {
        if (!cancelled) setAuditLogs(data);
      });
    }
    request
      .catch(err => {
        if (!cancelled) setError(apiErrorMessage(err, (err as { message?: string })?.message ?? "Failed to load logs"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [activeTab, guildId]);

  // ── Filtered data ─────────────────────────────────────────────────────────

  const filteredDashboard = useMemo(() =>
    dashboardAuditLogs.filter(log => {
      if (!search) return true;
      const q = search.toLowerCase();
      return (log.username?.toLowerCase().includes(q) || log.user_id?.includes(q) || log.action?.toLowerCase().includes(q)) ?? false;
    }),
  [dashboardAuditLogs, search]);

  const filteredLogins = useMemo(() =>
    accessLogs.filter(log => {
      if (!search) return true;
      const q = search.toLowerCase();
      return (log.username?.toLowerCase().includes(q) || log.discord_id?.includes(q) || log.ip?.includes(q) || log.action?.toLowerCase().includes(q)) ?? false;
    }),
  [accessLogs, search]);

  const filteredWarnings = useMemo(() =>
    warningLogs.filter(log => {
      if (!search) return true;
      const q = search.toLowerCase();
      return (
        log.target_name?.toLowerCase().includes(q) ||
        log.discord_id?.includes(q) ||
        log.moderator_name?.toLowerCase().includes(q) ||
        log.moderator_id?.includes(q) ||
        log.reason?.toLowerCase().includes(q)
      ) ?? false;
    }),
  [warningLogs, search]);

  const warnCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    warningLogs.forEach(log => { counts[log.discord_id] = (counts[log.discord_id] || 0) + 1; });
    return counts;
  }, [warningLogs]);

  const filteredAutomod = useMemo(() =>
    auditLogs.filter(log => {
      const status = String(log.details?.status ?? "completed");
      if (filterStatus !== "all" && status !== filterStatus) return false;
      if (!search) return true;
      const q = search.toLowerCase();
      const trigger = String(log.details?.trigger ?? log.details?.reason ?? log.details?.message ?? "");
      return (
        log.username?.toLowerCase().includes(q) ||
        log.user_id?.includes(q) ||
        log.action?.toLowerCase().includes(q) ||
        log.channel_name?.toLowerCase().includes(q) ||
        trigger.toLowerCase().includes(q)
      ) ?? false;
    }),
  [auditLogs, search, filterStatus]);

  const filteredCommands = useMemo(() =>
    auditLogs.filter(log => {
      const status = String(log.details?.status ?? "unknown");
      if (filterStatus !== "all" && status !== filterStatus) return false;
      if (!search) return true;
      const q = search.toLowerCase();
      const args = log.details?.options ? JSON.stringify(log.details.options).toLowerCase() : "";
      return (
        log.username?.toLowerCase().includes(q) ||
        log.user_id?.includes(q) ||
        log.action?.toLowerCase().includes(q) ||
        log.channel_name?.toLowerCase().includes(q) ||
        args.includes(q)
      ) ?? false;
    }),
  [auditLogs, search, filterStatus]);

  // ── Paged slices ──────────────────────────────────────────────────────────

  const activeFiltered =
    activeTab === "dashboard" ? filteredDashboard
    : activeTab === "logins" ? filteredLogins
    : activeTab === "warnings" ? filteredWarnings
    : activeTab === "automod" || activeTab === "verification" ? filteredAutomod
    : filteredCommands;

  const totalFiltered = activeFiltered.length;
  const slice = (arr: unknown[]) => showAll ? arr : arr.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const pagedDashboard = slice(filteredDashboard) as AuditLog[];
  const pagedLogins = slice(filteredLogins) as AccessLog[];
  const pagedWarnings = slice(filteredWarnings) as WarningLog[];
  const pagedAutomod = slice(filteredAutomod) as AuditLog[];
  const pagedCommands = slice(filteredCommands) as AuditLog[];

  // ── Render ────────────────────────────────────────────────────────────────

  const searchPlaceholder =
    activeTab === "dashboard" ? "Search user, action…"
    : activeTab === "logins" ? "Search user, IP…"
    : activeTab === "warnings" ? "Search user, moderator, reason…"
    : activeTab === "automod" || activeTab === "verification" ? "Search user, action, trigger…"
    : "Search user, command, args…";

  if (!guildId) {
    return (
      <div className="min-h-screen md:pl-64">
        <div className="border-b border-zinc-800 px-4 pb-4 pt-5 sm:px-6">
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Logs</h1>
          <p className="mt-1 text-sm text-zinc-500">Dashboard and Discord bot audit history.</p>
        </div>
        <div className="px-4 py-5 sm:px-6">
          <div className="rounded-lg border border-zinc-800 bg-zinc-900 p-5 text-sm text-zinc-400">
            No server selected. Pick a server from the switcher above to view its logs.
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen md:pl-64">
      <div className="border-b border-zinc-800 px-4 pb-4 pt-5 sm:px-6">
        <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Logs</h1>
        <p className="mt-1 text-sm text-zinc-500">Dashboard and Discord bot audit history.</p>
      </div>

      <div className="px-4 py-5 sm:px-6">
        {/* Tab bar */}
        <div className="mb-6 grid grid-cols-3 gap-2 sm:flex sm:flex-wrap">
          {TABS.map(tab => {
            const Icon = tab.icon;
            return (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)}
                className={cn(
                  "flex items-center justify-center gap-2 rounded-lg border px-5 py-3 text-sm font-semibold transition-colors sm:min-w-36",
                  activeTab === tab.id
                    ? "border-rose-500 bg-rose-600 text-white"
                    : "border-zinc-700 bg-zinc-800 text-zinc-300 hover:border-zinc-600 hover:bg-zinc-700",
                )}>
                <Icon className="h-4 w-4" /> {tab.label}
              </button>
            );
          })}
        </div>

        {/* Header + search + filters */}
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 mr-auto">
            <h2 className="text-xl font-bold text-zinc-100">{TABS.find(t => t.id === activeTab)?.label} Logs</h2>
            {activeTab === "automod" && (
              <button type="button" onClick={() => setAutomodInfoOpen(true)}
                className="flex h-7 w-7 items-center justify-center rounded-full border border-zinc-700 bg-zinc-800 text-zinc-400 transition-colors hover:border-indigo-500/60 hover:bg-indigo-500/10 hover:text-indigo-300">
                <Info className="h-4 w-4" />
              </button>
            )}
          </div>

          {!loading && !error && (
            <>
              <SearchBar value={search} onChange={setSearch} placeholder={searchPlaceholder} />

              {(activeTab === "commands") && (
                <FilterSelect value={filterStatus} onChange={setFilterStatus} options={[
                  { value: "all", label: "All statuses" },
                  { value: "success", label: "Success" },
                  { value: "blocked", label: "Blocked" },
                  { value: "rejected", label: "Rejected" },
                  { value: "failed", label: "Failed" },
                  { value: "error", label: "Error" },
                ]} />
              )}
              {(activeTab === "automod" || activeTab === "verification") && (
                <FilterSelect value={filterStatus} onChange={setFilterStatus} options={[
                  { value: "all", label: "All statuses" },
                  { value: "success", label: "Success" },
                  { value: "failed", label: "Failed" },
                  { value: "completed", label: "Completed" },
                ]} />
              )}

              <span className="text-xs text-zinc-600 whitespace-nowrap">
                {totalFiltered} {search || filterStatus !== "all" ? "matching" : ""} entries
              </span>
              <button onClick={() => { setShowAll(v => !v); setPage(1); }}
                className={cn(
                  "whitespace-nowrap rounded-lg border px-3 py-2 text-xs font-semibold transition-colors",
                  showAll
                    ? "border-indigo-500/60 bg-indigo-500/10 text-indigo-300 hover:bg-indigo-500/20"
                    : "border-zinc-700 bg-zinc-800 text-zinc-400 hover:border-zinc-600 hover:text-zinc-200"
                )}>
                {showAll ? "Paginate" : "Show all"}
              </button>
            </>
          )}
        </div>

        {loading && (
          <div className="flex min-h-72 items-center justify-center rounded-xl border border-zinc-800 bg-zinc-950/30 text-sm text-zinc-500">
            <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Loading logs...
          </div>
        )}
        {error && (
          <div className="flex items-center gap-2 rounded-lg border border-red-500/20 bg-red-500/10 p-4 text-sm text-red-400">
            <AlertCircle className="h-4 w-4 flex-shrink-0" /> {error}
          </div>
        )}

        {!loading && !error && activeTab === "dashboard" && (
          <>
            <DashboardTable entries={pagedDashboard} resolve={resolve} />
            <Pagination page={page} total={totalFiltered} onChange={setPage} />
          </>
        )}
        {!loading && !error && activeTab === "logins" && (
          <>
            <LoginsTable entries={pagedLogins} />
            <Pagination page={page} total={totalFiltered} onChange={setPage} />
          </>
        )}
        {!loading && !error && activeTab === "warnings" && (
          <>
            <WarningsTable logs={pagedWarnings} warnCounts={warnCounts} />
            <Pagination page={page} total={totalFiltered} onChange={setPage} />
          </>
        )}
        {!loading && !error && activeTab === "automod" && (
          <>
            <ActionAuditTable logs={pagedAutomod} label="automod" />
            <Pagination page={page} total={totalFiltered} onChange={setPage} />
          </>
        )}
        {!loading && !error && activeTab === "verification" && (
          <>
            <ActionAuditTable logs={pagedAutomod} label="verification" />
            <Pagination page={page} total={totalFiltered} onChange={setPage} />
          </>
        )}
        {!loading && !error && activeTab === "commands" && (
          <>
            <CommandsTable logs={pagedCommands} />
            <Pagination page={page} total={totalFiltered} onChange={setPage} />
          </>
        )}
      </div>

      <Dialog open={automodInfoOpen} onOpenChange={setAutomodInfoOpen}>
        <DialogContent className="max-w-lg border-zinc-700 bg-zinc-900 text-zinc-100">
          <DialogHeader>
            <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-indigo-500/10 text-indigo-300">
              <ShieldAlert className="h-5 w-5" />
            </div>
            <DialogTitle>What is logged in Automod?</DialogTitle>
            <DialogDescription className="leading-relaxed text-zinc-400">
              Automod records actions and detections performed automatically by the bot, without a moderator directly running that action.
            </DialogDescription>
          </DialogHeader>
          <ul className="mt-2 space-y-2 text-sm text-zinc-300">
            {AUTOMOD_LOGGED_ACTIONS.map(action => (
              <li key={action} className="flex gap-2 rounded-lg border border-zinc-800 bg-zinc-950/40 px-3 py-2.5">
                <span className="mt-1.5 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-indigo-400" />
                <span>{action}</span>
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-zinc-500">
            Manual slash-command activity is listed under Commands, while dashboard configuration changes are listed under Dashboard.
          </p>
        </DialogContent>
      </Dialog>
    </div>
  );
}
