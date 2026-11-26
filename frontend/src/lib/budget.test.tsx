import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { alertLevelFor, badgeVariantFor, progressColorClass } from "./budget";
import { BudgetProgress } from "@/components/budget-progress";

describe("budget alert levels", () => {
  it.each([
    [0, "OK"],
    [0.79, "OK"],
    [0.8, "WARNING"],
    [0.99, "WARNING"],
    [1, "EXCEEDED"],
    [1.7, "EXCEEDED"],
  ])("percent %s -> %s", (pct, level) => {
    expect(alertLevelFor(pct)).toBe(level);
  });

  it("maps levels to colours", () => {
    expect(progressColorClass("OK")).toBe("bg-success");
    expect(progressColorClass("WARNING")).toBe("bg-warning");
    expect(progressColorClass("EXCEEDED")).toBe("bg-destructive");
    expect(badgeVariantFor("EXCEEDED")).toBe("destructive");
  });
});

describe("<BudgetProgress>", () => {
  it("colours the bar by level and clamps width at 100%", () => {
    render(<BudgetProgress category="Food" spent={6000} limit={5000} percent={1.2} />);
    const bar = screen.getByRole("progressbar", { name: "Food budget used" });
    expect(bar).toHaveAttribute("aria-valuenow", "120");
    const indicator = bar.firstElementChild as HTMLElement;
    expect(indicator).toHaveClass("bg-destructive");
    expect(indicator.style.width).toBe("100%");
    expect(screen.getByTestId("budget-level")).toHaveTextContent("Exceeded");
  });

  it("shows a warning colour at 80%", () => {
    render(<BudgetProgress category="Rent" spent={800} limit={1000} percent={0.8} />);
    expect(screen.getByRole("progressbar").firstElementChild).toHaveClass("bg-warning");
    expect(screen.getByTestId("budget-level")).toHaveTextContent("Warning · 80%");
  });

  it("is green when on track", () => {
    render(<BudgetProgress category="Bills" spent={100} limit={1000} percent={0.1} />);
    expect(screen.getByRole("progressbar").firstElementChild).toHaveClass("bg-success");
  });
});
