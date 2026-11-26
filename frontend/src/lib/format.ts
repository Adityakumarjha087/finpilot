const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
const inrCompact = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  notation: "compact",
  maximumFractionDigits: 1,
});
const pct = new Intl.NumberFormat("en-IN", { style: "percent", maximumFractionDigits: 1 });
const num = new Intl.NumberFormat("en-IN");

export function formatMoney(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return inr.format(value);
}

export function formatMoneyCompact(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return inrCompact.format(value);
}

/** `ratio` is a 0..1 fraction (0.8 = 80 %). */
export function formatPercent(ratio: number | null | undefined): string {
  if (ratio == null || Number.isNaN(ratio)) return "—";
  return pct.format(ratio);
}

export function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return num.format(value);
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** "2026-11" -> "Nov 26" */
export function formatMonth(month: string): string {
  const m = /^(\d{4})-(\d{2})/.exec(month);
  if (!m) return month;
  const idx = Number(m[2]) - 1;
  return `${MONTHS[idx] ?? m[2]} ${m[1]!.slice(2)}`;
}

/** "2026-11-03" or ISO timestamp -> "3 Nov 2026" */
export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!m) return value;
  return `${Number(m[3])} ${MONTHS[Number(m[2]) - 1] ?? m[2]} ${m[1]}`;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
}

export function currentMonth(now: Date = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split("-").map(Number) as [number, number];
  const d = new Date(y, m - 1 + delta, 1);
  return currentMonth(d);
}

export type ResultCell = string | number | null;

function isMoneyColumn(name: string) {
  return /amount|spent|spend|total|income|expense|sum|net|cost|balance/i.test(name);
}

/** Format an assistant result cell; numeric columns whose name looks monetary are shown as INR. */
export function formatCell(value: ResultCell, column: string): string {
  if (value == null) return "—";
  if (typeof value === "number") return isMoneyColumn(column) ? formatMoney(value) : formatNumber(value);
  return value;
}
