import { useEffect, useState } from "react";
import { adminApi, type VerifiedUser } from "@/lib/api";
import { cn } from "@/lib/utils";
import { Search, UserCheck, X, ArrowUpDown, ArrowUp, ArrowDown } from "lucide-react";
import { format } from "date-fns";
import { useSelectedGuildId } from "@/components/modules/shared";
import { usePagination, PaginationControls } from "@/components/ui/pagination";

export function Users() {
  const currentGuildId = useSelectedGuildId();
  const [data, setData] = useState<VerifiedUser[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const guildFilter = currentGuildId || null;
  const [dateFilter, setDateFilter] = useState<string | null>(null);
  const [aisIdSort, setAisIdSort] = useState<"asc" | "desc" | null>(null);
  const itemsPerPage = 30;

  useEffect(() => {
    let cancelled = false;
    setData([]);
    setLoading(true);
    if (!currentGuildId) { setLoading(false); return () => { cancelled = true; }; }
    adminApi.getUsers(currentGuildId)
      .then(rows => { if (!cancelled) setData(rows); })
      .catch(error => { if (!cancelled) console.error(error); })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [currentGuildId]);

  const resetFilters = () => { setSearch(""); setDateFilter(null); setAisIdSort(null); };
  const cycleAisIdSort = () => setAisIdSort(p => p === null ? "asc" : p === "asc" ? "desc" : null);

  const uniqueDates = Array.from(new Set(data.map(u => format(new Date(u.verified_at), "MMMM")))).sort((a, b) => {
    const months = ["January","February","March","April","May","June","July","August","September","October","November","December"];
    return months.indexOf(a) - months.indexOf(b);
  });

  const filtered = data.filter(v => {
    const q = search.toLowerCase();
    return (v.discord_id.includes(search) || v.ais_id.toLowerCase().includes(q) || v.email.toLowerCase().includes(q) || v.guild_id.includes(search))
      && (guildFilter ? v.guild_id === guildFilter : true)
      && (dateFilter ? format(new Date(v.verified_at), "MMMM") === dateFilter : true);
  });

  const sorted = aisIdSort
    ? [...filtered].sort((a, b) => aisIdSort === "asc"
        ? a.ais_id.localeCompare(b.ais_id, undefined, { numeric: true })
        : b.ais_id.localeCompare(a.ais_id, undefined, { numeric: true }))
    : filtered;

  const { currentPage, setCurrentPage, showAll, setShowAll, totalPages, paged, pageNumbers } = usePagination(sorted, itemsPerPage);

  useEffect(() => { setCurrentPage(1); }, [search, guildFilter, dateFilter, aisIdSort, setCurrentPage]);

  const hasFilters = !!(search || dateFilter || aisIdSort);

  return (
    <div className="flex flex-col md:pl-64 min-h-screen">
      <div className="flex flex-wrap items-center gap-3 px-6 py-4 border-b border-zinc-800 flex-shrink-0">
        <UserCheck className="w-5 h-5 text-emerald-400 flex-shrink-0" />
        <h1 className="text-xl font-bold text-zinc-100 tracking-tight">Verified Directory</h1>
        {!loading && (
          <span className="text-[11px] text-zinc-600">
            {filtered.length !== data.length ? `${filtered.length} / ${data.length}` : data.length}
          </span>
        )}
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-zinc-500 pointer-events-none" />
            <input type="search" placeholder="Search…" value={search} onChange={e => setSearch(e.target.value)}
              className="w-52 pl-8 pr-3 py-1.5 bg-zinc-800 border border-zinc-700 rounded text-xs text-zinc-200 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors" />
          </div>
          <select value={dateFilter ?? ""} onChange={e => setDateFilter(e.target.value || null)}
            className={cn("px-2.5 py-1.5 bg-zinc-800 border rounded text-xs outline-none cursor-pointer transition-colors",
              dateFilter ? "border-emerald-500/60 text-emerald-300" : "border-zinc-700 text-zinc-400")}>
            <option value="">All months</option>
            {uniqueDates.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
          {hasFilters && (
            <button onClick={resetFilters}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded text-xs text-zinc-500 hover:text-zinc-200 border border-zinc-700 hover:border-zinc-600 transition-colors">
              <X className="w-3 h-3" /> Reset
            </button>
          )}
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-40 text-zinc-600 text-sm animate-pulse">Loading…</div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className="border-b border-zinc-800">
                  <th className="text-left px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap">User ID</th>
                  <th className="text-left px-4 py-2.5 bg-zinc-900/60">
                    <button onClick={cycleAisIdSort}
                      className={cn("flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wider transition-colors",
                        aisIdSort ? "text-emerald-400" : "text-zinc-500 hover:text-zinc-300")}>
                      AIS ID
                      {aisIdSort === "asc" ? <ArrowUp className="w-3 h-3" /> : aisIdSort === "desc" ? <ArrowDown className="w-3 h-3" /> : <ArrowUpDown className="w-3 h-3 opacity-40" />}
                    </button>
                  </th>
                  <th className="text-left px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap">Email</th>
                  <th className="text-left px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap hidden sm:table-cell">Guild ID</th>
                  <th className="text-right px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-zinc-500 bg-zinc-900/60 whitespace-nowrap">Verified On</th>
                </tr>
              </thead>
              <tbody>
                {paged.length === 0 ? (
                  <tr><td colSpan={5} className="h-32 text-center text-zinc-600">No users matched.</td></tr>
                ) : paged.map(item => (
                  <tr key={item.id} className="border-b border-zinc-800/50 hover:bg-zinc-800/30 transition-colors">
                    <td className="px-4 py-2.5 font-mono text-zinc-300">{item.discord_id}</td>
                    <td className="px-4 py-2.5 font-mono text-zinc-400">{item.ais_id}</td>
                    <td className="px-4 py-2.5 text-zinc-500">{item.email}</td>
                    <td className="px-4 py-2.5 text-zinc-600 hidden sm:table-cell">{item.guild_id}</td>
                    <td className="px-4 py-2.5 text-right text-zinc-500 tabular-nums">{format(new Date(item.verified_at), "dd/MM HH:mm")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <PaginationControls
            currentPage={currentPage} setCurrentPage={setCurrentPage} totalPages={totalPages}
            showAll={showAll} setShowAll={setShowAll} pageNumbers={pageNumbers}
            totalCount={sorted.length} itemsPerPage={itemsPerPage} />
        </>
      )}
    </div>
  );
}
