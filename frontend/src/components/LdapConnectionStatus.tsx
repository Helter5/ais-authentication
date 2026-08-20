import { useEffect, useState } from "react";
import { Loader2, Wifi, WifiOff } from "lucide-react";
import { cn } from "@/lib/utils";
import { adminApi, type LdapStatus, type LdapStatusBucket, type LdapStatusRange } from "@/lib/api";

const RANGE_STORAGE_KEY = "ldap_status_range";
const RANGES: { id: LdapStatusRange; label: string }[] = [
  { id: "hour", label: "Hour" },
  { id: "day", label: "Day" },
  { id: "week", label: "Week" },
];

function isLdapStatusRange(value: string | null): value is LdapStatusRange {
  return value === "hour" || value === "day" || value === "week";
}

function fmtTime(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function bucketColor(bucket: LdapStatusBucket): string {
  const total = bucket.successCount + bucket.failCount;
  if (total === 0) return "bg-zinc-800";
  if (bucket.failCount === 0) return "bg-emerald-500";
  if (bucket.successCount === 0) return "bg-red-500";
  return "bg-amber-500"; // partial outage within the bucket
}

function bucketTooltip(bucket: LdapStatusBucket): string {
  const total = bucket.successCount + bucket.failCount;
  const time = new Date(bucket.bucketStart).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  if (total === 0) return `${time}\nNo data`;
  const latency = bucket.avgLatencyMs !== null ? `, avg ${bucket.avgLatencyMs} ms` : "";
  return `${time}\n${bucket.successCount} up / ${bucket.failCount} down${latency}`;
}

/**
 * bucketLabel/labelStep together decide the sparse label row under the bars - one label per bar
 * would overlap at 60 (hour) or 24 (day) bars, so only every Nth bucket gets a visible label. Week
 * has just 7 buckets, room for all of them.
 */
function bucketLabel(range: LdapStatusRange, bucket: LdapStatusBucket): string {
  const date = new Date(bucket.bucketStart);
  if (range === "hour") return date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  if (range === "day") return date.toLocaleTimeString(undefined, { hour: "2-digit" });
  return date.toLocaleDateString(undefined, { weekday: "short" });
}

function labelStep(range: LdapStatusRange): number {
  if (range === "hour") return 10; // 60 buckets -> label every 10th (6 labels)
  if (range === "day") return 4;   // 24 buckets -> label every 4th (6 labels)
  return 1;                        // 7 buckets -> label every one
}

/**
 * Self-contained LDAP uptime widget: owns its own hour/day/week range (persisted to
 * localStorage so it survives a reload) and polls on the same 60s cadence LdapUptimeProbeJob
 * writes new samples on. No outer card/section wrapper of its own, so callers can drop it into
 * whatever container matches their page's layout.
 */
export function LdapConnectionStatus() {
  const [range, setRange] = useState<LdapStatusRange>(() => {
    const stored = localStorage.getItem(RANGE_STORAGE_KEY);
    return isLdapStatusRange(stored) ? stored : "day";
  });
  const [status, setStatus] = useState<LdapStatus | null>(null);

  useEffect(() => {
    localStorage.setItem(RANGE_STORAGE_KEY, range);
    let cancelled = false;
    const load = () => adminApi.getLdapStatus(range).then(data => { if (!cancelled) setStatus(data); }).catch(() => { /* keep showing the last known status */ });
    load();
    const interval = setInterval(load, 60_000);
    return () => { cancelled = true; clearInterval(interval); };
  }, [range]);

  const step = labelStep(range);

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-4">
        {status ? (
          <>
            <span className={cn(
              "inline-flex items-center gap-1.5 rounded px-2.5 py-1 text-xs font-bold uppercase",
              status.currentlyUp ? "bg-emerald-500/15 text-emerald-400" : "bg-red-500/15 text-red-400",
            )}>
              {status.currentlyUp ? <Wifi className="h-3.5 w-3.5" /> : <WifiOff className="h-3.5 w-3.5" />}
              {status.currentlyUp ? "Up" : "Down"}
            </span>
            <span className="text-xs text-zinc-500">
              {status.lastCheckedAt ? `Last checked ${fmtTime(status.lastCheckedAt)}` : "No samples yet"}
              {status.lastLatencyMs !== null && status.currentlyUp && ` · ${status.lastLatencyMs} ms`}
            </span>
            <span className="text-xs font-semibold text-zinc-300">
              {status.uptimePercent.toFixed(1)}% uptime
            </span>
          </>
        ) : (
          <span className="flex items-center gap-2 text-xs text-zinc-500">
            <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading LDAP status...
          </span>
        )}

        <div className="ml-auto flex gap-1 rounded-lg border border-zinc-800 bg-zinc-950/40 p-0.5">
          {RANGES.map(r => (
            <button
              key={r.id}
              onClick={() => setRange(r.id)}
              className={cn(
                "rounded px-2.5 py-1 text-[11px] font-semibold transition-colors",
                range === r.id ? "bg-indigo-600 text-white" : "text-zinc-400 hover:text-zinc-200",
              )}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {status && (
        <>
          <div className="flex h-10 items-end gap-0.5">
            {status.buckets.map(bucket => (
              <div
                key={bucket.bucketStart}
                title={bucketTooltip(bucket)}
                className={cn("h-full flex-1 rounded-sm transition-opacity hover:opacity-75", bucketColor(bucket))}
              />
            ))}
          </div>
          <div className="mt-1 flex gap-0.5">
            {status.buckets.map((bucket, i) => (
              <span key={bucket.bucketStart} className="flex-1 text-center text-[9px] text-zinc-600">
                {i % step === 0 ? bucketLabel(range, bucket) : ""}
              </span>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
