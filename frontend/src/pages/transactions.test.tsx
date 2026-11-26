import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TransactionsPage } from "./transactions";
import { ApiError } from "@/lib/api-client";
import { apiMock, resetApiMock, signIn } from "@/test/api-mock";
import { demoUser, page, renderWithProviders, tx } from "@/test/utils";

vi.mock("@/lib/api", async () => ({ api: (await import("@/test/api-mock")).apiMock }));

const rows = [
  tx(),
  tx({
    id: 2,
    merchant: "Amazon",
    description: "POS AMAZON IN",
    amount: -25999,
    category: "Shopping",
    categorySource: "USER",
    categoryConfidence: null,
    anomaly: { status: "FLAGGED", score: 0.97, reasons: ["Amount is 6x your usual Shopping spend"] },
  }),
  tx({ id: 3, merchant: "ACME", description: "SALARY ACME", amount: 129700, category: "Income", categorySource: "RULE", categoryConfidence: null }),
];

beforeEach(() => {
  resetApiMock();
  signIn(demoUser);
  apiMock.transactions.list.mockResolvedValue(page(rows, { totalElements: 60, totalPages: 3 }));
});

describe("<TransactionsPage>", () => {
  it("renders rows with category badges, confidence, amounts and anomaly flag", async () => {
    renderWithProviders(<TransactionsPage />);
    const table = await screen.findByRole("table", { name: "Transactions" });
    const bodyRows = within(table).getAllByTestId("transaction-row");
    expect(bodyRows).toHaveLength(3);
    const badges = within(table).getAllByTestId("category-badge");
    expect(badges[0]).toHaveTextContent("Food");
    expect(badges[0]).toHaveTextContent("92%");
    expect(within(bodyRows[1]!).getByText("-₹25,999.00")).toBeInTheDocument();
    expect(within(bodyRows[2]!).getByText("₹1,29,700.00")).toBeInTheDocument();
    expect(within(bodyRows[1]!).getByTestId("anomaly-indicator")).toHaveAccessibleName(/Amount is 6x your usual Shopping spend/);
    expect(screen.getByText(/60 transactions · page 1 of 3/)).toBeInTheDocument();
    expect(apiMock.transactions.list).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 25, sort: "postedAt,desc" }));
  });

  it("edits a category optimistically and confirms it teaches the model", async () => {
    const user = userEvent.setup();
    let resolve: (v: unknown) => void = () => {};
    apiMock.transactions.update.mockImplementation(() => new Promise((r) => (resolve = r)));
    renderWithProviders(<TransactionsPage />);

    const select = await screen.findByRole("combobox", { name: "Category for Swiggy (teaches the model)" });
    await user.selectOptions(select, "Groceries");

    expect(apiMock.transactions.update).toHaveBeenCalledWith(1, { category: "Groceries" });
    // optimistic: badge changes before the server responds
    const firstRow = screen.getAllByTestId("transaction-row")[0]!;
    await waitFor(() => expect(within(firstRow).getByTestId("category-badge")).toHaveTextContent("Groceries"));
    expect(within(firstRow).getByTestId("category-badge")).not.toHaveTextContent("92%");

    resolve(tx({ category: "Groceries", categorySource: "USER", categoryConfidence: null }));
    expect(await screen.findByText(/teaches the model/i, { selector: "[data-description]" })).toBeInTheDocument();
  });

  it("rolls back when the update fails", async () => {
    const user = userEvent.setup();
    apiMock.transactions.update.mockRejectedValue(new ApiError({ status: 500, detail: "boom" }));
    renderWithProviders(<TransactionsPage />);
    const select = await screen.findByRole("combobox", { name: /Category for Swiggy/ });
    await user.selectOptions(select, "Rent");
    expect(await screen.findByText("Update failed: boom")).toBeInTheDocument();
    const firstRow = screen.getAllByTestId("transaction-row")[0]!;
    expect(within(firstRow).getByTestId("category-badge")).toHaveTextContent("Food");
  });

  it("requests server-side sorting, filtering and paging", async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionsPage />);
    await screen.findByRole("table", { name: "Transactions" });

    await user.click(screen.getByRole("button", { name: /^amount$/i }));
    await waitFor(() => expect(apiMock.transactions.list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: "amount,desc" })));

    await user.click(screen.getByRole("switch", { name: "Flagged only" }));
    await waitFor(() => expect(apiMock.transactions.list).toHaveBeenLastCalledWith(expect.objectContaining({ flagged: true, page: 0 })));

    await user.selectOptions(screen.getByLabelText("Category", { selector: "select" }), "Food");
    await waitFor(() => expect(apiMock.transactions.list).toHaveBeenLastCalledWith(expect.objectContaining({ category: "Food" })));

    await user.click(screen.getByRole("button", { name: "Next page" }));
    await waitFor(() => expect(apiMock.transactions.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })));
  });

  it("shows an empty state and an error state with retry", async () => {
    const user = userEvent.setup();
    apiMock.transactions.list.mockRejectedValueOnce(new ApiError({ status: 503, detail: "Service unavailable" }));
    renderWithProviders(<TransactionsPage />);
    expect(await screen.findByText("Service unavailable")).toBeInTheDocument();
    apiMock.transactions.list.mockResolvedValueOnce(page([]));
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("No transactions found")).toBeInTheDocument();
  });
});
