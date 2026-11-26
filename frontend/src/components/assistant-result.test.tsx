import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AssistantResultView } from "./assistant-result";
import { AskPage } from "@/pages/ask";
import { ApiError } from "@/lib/api-client";
import type { AssistantResult } from "@/lib/types";
import { apiMock, resetApiMock, signIn } from "@/test/api-mock";
import { demoUser, renderWithProviders } from "@/test/utils";

vi.mock("@/lib/api", async () => ({ api: (await import("@/test/api-mock")).apiMock }));

const barResult: AssistantResult = {
  question: "How much did I spend on food last month vs this month?",
  sql: "SELECT month, SUM(-amount) AS spent FROM transactions WHERE category = 'Food' GROUP BY month",
  columns: ["month", "spent"],
  rows: [
    ["2026-10", 8200.5],
    ["2026-11", 6100],
  ],
  chart: { type: "bar", x: "month", y: "spent" },
  summary: "You spent ₹2,100.50 less on food this month.",
  provider: "rules",
  durationMs: 37,
};

beforeEach(() => resetApiMock());

describe("<AssistantResultView>", () => {
  it("renders SQL, summary, provider, duration, chart and table", async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });

    render(<AssistantResultView result={barResult} />);
    expect(screen.getByTestId("assistant-sql")).toHaveTextContent("SELECT month, SUM(-amount)");
    expect(screen.getByTestId("assistant-summary")).toHaveTextContent("less on food");
    expect(screen.getByText("rules")).toBeInTheDocument();
    expect(screen.getByText("37 ms")).toBeInTheDocument();
    expect(screen.getByTestId("assistant-chart")).toHaveAttribute("data-chart-type", "bar");
    const table = screen.getByRole("table", { name: "Query result" });
    expect(within(table).getByText("₹8,200.50")).toBeInTheDocument();
    expect(within(table).getAllByRole("row")).toHaveLength(3);

    await user.click(screen.getByRole("button", { name: "Copy SQL" }));
    expect(writeText).toHaveBeenCalledWith(barResult.sql);
    expect(await screen.findByRole("button", { name: "Copied" })).toBeInTheDocument();
  });

  it("renders a big number for chart type number", () => {
    render(
      <AssistantResultView
        result={{ ...barResult, columns: ["total_spent"], rows: [[12345]], chart: { type: "number" } }}
      />,
    );
    expect(screen.getByTestId("assistant-number")).toHaveTextContent("₹12,345.00");
    expect(screen.queryByTestId("assistant-chart")).not.toBeInTheDocument();
  });

  it("renders only a table for chart type table", () => {
    render(
      <AssistantResultView
        result={{ ...barResult, columns: ["merchant", "count"], rows: [["Swiggy", 12]], chart: { type: "table" } }}
      />,
    );
    expect(screen.queryByTestId("assistant-chart")).not.toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Query result" })).toHaveTextContent("Swiggy");
  });
});

describe("<AskPage>", () => {
  beforeEach(() => {
    signIn(demoUser);
    apiMock.assistant.examples.mockResolvedValue(["Top 5 merchants last month", "Average grocery spend"]);
    apiMock.assistant.history.mockResolvedValue([]);
  });

  it("asks via an example chip and shows the result", async () => {
    const user = userEvent.setup();
    apiMock.assistant.query.mockResolvedValue({ ...barResult, question: "Top 5 merchants last month" });
    renderWithProviders(<AskPage />);
    await user.click(await screen.findByRole("button", { name: "Top 5 merchants last month" }));
    expect(apiMock.assistant.query).toHaveBeenCalledWith("Top 5 merchants last month");
    expect(await screen.findByTestId("assistant-sql")).toBeInTheDocument();
    expect(screen.getByTestId("ask-input")).toHaveValue("Top 5 merchants last month");
  });

  it("shows the reason when a question is rejected", async () => {
    const user = userEvent.setup();
    apiMock.assistant.query.mockRejectedValue(new ApiError({ status: 422, detail: "Only read-only questions are supported" }));
    renderWithProviders(<AskPage />);
    await user.type(screen.getByTestId("ask-input"), "delete all my data");
    await user.click(screen.getByTestId("ask-submit"));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Only read-only questions are supported"));
  });
});
