import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test, type Page } from "@playwright/test";

const here = path.dirname(fileURLToPath(import.meta.url));
const SAMPLE_CSV = path.resolve(here, "../../docs/samples/sample_statement.csv");

const DEMO_EMAIL = process.env.E2E_EMAIL ?? "demo@finpilot.dev";
const DEMO_PASSWORD = process.env.E2E_PASSWORD ?? "DemoPass123!";

async function goTo(page: Page, label: string) {
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: label, exact: true }).click();
}

test("demo user: dashboard -> import -> transactions -> ask", async ({ page }) => {
  // Log in
  await page.goto("/login");
  await page.getByLabel("Email").fill(DEMO_EMAIL);
  await page.getByLabel("Password").fill(DEMO_PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  // Dashboard KPI cards
  await expect(page.getByRole("heading", { name: "Dashboard", exact: true })).toBeVisible();
  for (const id of ["kpi-income", "kpi-expense", "kpi-net", "kpi-savings-rate", "kpi-anomalies", "kpi-budget-alerts"]) {
    await expect(page.getByTestId(id)).toBeVisible();
  }

  // Import the sample statement
  await goTo(page, "Import");
  await expect(page.getByRole("heading", { name: "Import", exact: true })).toBeVisible();
  await page.getByTestId("import-file-input").setInputFiles(SAMPLE_CSV);
  await expect(page.getByTestId("mapping-form")).toBeVisible();

  // Confirm (and if needed set) the suggested mapping
  await page.getByTestId("map-date").selectOption("Txn Date");
  await page.getByTestId("map-time").selectOption("Time");
  await page.getByTestId("map-description").selectOption("Narration");
  await page.getByRole("radio", { name: "Separate debit / credit columns" }).check();
  await page.getByTestId("map-debit").selectOption("Withdrawal Amt");
  await page.getByTestId("map-credit").selectOption("Deposit Amt");
  await page.getByRole("checkbox", { name: "Auto-detect date format" }).uncheck();
  await page.getByTestId("map-date-format").fill("dd/MM/yyyy");
  await expect(page.getByRole("table", { name: "Sample rows" })).toContainText("SALARY");

  await page.getByTestId("import-submit").click();
  const result = page.getByTestId("import-result");
  await expect(result).toBeVisible({ timeout: 60_000 });
  await expect(result).toContainText("Import complete");
  await expect(page.getByTestId("import-imported")).toHaveText(/\d+/);
  await expect(page.getByTestId("import-duplicates")).toHaveText(/\d+/);
  await expect(page.getByTestId("import-failed")).toHaveText(/\d+/);

  // Transactions page shows rows with category badges
  await goTo(page, "Transactions");
  const rows = page.getByTestId("transaction-row");
  await expect(rows.first()).toBeVisible();
  expect(await rows.count()).toBeGreaterThan(0);
  await expect(rows.first().getByTestId("category-badge")).toBeVisible();

  // Ask a natural-language question
  await goTo(page, "Ask");
  await page.getByTestId("ask-input").fill("How much did I spend on food last month vs this month?");
  await page.getByTestId("ask-submit").click();
  const sql = page.getByTestId("assistant-sql");
  await expect(sql).toBeVisible({ timeout: 60_000 });
  await expect(sql).toContainText(/select/i);
  await expect(
    page.getByTestId("assistant-table").or(page.getByTestId("assistant-chart")).or(page.getByTestId("assistant-number")).first(),
  ).toBeVisible();
});
