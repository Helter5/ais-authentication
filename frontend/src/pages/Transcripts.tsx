import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { adminApi, type TicketSummary } from "@/lib/api";
import { cn } from "@/lib/utils";
import { FileText, Search, X } from "lucide-react";
import { format, differenceInCalendarDays } from "date-fns";
import { useSelectedGuildId } from "@/components/modules/shared";
import { usePagination, PaginationControls } from "@/components/ui/pagination";

function ExpiryCell({ expiresAt }: { expiresAt: string | null }) {
  if (!expiresAt) return <span className="text-zinc-600">Never</span>;
  const daysLeft = differenceInCalendarDays(new Date(expiresAt), new Date());
  return (
    <div>
      <div>{format(new Date(expiresAt), "dd/MM HH:mm")}</div>
      <div className={cn("text-[11px]", daysLeft < 0 ? "text-red-400" : daysLeft <= 7 ? "text-amber-400" : "text-zinc-600")}>
        {daysLeft < 0 ? "Expired" : daysLeft === 0 ? "Expires today" : `${daysLeft} day${daysLeft === 1 ? "" : "s"} left`}
      </div>
    </div>
  );
}

export function Transcripts() {
  const currentGuildId = useSelectedGuildId();
  const [data, setData] = useState<TicketSummary[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const itemsPerPage = 30;

  useEffect(() => {
    let cancelled = false;
    setData([]);
    setLoading(true);
    if (!currentGuildId) { setLoading(false); return () => { cancelled = true; }; }
    adminApi.listTicketTranscripts(currentGuildId)
      .then(rows => { if (!cancelled) setData(rows); })
      .catch(error => { if (!cancelled) console.error(error); })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [currentGuildId]);

  const filtered = data.filter(t => {
    const q = search.toLowerCase();
    if (!q) return true;
    return t.userId.includes(search)
      || (t.username?.toLowerCase().includes(q) ?? false)
      || t.channelId.includes(search)
      || (t.closedByUsername?.toLowerCase().includes(q) ?? false);
  });

  const { currentPage, setCurrentPage, showAll, setShowAll, totalPages, paged, pageNumbers } = usePagination(filtered, itemsPerPage);

  useEffect(() => { setCurrentPage(1); }, [search, setCurrentPage]);

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      <div className="flex flex-wrap items-center gap-3 px-6 py-4 border-b border-zinc-800 flex-shrink-0">
        <FileText className="w-5 h-5 text-indigo-400 flex-shrink-0" />
        <h1 className="text-xl font-bold text-zinc-100 tracking-tight">Ticket Transcripts</h1>
        {!loading && <span className="text-[11px] text-zinc-500">{data.length} saved</span>}
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-zinc-500 pointer-events-none" />
            <input type="search" placeholder="Search…" value={search} onChange={e => setSearch(e.target.value)}
              className="w-52 pl-8 pr-3 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors" />
          </div>
          {search && (
            <button onClick={() => setSearch("")}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded text-xs text-zinc-500 hover:text-zinc-200 border border-zinc-700 hover:border-zinc-600 transition-colors">
              <X className="w-3 h-3" /> Reset
            </button>
          )}
        </div>
      </div>

      {!currentGuildId ? (
        <div className="flex items-center justify-center h-40 text-zinc-600 text-sm">No server selected.</div>
      ) : loading ? (
        <div className="flex items-center justify-center h-40 text-zinc-600 text-sm animate-pulse">Loading…</div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className="border-b border-zinc-800">
                  <th className="text-left px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap">User</th>
                  <th className="text-left px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap hidden sm:table-cell">Closed By</th>
                  <th className="text-left px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap">Created</th>
                  <th className="text-left px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap">Closed</th>
                  <th className="text-left px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap">Expires</th>
                  <th className="text-right px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap"></th>
                </tr>
              </thead>
              <tbody>
                {paged.length === 0 ? (
                  <tr><td colSpan={6} className="h-32 text-center text-zinc-600">No saved transcripts found.</td></tr>
                ) : paged.map(item => (
                  <tr key={item.channelId} className="border-b border-zinc-800/50 hover:bg-zinc-800/30 transition-colors">
                    <td className="px-4 py-2.5">
                      <div className="font-medium text-zinc-300">{item.username ?? "Unknown"}</div>
                      <div className="font-mono text-[11px] text-zinc-600">{item.userId}</div>
                    </td>
                    <td className="px-4 py-2.5 text-zinc-500 hidden sm:table-cell">
                      {item.closedByUsername ?? item.closedBy ?? "-"}
                    </td>
                    <td className="px-4 py-2.5 text-zinc-500 tabular-nums">{format(new Date(item.createdAt), "dd/MM HH:mm")}</td>
                    <td className="px-4 py-2.5 text-zinc-500 tabular-nums">
                      {item.closedAt ? format(new Date(item.closedAt), "dd/MM HH:mm") : "-"}
                    </td>
                    <td className="px-4 py-2.5 tabular-nums">
                      <ExpiryCell expiresAt={item.expiresAt} />
                    </td>
                    <td className="px-4 py-2.5 text-right">
                      <Link to={`/tickets/${item.channelId}?guildId=${item.guildId}`}
                        className="text-indigo-400 hover:text-indigo-300 hover:underline font-semibold">
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <PaginationControls
            currentPage={currentPage} setCurrentPage={setCurrentPage} totalPages={totalPages}
            showAll={showAll} setShowAll={setShowAll} pageNumbers={pageNumbers}
            totalCount={filtered.length} itemsPerPage={itemsPerPage} />
        </>
      )}
    </div>
  );
}
