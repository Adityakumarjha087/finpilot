import { formatMoney } from "@/lib/format";
import { cn } from "@/lib/utils";

/** Signed amount: negative = expense (red), positive = income (green). */
export function Money({ value, className, colored = true }: { value: number | null | undefined; className?: string; colored?: boolean }) {
  const tone = !colored || value == null ? "" : value < 0 ? "text-destructive" : value > 0 ? "text-success" : "";
  return <span className={cn("tabular-nums", tone, className)}>{formatMoney(value)}</span>;
}
