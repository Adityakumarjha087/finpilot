export const CHART_COLORS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
  "var(--chart-6)",
  "var(--chart-7)",
  "var(--chart-8)",
];

export const chartColor = (i: number) => CHART_COLORS[i % CHART_COLORS.length]!;

export const tooltipStyle = {
  backgroundColor: "var(--popover)",
  border: "1px solid var(--border)",
  borderRadius: 8,
  color: "var(--popover-foreground)",
  fontSize: 12,
};

export const axisProps = {
  stroke: "var(--muted-foreground)",
  fontSize: 12,
  tickLine: false,
  axisLine: false,
} as const;

export const gridProps = { stroke: "var(--grid)", strokeDasharray: "0", vertical: false } as const;

/** Keep the top `max - 1` slices and fold the rest into "Other" (never cycle hues). */
export function foldSlices<T extends { amount: number }>(
  rows: T[],
  label: (r: T) => string,
  max = 8,
): { name: string; value: number }[] {
  const sorted = [...rows].sort((a, b) => b.amount - a.amount);
  if (sorted.length <= max) return sorted.map((r) => ({ name: label(r), value: r.amount }));
  const head = sorted.slice(0, max - 1).map((r) => ({ name: label(r), value: r.amount }));
  const rest = sorted.slice(max - 1).reduce((s, r) => s + r.amount, 0);
  return [...head, { name: "Other", value: rest }];
}
