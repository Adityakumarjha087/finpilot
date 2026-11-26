import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ImportPage } from "./import";
import { apiMock, resetApiMock, signIn } from "@/test/api-mock";
import { demoUser, renderWithProviders } from "@/test/utils";

vi.mock("@/lib/api", async () => ({ api: (await import("@/test/api-mock")).apiMock }));

const headers = ["Txn Date", "Time", "Narration", "Withdrawal Amt", "Deposit Amt", "Closing Balance"];

beforeEach(() => {
  resetApiMock();
  signIn(demoUser);
  apiMock.imports.history.mockResolvedValue([
    { id: 7, fileName: "old.csv", importedAt: "2026-10-01T10:00:00Z", totalRows: 10, imported: 9, duplicates: 1, failed: 0 },
  ]);
  apiMock.imports.preview.mockResolvedValue({
    fileName: "sample_statement.csv",
    headers,
    sampleRows: [["01/09/2026", "08:46", "SALARY ACME", "", "129700.00", "379700.00"]],
    totalRows: 156,
    suggestedMapping: {
      date: "Txn Date",
      time: "Time",
      description: "Narration",
      debit: "Withdrawal Amt",
      credit: "Deposit Amt",
      dateFormat: "dd/MM/yyyy",
    },
  });
  apiMock.imports.run.mockResolvedValue({
    batchId: 8,
    fileName: "sample_statement.csv",
    totalRows: 156,
    imported: 150,
    duplicates: 4,
    failed: 2,
    errors: [{ row: 17, message: "Unparseable date '31/02/2026'" }],
  });
});

const file = () => new File(["Txn Date,Time\n"], "sample_statement.csv", { type: "text/csv" });

describe("<ImportPage>", () => {
  it("previews, pre-fills the suggested mapping, imports and summarises", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ImportPage />);
    expect(await screen.findByText("old.csv")).toBeInTheDocument();

    await user.upload(screen.getByLabelText("Statement file"), file());
    await screen.findByTestId("mapping-form");
    expect(apiMock.imports.preview).toHaveBeenCalled();

    expect(screen.getByTestId("map-date")).toHaveValue("Txn Date");
    expect(screen.getByTestId("map-time")).toHaveValue("Time");
    expect(screen.getByTestId("map-description")).toHaveValue("Narration");
    expect(screen.getByTestId("map-debit")).toHaveValue("Withdrawal Amt");
    expect(screen.getByTestId("map-credit")).toHaveValue("Deposit Amt");
    expect(screen.getByTestId("map-date-format")).toHaveValue("dd/MM/yyyy");
    expect(screen.getByRole("table", { name: "Sample rows" })).toHaveTextContent("SALARY ACME");

    await user.click(screen.getByTestId("import-submit"));
    const result = await screen.findByTestId("import-result");
    expect(apiMock.imports.run).toHaveBeenCalledWith(expect.any(File), {
      date: "Txn Date",
      time: "Time",
      description: "Narration",
      amount: null,
      debit: "Withdrawal Amt",
      credit: "Deposit Amt",
      dateFormat: "dd/MM/yyyy",
      invertSign: false,
    });
    expect(screen.getByTestId("import-imported")).toHaveTextContent("150");
    expect(screen.getByTestId("import-duplicates")).toHaveTextContent("4");
    expect(screen.getByTestId("import-failed")).toHaveTextContent("2");
    expect(result).toHaveTextContent("Row 17: Unparseable date");
  });

  it("supports a single signed amount column with inverted sign and auto date format", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ImportPage />);
    await user.upload(screen.getByLabelText("Statement file"), file());
    await screen.findByTestId("mapping-form");

    await user.click(screen.getByRole("radio", { name: "Single signed amount column" }));
    await user.click(screen.getByTestId("import-submit"));
    expect(await screen.findByText("Select the amount column.")).toBeInTheDocument();
    expect(apiMock.imports.run).not.toHaveBeenCalled();

    await user.selectOptions(screen.getByTestId("map-amount"), "Deposit Amt");
    await user.click(screen.getByRole("checkbox", { name: /Invert sign/ }));
    await user.click(screen.getByRole("checkbox", { name: "Auto-detect date format" }));
    await user.click(screen.getByTestId("import-submit"));
    await waitFor(() =>
      expect(apiMock.imports.run).toHaveBeenCalledWith(
        expect.any(File),
        expect.objectContaining({ amount: "Deposit Amt", debit: null, credit: null, invertSign: true, dateFormat: null }),
      ),
    );
  });

  it("rejects unsupported files client-side", async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderWithProviders(<ImportPage />);
    await user.upload(screen.getByLabelText("Statement file"), new File(["x"], "statement.pdf"));
    expect(await screen.findByText("Only .csv and .xlsx files are supported.")).toBeInTheDocument();
    expect(apiMock.imports.preview).not.toHaveBeenCalled();
  });
});
