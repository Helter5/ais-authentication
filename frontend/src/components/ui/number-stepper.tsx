import { Minus, Plus } from "lucide-react";
import { cn } from "@/lib/utils";

type NumberStepperProps = {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
  className?: string;
  inputClassName?: string;
  ariaLabel?: string;
};

export function NumberStepper({
  value,
  onChange,
  min = Number.MIN_SAFE_INTEGER,
  max = Number.MAX_SAFE_INTEGER,
  step = 1,
  disabled = false,
  className,
  inputClassName,
  ariaLabel = "Number",
}: NumberStepperProps) {
  const clamp = (next: number) => Math.min(max, Math.max(min, next));
  const change = (next: number) => {
    if (Number.isFinite(next)) onChange(clamp(next));
  };

  return (
    <div className={cn(
      "flex h-10 overflow-hidden rounded-lg border border-zinc-700 bg-zinc-800 transition-colors focus-within:border-indigo-500 focus-within:ring-1 focus-within:ring-indigo-500/20",
      disabled && "cursor-not-allowed opacity-50",
      className,
    )}>
      <button
        type="button"
        onClick={() => change(value - step)}
        disabled={disabled || value <= min}
        aria-label={`Decrease ${ariaLabel}`}
        className="flex w-10 items-center justify-center border-r border-zinc-700 text-zinc-400 transition-colors hover:bg-zinc-700 hover:text-white disabled:cursor-not-allowed disabled:text-zinc-700 disabled:hover:bg-transparent"
      >
        <Minus className="h-3.5 w-3.5" />
      </button>
      <input
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        value={value}
        onChange={event => {
          const next = event.target.value.replace(/\D/g, "");
          if (next !== "") change(Number(next));
        }}
        onBlur={() => change(value)}
        onKeyDown={event => {
          if (event.key === "ArrowUp") {
            event.preventDefault();
            change(value + step);
          } else if (event.key === "ArrowDown") {
            event.preventDefault();
            change(value - step);
          }
        }}
        disabled={disabled}
        aria-label={ariaLabel}
        className={cn(
          "min-w-0 flex-1 bg-transparent px-3 text-center text-sm font-semibold tabular-nums text-zinc-100 outline-none",
          inputClassName,
        )}
      />
      <button
        type="button"
        onClick={() => change(value + step)}
        disabled={disabled || value >= max}
        aria-label={`Increase ${ariaLabel}`}
        className="flex w-10 items-center justify-center border-l border-zinc-700 text-zinc-400 transition-colors hover:bg-zinc-700 hover:text-white disabled:cursor-not-allowed disabled:text-zinc-700 disabled:hover:bg-transparent"
      >
        <Plus className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
