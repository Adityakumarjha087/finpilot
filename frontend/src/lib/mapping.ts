import type { ColumnMapping } from "./types";

export type AmountMode = "single" | "split";

export interface MappingDraft {
  date: string;
  time: string;
  description: string;
  mode: AmountMode;
  amount: string;
  debit: string;
  credit: string;
  invertSign: boolean;
  autoDate: boolean;
  dateFormat: string;
}

export function draftFromSuggestion(s: ColumnMapping | null | undefined, headers: string[]): MappingDraft {
  const has = (h: string | null | undefined) => (h && headers.includes(h) ? h : "");
  const mode: AmountMode = s?.amount && !s.debit && !s.credit ? "single" : s?.debit || s?.credit ? "split" : "single";
  return {
    date: has(s?.date),
    time: has(s?.time),
    description: has(s?.description),
    mode,
    amount: has(s?.amount),
    debit: has(s?.debit),
    credit: has(s?.credit),
    invertSign: !!s?.invertSign,
    autoDate: !s?.dateFormat,
    dateFormat: s?.dateFormat ?? "dd/MM/yyyy",
  };
}

/** Returns a list of human readable problems; empty = valid. */
export function validateDraft(d: MappingDraft): string[] {
  const problems: string[] = [];
  if (!d.date) problems.push("Select the date column.");
  if (!d.description) problems.push("Select the description column.");
  if (d.mode === "single" && !d.amount) problems.push("Select the amount column.");
  if (d.mode === "split" && !d.debit && !d.credit) problems.push("Select a debit and/or credit column.");
  if (d.mode === "split" && d.debit && d.debit === d.credit) problems.push("Debit and credit must be different columns.");
  if (!d.autoDate && !d.dateFormat.trim()) problems.push("Enter a date format or choose auto-detect.");
  return problems;
}

export function draftToMapping(d: MappingDraft): ColumnMapping {
  return {
    date: d.date,
    time: d.time || null,
    description: d.description,
    amount: d.mode === "single" ? d.amount || null : null,
    debit: d.mode === "split" ? d.debit || null : null,
    credit: d.mode === "split" ? d.credit || null : null,
    dateFormat: d.autoDate ? null : d.dateFormat.trim(),
    invertSign: d.mode === "single" ? d.invertSign : false,
  };
}

export const MAX_IMPORT_BYTES = 5 * 1024 * 1024;

export function validateImportFile(file: File): string | null {
  const name = file.name.toLowerCase();
  if (!name.endsWith(".csv") && !name.endsWith(".xlsx")) return "Only .csv and .xlsx files are supported.";
  if (file.size > MAX_IMPORT_BYTES) return "File is larger than 5 MB.";
  return null;
}
