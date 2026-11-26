import { cn } from "@/lib/utils";

interface ProgressProps {
  /** 0..1 ratio; values above 1 are clamped visually. */
  value: number;
  label: string;
  className?: string;
  indicatorClassName?: string;
}

export function Progress({ value, label, className, indicatorClassName }: ProgressProps) {
  const pct = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0)) * 100;
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round((Number.isFinite(value) ? value : 0) * 100)}
      className={cn("h-2.5 w-full overflow-hidden rounded-full bg-muted", className)}
    >
      <div className={cn("h-full rounded-full bg-primary transition-all", indicatorClassName)} style={{ width: `${pct}%` }} />
    </div>
  );
}
