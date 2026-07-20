import { useEffect, useMemo, useState, type ElementType, type ReactNode } from "react";
import {
  AlertCircle, Bot, Command, Gauge, Info, Loader2, LogIn, Settings2,
  ShieldAlert, TriangleAlert, Search, ChevronLeft, ChevronRight, X, UserCheck,
} from "lucide-react";
import { adminApi, type AuditLog } from "@/lib/api";
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

function DetailValue({ value }: { value: unknown }) {
  if (value === null || value === undefined || value === "") return <span className="text-zinc-600">-</span>;
  return <>{String(value)}</>;
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

function DashboardTable({ entries }: { entries: AuditLog[] }) {
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
          <td className="max-w-xs px-4 py-3 text-xs text-zinc-500">
            {(log.channel_name || log.channel_id) && (
              <p className="mb-1">#{log.channel_name ?? log.channel_id}</p>
            )}
            {log.details && (
              <p className="whitespace-pre-wrap break-words">
                {Object.entries(log.details).map(([k, v]) => `${k}: ${String(v)}`).join(" · ")}
              </p>
            )}
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

// ── Main page ─────────────────────────────────────────────────────────────────

export function Logs() {
  const guildId = useSelectedGuildId();
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
        if (!cancelled) setError((err as { response?: { data?: { error?: string } }; message?: string })?.response?.data?.error ?? (err as { message?: string })?.message ?? "Failed to load logs");
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
            <DashboardTable entries={pagedDashboard} />
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
