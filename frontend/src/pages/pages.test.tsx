import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AnomaliesPage } from "./anomalies";
import { AuditPage } from "./audit";
import { BudgetsPage } from "./budgets";
import { ForecastPage } from "./forecast";
import { GoalsPage } from "./goals";
import { SettingsPage } from "./settings";
import { SubscriptionsPage } from "./subscriptions";
import { RegisterPage } from "./register";
import { apiMock, resetApiMock, signIn } from "@/test/api-mock";
import { adminUser, demoUser, page, renderWithProviders, tx } from "@/test/utils";
import { Route } from "react-router";

vi.mock("@/lib/api", async () => ({ api: (await import("@/test/api-mock")).apiMock }));

beforeEach(() => {
  resetApiMock();
  signIn(demoUser);
});

describe("<BudgetsPage>", () => {
  it("lists budgets with coloured progress, alerts and creates a budget", async () => {
    const user = userEvent.setup();
    apiMock.budgets.list.mockResolvedValue([
      { id: 1, category: "Food", monthlyLimit: 5000, spent: 4500, percent: 0.9, alertLevel: "WARNING", month: "2026-11" },
      { id: 2, category: "Rent", monthlyLimit: 26000, spent: 26000, percent: 1, alertLevel: "EXCEEDED", month: "2026-11" },
    ]);
    apiMock.budgets.alerts.mockResolvedValue([
      { budgetId: 1, category: "Food", alertLevel: "WARNING", percent: 0.9, message: "90% of your Food budget used" },
    ]);
    apiMock.budgets.create.mockResolvedValue({});
    renderWithProviders(<BudgetsPage />);

    const cards = await screen.findAllByTestId("budget-card");
    expect(cards).toHaveLength(2);
    expect(within(cards[0]!).getByRole("progressbar").firstElementChild).toHaveClass("bg-warning");
    expect(within(cards[1]!).getByRole("progressbar").firstElementChild).toHaveClass("bg-destructive");
    expect(await screen.findByText("90% of your Food budget used")).toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: "New budget" })[0]!);
    const dialog = await screen.findByRole("dialog");
    await user.selectOptions(within(dialog).getByLabelText("Category"), "Groceries");
    await user.type(within(dialog).getByLabelText("Monthly limit (₹)"), "8000");
    await user.click(within(dialog).getByRole("button", { name: "Create budget" }));
    await waitFor(() => expect(apiMock.budgets.create).toHaveBeenCalledWith("Groceries", 8000));
  });

  it("changes month with the month picker", async () => {
    const user = userEvent.setup();
    apiMock.budgets.list.mockResolvedValue([]);
    apiMock.budgets.alerts.mockResolvedValue([]);
    renderWithProviders(<BudgetsPage />);
    expect(await screen.findByText("No budgets yet")).toBeInTheDocument();
    const first = apiMock.budgets.list.mock.calls[0]![0] as string;
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    await waitFor(() => expect(apiMock.budgets.list).toHaveBeenCalledTimes(2));
    expect(apiMock.budgets.list.mock.calls[1]![0]).not.toBe(first);
  });
});

describe("<GoalsPage>", () => {
  it("shows goals and an AI suggestion", async () => {
    const user = userEvent.setup();
    apiMock.goals.list.mockResolvedValue([
      { id: 1, name: "Emergency fund", targetAmount: 300000, savedAmount: 120000, targetDate: "2027-06-30", progress: 0.4, monthsRemaining: 7, requiredMonthly: 25714 },
    ]);
    apiMock.goals.suggestion.mockResolvedValue({
      goalId: 1,
      requiredMonthly: 25714,
      averageMonthlySurplus: 20000,
      feasible: false,
      cuts: [{ category: "Shopping", currentMonthly: 9000, suggestedMonthly: 3000, saving: 6000 }],
      advice: "Trim shopping to close the gap.",
      provider: "heuristic",
    });
    renderWithProviders(<GoalsPage />);
    const card = await screen.findByTestId("goal-card");
    expect(card).toHaveTextContent("Emergency fund");
    expect(within(card).getByRole("progressbar")).toHaveAttribute("aria-valuenow", "40");
    await user.click(within(card).getByRole("button", { name: /Get AI suggestion/ }));
    const s = await screen.findByTestId("goal-suggestion");
    expect(s).toHaveTextContent("Needs cuts");
    expect(s).toHaveTextContent("Trim shopping to close the gap.");
    expect(within(s).getByRole("table", { name: "Suggested cuts" })).toHaveTextContent("₹6,000.00");
  });

  it("validates the goal form", async () => {
    const user = userEvent.setup();
    apiMock.goals.list.mockResolvedValue([]);
    renderWithProviders(<GoalsPage />);
    await user.click((await screen.findAllByRole("button", { name: "New goal" }))[0]!);
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Create goal" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Name is required");
    expect(apiMock.goals.create).not.toHaveBeenCalled();
  });
});

describe("<AnomaliesPage>", () => {
  it("scans and dismisses", async () => {
    const user = userEvent.setup();
    apiMock.anomalies.list.mockResolvedValue([
      tx({ id: 9, merchant: "Croma", amount: -89999, anomaly: { status: "FLAGGED", score: 0.91, reasons: ["Unusually large", "New merchant"] } }),
    ]);
    apiMock.anomalies.scan.mockResolvedValue({ scanned: 240, flagged: 1 });
    apiMock.anomalies.dismiss.mockResolvedValue(tx({ id: 9 }));
    renderWithProviders(<AnomaliesPage />);
    const card = await screen.findByTestId("anomaly-card");
    expect(within(card).getByRole("list", { name: "Reasons" })).toHaveTextContent("New merchant");
    expect(card).toHaveTextContent("Score 0.91");
    await user.click(screen.getByTestId("scan-now"));
    expect(await screen.findByTestId("scan-result")).toHaveTextContent("Scanned 240 transactions and flagged 1");
    await user.click(within(card).getByRole("button", { name: /Dismiss/ }));
    await waitFor(() => expect(screen.queryByTestId("anomaly-card")).not.toBeInTheDocument());
    expect(apiMock.anomalies.dismiss).toHaveBeenCalledWith(9);
  });
});

describe("<SubscriptionsPage>", () => {
  it("totals monthly subscription cost", async () => {
    apiMock.recurring.mockResolvedValue([
      { merchant: "Netflix", category: "Subscriptions", cadence: "MONTHLY", averageAmount: 649, lastCharged: "2026-11-05", nextExpected: "2026-12-05", occurrences: 12, monthlyCost: 649, subscription: true },
      { merchant: "Spotify", category: "Subscriptions", cadence: "MONTHLY", averageAmount: 119, lastCharged: "2026-11-02", nextExpected: "2026-12-02", occurrences: 12, monthlyCost: 119, subscription: true },
      { merchant: "Landlord", category: "Rent", cadence: "MONTHLY", averageAmount: 26000, lastCharged: "2026-11-03", nextExpected: "2026-12-03", occurrences: 12, monthlyCost: 26000, subscription: false },
    ]);
    renderWithProviders(<SubscriptionsPage />);
    expect(await screen.findByRole("table", { name: "Recurring payments" })).toHaveTextContent("Netflix");
    expect(screen.getByTestId("subscriptions-total")).toHaveTextContent("₹768.00");
  });
});

describe("<ForecastPage>", () => {
  it("renders category selector, method and forecast table", async () => {
    const user = userEvent.setup();
    const series = {
      history: [{ month: "2026-11", amount: 8000 }],
      forecast: [{ month: "2026-12", mean: 8200, lower: 7000, upper: 9400 }],
    };
    apiMock.forecast.mockResolvedValue({
      horizon: 3,
      confidence: 0.8,
      generatedAt: "2026-11-26T10:00:00Z",
      categories: [
        { category: "Food", method: "holt-winters", ...series },
        { category: "Rent", method: "naive", ...series },
      ],
      total: series,
    });
    renderWithProviders(<ForecastPage />);
    expect(await screen.findByTestId("forecast-method")).toHaveTextContent("holt-winters");
    expect(screen.getByRole("table", { name: "Food forecast" })).toHaveTextContent("₹7,000.00 – ₹9,400.00");
    await user.selectOptions(screen.getByLabelText("Category"), "Rent");
    expect(screen.getByTestId("forecast-method")).toHaveTextContent("naive");
  });
});

describe("<SettingsPage>", () => {
  it("sets up 2FA with a QR code and enables it", async () => {
    const user = userEvent.setup();
    apiMock.auth.mfaSetup.mockResolvedValue({ secret: "JBSWY3DPEHPK3PXP", otpauthUri: "otpauth://totp/FinPilot:demo?secret=JBSWY3DPEHPK3PXP&issuer=FinPilot" });
    apiMock.auth.mfaEnable.mockResolvedValue({ ...demoUser, mfaEnabled: true });
    renderWithProviders(<SettingsPage />);
    await user.click(await screen.findByRole("button", { name: /Set up 2FA/ }));
    expect(await screen.findByTestId("mfa-secret")).toHaveTextContent("JBSWY3DPEHPK3PXP");
    expect(screen.getByTestId("mfa-qr").querySelector("svg")).not.toBeNull();
    await user.type(screen.getByLabelText("Code from the app"), "123456");
    await user.click(screen.getByRole("button", { name: "Enable 2FA" }));
    await waitFor(() => expect(apiMock.auth.mfaEnable).toHaveBeenCalledWith("123456"));
    expect(await screen.findByRole("button", { name: "Disable 2FA" })).toBeInTheDocument();
  });

  it("switches theme from the appearance radio group", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SettingsPage />);
    await user.click(await screen.findByRole("radio", { name: /Dark/ }));
    expect(document.documentElement).toHaveClass("dark");
    expect(screen.getByRole("radio", { name: /Dark/ })).toHaveAttribute("aria-checked", "true");
  });
});

describe("<AuditPage>", () => {
  const entries = page([{ id: 1, action: "LOGIN_SUCCESS", entityType: "User", entityId: 1, ip: "10.0.0.1", details: "chrome", createdAt: "2026-11-26T09:00:00Z", userEmail: "demo@finpilot.dev" }]);

  it("hides the ML card for USER", async () => {
    apiMock.audit.mockResolvedValue(entries);
    renderWithProviders(<AuditPage />);
    expect(await screen.findByRole("table", { name: "Audit log" })).toHaveTextContent("LOGIN_SUCCESS");
    expect(screen.queryByTestId("ml-model-card")).not.toBeInTheDocument();
  });

  it("shows ML status and retrains for ADMIN", async () => {
    resetApiMock();
    signIn(adminUser);
    const user = userEvent.setup();
    apiMock.audit.mockResolvedValue(entries);
    apiMock.admin.mlStatus.mockResolvedValue({ modelVersion: "v3", trainedAt: "2026-11-20T00:00:00Z", holdoutAccuracy: 0.912, nSamples: 5000, nCorrections: 40, pendingCorrections: 5 });
    apiMock.admin.retrain.mockResolvedValue({ modelVersion: "v4", corrections: 45, holdoutAccuracy: 0.93 });
    renderWithProviders(<AuditPage />);
    const card = await screen.findByTestId("ml-model-card");
    expect(await within(card).findByText("91.2%")).toBeInTheDocument();
    await user.click(within(card).getByRole("button", { name: "Retrain" }));
    expect(await screen.findByText("Model v4 trained")).toBeInTheDocument();
  });
});

describe("<RegisterPage>", () => {
  it("enforces the password policy and registers", async () => {
    const user = userEvent.setup();
    resetApiMock();
    apiMock.auth.register.mockResolvedValue({ mfaRequired: false, accessToken: "A", refreshToken: "R", user: demoUser });
    renderWithProviders(<RegisterPage />, { route: "/register", path: "/register", extraRoutes: <Route path="/" element={<h1>Home</h1>} /> });
    await user.type(screen.getByLabelText("Name"), "Asha");
    await user.type(screen.getByLabelText("Email"), "asha@example.com");
    await user.type(screen.getByLabelText("Password"), "short1");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Password must be at least 10 characters")).toBeInTheDocument();
    await user.clear(screen.getByLabelText("Password"));
    await user.type(screen.getByLabelText("Password"), "longpassword1");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByRole("heading", { name: "Home" })).toBeInTheDocument();
    expect(apiMock.auth.register).toHaveBeenCalledWith("asha@example.com", "longpassword1", "Asha");
  });
});
