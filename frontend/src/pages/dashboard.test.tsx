import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DashboardPage } from "./dashboard";
import { ApiError } from "@/lib/api-client";
import type { Dashboard } from "@/lib/types";
import { apiMock, resetApiMock, signIn } from "@/test/api-mock";
import { demoUser, renderWithProviders } from "@/test/utils";

vi.mock("@/lib/api", async () => ({ api: (await import("@/test/api-mock")).apiMock }));

const data: Dashboard = {
  currency: "INR",
  totals: { income: 129700, expense: 64850, net: 64850, savingsRate: 0.5 },
  monthly: [
    { month: "2026-10", income: 129700, expense: 70000, net: 59700 },
    { month: "2026-11", income: 129700, expense: 64850, net: 64850 },
  ],
  categoryBreakdown: [
    { category: "Rent", amount: 26000 },
    { category: "Food", amount: 8000 },
  ],
  netWorth: [
    { month: "2026-10", value: 300000 },
    { month: "2026-11", value: 364850 },
  ],
  flaggedAnomalies: 3,
  budgetAlerts: 2,
  transactionCount: 240,
};

beforeEach(() => {
  resetApiMock();
  signIn(demoUser);
});

describe("<DashboardPage>", () => {
  it("shows KPI cards and the budget alert banner", async () => {
    apiMock.dashboard.mockResolvedValue(data);
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByTestId("kpi-income")).toHaveTextContent("₹1,29,700.00");
    expect(screen.getByTestId("kpi-expense")).toHaveTextContent("₹64,850.00");
    expect(screen.getByTestId("kpi-savings-rate")).toHaveTextContent("50%");
    expect(screen.getByTestId("kpi-anomalies")).toHaveTextContent("3");
    expect(screen.getByTestId("budget-alert-banner")).toHaveTextContent("2 budgets are at or above 80 %");
    expect(screen.getByText("Income vs expenses")).toBeInTheDocument();
    expect(screen.getByText("Net worth")).toBeInTheDocument();
  });

  it("shows an empty state without transactions", async () => {
    apiMock.dashboard.mockResolvedValue({ ...data, transactionCount: 0, budgetAlerts: 0 });
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByText("No transactions yet")).toBeInTheDocument();
    expect(screen.queryByTestId("budget-alert-banner")).not.toBeInTheDocument();
  });

  it("retries after an error", async () => {
    const user = userEvent.setup();
    apiMock.dashboard.mockRejectedValueOnce(new ApiError({ status: 500, detail: "Database down" })).mockResolvedValueOnce(data);
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByText("Database down")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByTestId("kpi-net")).toBeInTheDocument();
  });
});
