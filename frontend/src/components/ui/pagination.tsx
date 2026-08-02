import { useState, type Dispatch, type SetStateAction } from "react";
import { ChevronsLeft, ChevronLeft, ChevronRight, ChevronsRight } from "lucide-react";
import { cn } from "@/lib/utils";

export function usePagination<T>(items: T[], itemsPerPage: number) {
  const [currentPage, setCurrentPage] = useState(1);
  const [showAll, setShowAll] = useState(false);

  const totalPages = Math.ceil(items.length / itemsPerPage);
  const paged = showAll ? items : items.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);

  const maxVisible = 5;
  let startPage = Math.max(1, currentPage - Math.floor(maxVisible / 2));
  const endPage = Math.min(totalPages, startPage + maxVisible - 1);
  if (endPage - startPage + 1 < maxVisible) startPage = Math.max(1, endPage - maxVisible + 1);
  const pageNumbers = Array.from({ length: Math.max(0, endPage - startPage + 1) }, (_, i) => startPage + i);

  return { currentPage, setCurrentPage, showAll, setShowAll, totalPages, paged, pageNumbers };
}

export function PaginationControls({
  currentPage, setCurrentPage, totalPages, showAll, setShowAll, pageNumbers, totalCount, itemsPerPage,
}: {
  currentPage: number;
  setCurrentPage: Dispatch<SetStateAction<number>>;
  totalPages: number;
  showAll: boolean;
  setShowAll: Dispatch<SetStateAction<boolean>>;
  pageNumbers: number[];
  totalCount: number;
  itemsPerPage: number;
}) {
  return (
    <div className="flex flex-col items-center gap-3 py-4 border-t border-zinc-800">
      {!showAll && totalPages > 1 && (
        <div className="flex items-center gap-1">
          <button onClick={() => setCurrentPage(1)} disabled={currentPage === 1}
            className="p-1.5 rounded text-zinc-500 hover:text-zinc-200 disabled:opacity-30 transition-colors">
            <ChevronsLeft className="w-3.5 h-3.5" />
          </button>
          <button onClick={() => setCurrentPage(p => Math.max(1, p - 1))} disabled={currentPage === 1}
            className="p-1.5 rounded text-zinc-500 hover:text-zinc-200 disabled:opacity-30 transition-colors">
            <ChevronLeft className="w-3.5 h-3.5" />
          </button>
          {pageNumbers.map(p => (
            <button key={p} onClick={() => setCurrentPage(p)}
              className={cn("w-7 h-7 rounded text-xs font-semibold transition-colors",
                p === currentPage ? "bg-zinc-700 text-zinc-100" : "text-zinc-500 hover:text-zinc-200")}>
              {p}
            </button>
          ))}
          <button onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))} disabled={currentPage === totalPages}
            className="p-1.5 rounded text-zinc-500 hover:text-zinc-200 disabled:opacity-30 transition-colors">
            <ChevronRight className="w-3.5 h-3.5" />
          </button>
          <button onClick={() => setCurrentPage(totalPages)} disabled={currentPage === totalPages}
            className="p-1.5 rounded text-zinc-500 hover:text-zinc-200 disabled:opacity-30 transition-colors">
            <ChevronsRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}
      {totalCount > itemsPerPage && (
        <button onClick={() => { setShowAll(v => !v); setCurrentPage(1); }}
          className="text-xs text-zinc-600 hover:text-zinc-400 transition-colors">
          {showAll ? "Show pages" : `Show all ${totalCount}`}
        </button>
      )}
    </div>
  );
}
