import type { ForecastPoint, HistoryPoint } from "./types";

export interface ForecastRow {
  month: string;
  actual: number | null;
  mean: number | null;
  band: [number, number] | null;
}

/** Merge history + forecast into one series; the last actual point seeds the forecast line so it connects. */
export function buildForecastSeries(history: HistoryPoint[], forecast: ForecastPoint[]): ForecastRow[] {
  const rows: ForecastRow[] = history.map((h) => ({ month: h.month, actual: h.amount, mean: null, band: null }));
  const last = rows[rows.length - 1];
  if (last && forecast.length) {
    last.mean = last.actual;
    last.band = [last.actual ?? 0, last.actual ?? 0];
  }
  for (const f of forecast) rows.push({ month: f.month, actual: null, mean: f.mean, band: [f.lower, f.upper] });
  return rows;
}
