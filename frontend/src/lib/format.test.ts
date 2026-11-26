import { describe, expect, it } from "vitest";
import { currentMonth, formatCell, formatDate, formatMoney, formatMonth, formatPercent, shiftMonth } from "./format";

describe("money formatting", () => {
  it("formats INR with Indian digit grouping", () => {
    expect(formatMoney(1234567.5)).toBe("₹12,34,567.50");
    expect(formatMoney(0)).toBe("₹0.00");
  });
  it("keeps the sign for expenses", () => {
    expect(formatMoney(-450.5)).toBe("-₹450.50");
  });
  it("renders a dash for missing values", () => {
    expect(formatMoney(null)).toBe("—");
    expect(formatMoney(undefined)).toBe("—");
    expect(formatMoney(Number.NaN)).toBe("—");
  });
  it("formats money-like assistant cells as currency and others as numbers", () => {
    expect(formatCell(1500, "total_spent")).toBe("₹1,500.00");
    expect(formatCell(1500, "txn_count")).toBe("1,500");
    expect(formatCell("Food", "category")).toBe("Food");
    expect(formatCell(null, "x")).toBe("—");
  });
});

describe("other formatters", () => {
  it("formats ratios as percent", () => {
    expect(formatPercent(0.8)).toBe("80%");
    expect(formatPercent(0.123)).toBe("12.3%");
  });
  it("formats months and dates", () => {
    expect(formatMonth("2026-11")).toBe("Nov 26");
    expect(formatDate("2026-11-03")).toBe("3 Nov 2026");
    expect(formatDate("2026-11-03T14:22:00Z")).toBe("3 Nov 2026");
  });
  it("computes and shifts months across year boundaries", () => {
    expect(currentMonth(new Date(2026, 0, 15))).toBe("2026-01");
    expect(shiftMonth("2026-01", -1)).toBe("2025-12");
    expect(shiftMonth("2026-12", 1)).toBe("2027-01");
  });
});
