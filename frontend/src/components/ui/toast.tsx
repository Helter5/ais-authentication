import { createContext, useCallback, useContext, useRef, useState } from "react";
import { CheckCircle2, AlertCircle, X } from "lucide-react";
import { cn } from "@/lib/utils";

type ToastType = "success" | "error";

interface ToastItem {
  id: number;
  message: string;
  type: ToastType;
  exiting: boolean;
}

interface ToastContextValue {
  toast: (message: string, type?: ToastType) => void;
}

const ToastContext = createContext<ToastContextValue>({ toast: () => {} });

const DURATION = 4000;
const EXIT_MS = 400;
let uid = 0;

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const timers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  const dismiss = useCallback((id: number) => {
    clearTimeout(timers.current.get(id));
    timers.current.delete(id);
    setToasts(prev => prev.map(t => t.id === id ? { ...t, exiting: true } : t));
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), EXIT_MS);
  }, []);

  const toast = useCallback((message: string, type: ToastType = "success") => {
    const id = uid++;
    setToasts(prev => [...prev, { id, message, type, exiting: false }]);
    timers.current.set(id, setTimeout(() => dismiss(id), DURATION));
  }, [dismiss]);

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[200] flex flex-col items-center gap-2 pointer-events-none" aria-live="polite">
        {toasts.map(t => (
          <div
            key={t.id}
            className={cn(
              "pointer-events-auto relative overflow-hidden flex items-start gap-3 pl-4 pr-3 py-3.5 rounded-xl shadow-2xl min-w-[300px] max-w-sm",
              "bg-zinc-900 border border-zinc-700/60",
              t.type === "success" ? "border-l-[3px] border-l-emerald-500" : "border-l-[3px] border-l-red-500",
            )}
            style={{
              animation: t.exiting
                ? `toast-out ${EXIT_MS}ms ease-in forwards`
                : `toast-in 300ms cubic-bezier(0.21,1.02,0.73,1) forwards`,
            }}
          >
            {t.type === "success"
              ? <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0 mt-0.5" />
              : <AlertCircle className="w-4 h-4 text-red-400 flex-shrink-0 mt-0.5" />}
            <p className="text-sm text-zinc-100 flex-1 leading-snug">{t.message}</p>
            <button
              onClick={() => dismiss(t.id)}
              className="text-zinc-600 hover:text-zinc-300 transition-colors flex-shrink-0 ml-1 mt-0.5"
            >
              <X className="w-3.5 h-3.5" />
            </button>
            {!t.exiting && (
              <div
                className={cn("absolute bottom-0 left-0 h-[2px]", t.type === "success" ? "bg-emerald-500/60" : "bg-red-500/60")}
                style={{ animation: `toast-progress ${DURATION}ms linear forwards` }}
              />
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
