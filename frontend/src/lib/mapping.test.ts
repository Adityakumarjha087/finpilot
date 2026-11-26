import { describe, expect, it } from "vitest";
import { draftFromSuggestion, draftToMapping, validateDraft, validateImportFile } from "./mapping";
import { buildForecastSeries } from "./forecast";
import { foldSlices } from "./chart";

const headers = ["Txn Date", "Time", "Narration", "Withdrawal Amt", "Deposit Amt", "Closing Balance"];

describe("column mapping", () => {
  it("builds a split debit/credit draft from the suggestion and round-trips it", () => {
    const d = draftFromSuggestion(
      { date: "Txn Date", time: "Time", description: "Narration", debit: "Withdrawal Amt", credit: "Deposit Amt", dateFormat: "dd/MM/yyyy" },
      headers,
    );
    expect(d.mode).toBe("split");
    expect(d.autoDate).toBe(false);
    expect(validateDraft(d)).toEqual([]);
    expect(draftToMapping(d)).toEqual({
      date: "Txn Date",
      time: "Time",
      description: "Narration",
      amount: null,
      debit: "Withdrawal Amt",
      credit: "Deposit Amt",
      dateFormat: "dd/MM/yyyy",
      invertSign: false,
    });
  });

  it("ignores suggested headers that are not in the file and reports problems", () => {
    const d = draftFromSuggestion({ date: "Nope", description: "Narration", amount: "Amount" }, headers);
    expect(d.date).toBe("");
    expect(d.mode).toBe("single");
    expect(validateDraft(d)).toEqual(["Select the date column.", "Select the amount column."]);
  });

  it("sends null dateFormat for auto-detect and invertSign for single amount", () => {
    const d = { ...draftFromSuggestion(null, headers), date: "Txn Date", description: "Narration", amount: "Deposit Amt", invertSign: true };
    expect(d.autoDate).toBe(true);
    expect(draftToMapping(d)).toMatchObject({ amount: "Deposit Amt", debit: null, credit: null, dateFormat: null, invertSign: true });
  });

  it("validates file type and size", () => {
    expect(validateImportFile(new File(["a"], "s.csv"))).toBeNull();
    expect(validateImportFile(new File(["a"], "s.XLSX"))).toBeNull();
    expect(validateImportFile(new File(["a"], "s.pdf"))).toMatch(/only \.csv and \.xlsx/i);
  });
});

describe("chart helpers", () => {
  it("joins history and forecast so the forecast line connects", () => {
    const rows = buildForecastSeries(
      [
        { month: "2026-10", amount: 100 },
        { month: "2026-11", amount: 120 },
      ],
      [{ month: "2026-12", mean: 130, lower: 110, upper: 150 }],
    );
    expect(rows).toEqual([
      { month: "2026-10", actual: 100, mean: null, band: null },
      { month: "2026-11", actual: 120, mean: 120, band: [120, 120] },
      { month: "2026-12", actual: null, mean: 130, band: [110, 150] },
    ]);
  });

  it("folds small pie slices into Other", () => {
    const rows = Array.from({ length: 10 }, (_, i) => ({ c: `C${i}`, amount: 10 - i }));
    const out = foldSlices(rows, (r) => r.c, 8);
    expect(out).toHaveLength(8);
    expect(out[7]).toEqual({ name: "Other", value: 3 + 2 + 1 });
  });
});
