#!/usr/bin/env node
// Captures README screenshots from a running stack (docker compose up) using the demo user's synthetic data.
// Usage: node scripts/screenshots.mjs   (BASE_URL defaults to http://localhost:20800)
// Playwright is resolved from frontend/node_modules (run `npm ci && npx playwright install chromium` there first).
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";
import fs from "node:fs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(path.join(root, "frontend", "package.json"));
const { chromium } = require("@playwright/test");

const BASE = process.env.BASE_URL ?? "http://localhost:20800";
const OUT = path.join(root, "docs", "screenshots");
fs.mkdirSync(OUT, { recursive: true });

async function newPage(browser, theme) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
  await context.addInitScript((t) => localStorage.setItem("finpilot.theme", t), theme);
  const page = await context.newPage();
  await page.goto(`${BASE}/login`);
  await page.getByLabel("Email").fill("demo@finpilot.dev");
  await page.getByLabel("Password").fill("DemoPass123!");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByRole("heading", { name: "Dashboard", exact: true }).waitFor();
  return page;
}

async function settle(page) {
  await page.waitForLoadState("networkidle");
  // let Recharts finish its entry animations
  await page.waitForTimeout(1500);
  await page.mouse.move(0, 0);
}

async function nav(page, label) {
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: label, exact: true }).click();
  await page.getByRole("heading", { name: label, exact: true }).waitFor();
}

async function shot(page, name, fullPage = false) {
  await settle(page);
  await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage });
  console.log(`saved ${name}.png`);
}

const browser = await chromium.launch();
try {
  const page = await newPage(browser, "light");
  await page.locator(".recharts-surface").first().waitFor();
  await shot(page, "dashboard");

  await nav(page, "Ask");
  await page.getByTestId("ask-input").fill("How much did I spend on food last month vs this month?");
  await page.getByTestId("ask-submit").click();
  await page.getByTestId("assistant-sql").waitFor();
  await page.getByTestId("assistant-chart").waitFor();
  // scroll so the answer card (question, summary, chart, table and generated SQL) fills the viewport
  await page.getByTestId("assistant-result").evaluate((el) => window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 64));
  await shot(page, "assistant");

  await nav(page, "Forecast");
  await page.locator(".recharts-surface").first().waitFor();
  await page.getByLabel("Category").selectOption("Groceries");
  await shot(page, "forecast");

  await nav(page, "Anomalies");
  await page.getByTestId("anomaly-card").first().waitFor();
  await shot(page, "anomalies");

  await nav(page, "Budgets");
  await page.getByTestId("budget-card").first().waitFor();
  await shot(page, "budgets");

  await nav(page, "Import");
  await page.getByTestId("import-file-input").setInputFiles(path.join(root, "docs", "samples", "sample_statement.csv"));
  await page.getByTestId("mapping-form").waitFor();
  await shot(page, "import-mapping");
  await page.context().close();

  const dark = await newPage(browser, "dark");
  await dark.locator(".recharts-surface").first().waitFor();
  await shot(dark, "dashboard-dark");
  await dark.context().close();
} finally {
  await browser.close();
}
