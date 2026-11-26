export type Role = "USER" | "ADMIN";

export interface User {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  mfaEnabled: boolean;
  currency: "INR";
}

export interface LoginResponse {
  mfaRequired: boolean;
  mfaToken?: string;
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
  user?: User;
}

export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type CategorySource = "ML" | "USER" | "RULE" | "NONE";
export type AnomalyStatus = "NONE" | "FLAGGED" | "DISMISSED";

export interface Transaction {
  id: number;
  postedAt: string;
  description: string;
  merchant: string;
  amount: number;
  category: string;
  categorySource: CategorySource;
  categoryConfidence: number | null;
  notes: string | null;
  anomaly: { status: AnomalyStatus; score: number | null; reasons: string[] };
}

export interface TransactionQuery {
  page: number;
  size: number;
  sort?: string;
  category?: string;
  q?: string;
  from?: string;
  to?: string;
  flagged?: boolean;
}

export interface ColumnMapping {
  date: string;
  time?: string | null;
  description: string;
  amount?: string | null;
  debit?: string | null;
  credit?: string | null;
  dateFormat?: string | null;
  invertSign?: boolean;
}

export interface ImportPreview {
  fileName: string;
  headers: string[];
  sampleRows: string[][];
  totalRows: number;
  suggestedMapping: ColumnMapping;
}

export interface ImportResult {
  batchId: number | string;
  fileName: string;
  totalRows: number;
  imported: number;
  duplicates: number;
  failed: number;
  errors: { row: number; message: string }[];
}

export interface ImportBatch {
  id: number;
  fileName: string;
  importedAt: string;
  totalRows: number;
  imported: number;
  duplicates: number;
  failed: number;
}

export type AlertLevel = "OK" | "WARNING" | "EXCEEDED";

export interface BudgetStatus {
  id: number;
  category: string;
  monthlyLimit: number;
  spent: number;
  percent: number;
  alertLevel: AlertLevel;
  month: string;
}

export interface BudgetAlert {
  budgetId: number;
  category: string;
  alertLevel: AlertLevel;
  percent: number;
  message: string;
}

export interface Dashboard {
  currency: "INR";
  totals: { income: number; expense: number; net: number; savingsRate: number };
  monthly: { month: string; income: number; expense: number; net: number }[];
  categoryBreakdown: { category: string; amount: number }[];
  netWorth: { month: string; value: number }[];
  flaggedAnomalies: number;
  budgetAlerts: number;
  transactionCount: number;
}

export interface ForecastPoint {
  month: string;
  mean: number;
  lower: number;
  upper: number;
}

export interface HistoryPoint {
  month: string;
  amount: number;
}

export interface Forecast {
  horizon: number;
  confidence: number;
  generatedAt: string;
  categories: { category: string; method: string; history: HistoryPoint[]; forecast: ForecastPoint[] }[];
  total: { history: HistoryPoint[]; forecast: ForecastPoint[] };
}

export interface ScanResult {
  scanned: number;
  flagged: number;
}

export type Cadence = "WEEKLY" | "MONTHLY" | "QUARTERLY" | "YEARLY";

export interface RecurringPayment {
  merchant: string;
  category: string;
  cadence: Cadence;
  averageAmount: number;
  lastCharged: string;
  nextExpected: string;
  occurrences: number;
  monthlyCost: number;
  subscription: boolean;
}

export interface Goal {
  id: number;
  name: string;
  targetAmount: number;
  savedAmount: number;
  targetDate: string;
  progress: number;
  monthsRemaining: number;
  requiredMonthly: number;
}

export interface GoalInput {
  name: string;
  targetAmount: number;
  savedAmount: number;
  targetDate: string;
}

export interface GoalSuggestion {
  goalId: number;
  requiredMonthly: number;
  averageMonthlySurplus: number;
  feasible: boolean;
  cuts: { category: string; currentMonthly: number; suggestedMonthly: number; saving: number }[];
  advice: string;
  provider: string;
}

export type ChartType = "bar" | "line" | "pie" | "table" | "number";

export interface AssistantResult {
  question: string;
  sql: string;
  columns: string[];
  rows: (string | number | null)[][];
  chart: { type: ChartType; x?: string; y?: string };
  summary: string;
  provider: string;
  durationMs: number;
}

export interface AssistantHistoryItem {
  question: string;
  sql: string;
  rowCount: number;
  createdAt: string;
}

export interface AuditEntry {
  id: number;
  action: string;
  entityType: string;
  entityId: string | number | null;
  ip: string | null;
  details: string | null;
  createdAt: string;
  userEmail: string;
}

export interface MlStatus {
  modelVersion: string;
  trainedAt: string | null;
  holdoutAccuracy: number | null;
  nSamples: number;
  nCorrections: number;
  pendingCorrections: number;
}

export interface RetrainResult {
  modelVersion: string;
  corrections: number;
  holdoutAccuracy: number | null;
}
