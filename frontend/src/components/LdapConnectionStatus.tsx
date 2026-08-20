import { Loader2, Wifi, WifiOff } from "lucide-react";
import { cn } from "@/lib/utils";
import type { LdapStatus, LdapStatusBucket } from "@/lib/api";

function fmtTime(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function bucketColor(bucket: LdapStatusBucket): string {
  const total = bucket.successCount + bucket.failCount;
  if (total === 0) return "bg-zinc-800";
  if (bucket.failCount === 0) return "bg-emerald-500";
  if (bucket.successCount === 0) return "bg-red-500";
  return "bg-amber-500"; // partial outage within the hour
}

function bucketTitle(bucket: LdapStatusBucket): string {
  const total = bucket.successCount + bucket.failCount;
  const time = new Date(bucket.bucketStart).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  if (total === 0) return `${time}\nNo data`;
  const latency = bucket.avgLatencyMs !== null ? `, avg ${bucket.avgLatencyMs} ms` : "";
  return `${time}\n${bucket.successCount} up / ${bucket.failCount} down${latency}`;
}

/**
 * Content-only LDAP uptime widget (badge + 24h hourly bar chart) - no card/section wrapper of its
 * own, so callers can drop it into whatever container matches their page's layout (e.g. nested
 * inside Dashboard's "Bot Status" section instead of getting its own top-level card).
 */
export function LdapConnectionStatus({ status }: { status: LdapStatus | null }) {
  if (!status) {
    return (
      <div className="flex items-center gap-2 text-xs text-zinc-500">
        <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading LDAP status...
      </div>
    );
  }

  const StatusIcon = status.currentlyUp ? Wifi : WifiOff;

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-4">
        <span className={cn(
          "inline-flex items-center gap-1.5 rounded px-2.5 py-1 text-xs font-bold uppercase",
          status.currentlyUp ? "bg-emerald-500/15 text-emerald-400" : "bg-red-500/15 text-red-400",
        )}>
          <StatusIcon className="h-3.5 w-3.5" /> {status.currentlyUp ? "Up" : "Down"}
        </span>
        <span className="text-xs text-zinc-500">
          {status.lastCheckedAt ? `Last checked ${fmtTime(status.lastCheckedAt)}` : "No samples yet"}
          {status.lastLatencyMs !== null && status.currentlyUp && ` · ${status.lastLatencyMs} ms`}
        </span>
        <span className="ml-auto text-xs font-semibold text-zinc-300">
          {status.uptimePercent.toFixed(1)}% uptime (24h)
        </span>
      </div>

      <div className="flex h-10 items-end gap-0.5">
        {status.buckets.map(bucket => (
          <div
            key={bucket.bucketStart}
            title={bucketTitle(bucket)}
            className={cn("h-full flex-1 rounded-sm transition-opacity hover:opacity-75", bucketColor(bucket))}
          />
        ))}
      </div>
      <div className="mt-1.5 flex justify-between text-[10px] text-zinc-600">
        <span>{status.buckets.length > 0 ? fmtTime(status.buckets[0].bucketStart) : ""}</span>
        <span>now</span>
      </div>
    </div>
  );
}
