import { apiRequest, buildQuery } from "./api-client";
import type {
  AssistantHistoryItem,
  AssistantResult,
  AuditEntry,
  BudgetAlert,
  BudgetStatus,
  ColumnMapping,
  Dashboard,
  Forecast,
  Goal,
  GoalInput,
  GoalSuggestion,
  ImportBatch,
  ImportPreview,
  ImportResult,
  LoginResponse,
  MlStatus,
  Page,
  RecurringPayment,
  RetrainResult,
  ScanResult,
  Transaction,
  TransactionQuery,
  User,
} from "./types";

export const api = {
  auth: {
    login: (email: string, password: string) =>
      apiRequest<LoginResponse>("/auth/login", { method: "POST", body: { email, password }, auth: false }),
    loginMfa: (mfaToken: string, code: string) =>
      apiRequest<LoginResponse>("/auth/login/mfa", { method: "POST", body: { mfaToken, code }, auth: false }),
    register: (email: string, password: string, displayName: string) =>
      apiRequest<LoginResponse>("/auth/register", {
        method: "POST",
        body: { email, password, displayName },
        auth: false,
      }),
    logout: (refreshToken: string) =>
      apiRequest<void>("/auth/logout", { method: "POST", body: { refreshToken }, auth: false }),
    me: () => apiRequest<User>("/auth/me"),
    mfaSetup: () => apiRequest<{ secret: string; otpauthUri: string }>("/auth/mfa/setup", { method: "POST" }),
    mfaEnable: (code: string) => apiRequest<User>("/auth/mfa/enable", { method: "POST", body: { code } }),
    mfaDisable: (code: string) => apiRequest<User>("/auth/mfa/disable", { method: "POST", body: { code } }),
  },
  categories: () => apiRequest<string[]>("/categories"),
  transactions: {
    list: (q: TransactionQuery) =>
      apiRequest<Page<Transaction>>(
        `/transactions${buildQuery({
          page: q.page,
          size: q.size,
          sort: q.sort,
          category: q.category,
          q: q.q,
          from: q.from,
          to: q.to,
          flagged: q.flagged ? true : undefined,
        })}`,
      ),
    update: (id: number, patch: { category?: string; notes?: string | null }) =>
      apiRequest<Transaction>(`/transactions/${id}`, { method: "PATCH", body: patch }),
    remove: (id: number) => apiRequest<void>(`/transactions/${id}`, { method: "DELETE" }),
  },
  imports: {
    preview: (file: File) => {
      const fd = new FormData();
      fd.append("file", file);
      return apiRequest<ImportPreview>("/imports/preview", { method: "POST", formData: fd });
    },
    run: (file: File, mapping: ColumnMapping) => {
      const fd = new FormData();
      fd.append("file", file);
      fd.append("mapping", new Blob([JSON.stringify(mapping)], { type: "application/json" }));
      return apiRequest<ImportResult>("/imports", { method: "POST", formData: fd });
    },
    history: () => apiRequest<ImportBatch[]>("/imports"),
  },
  budgets: {
    list: (month?: string) => apiRequest<BudgetStatus[]>(`/budgets${buildQuery({ month })}`),
    create: (category: string, monthlyLimit: number) =>
      apiRequest<BudgetStatus>("/budgets", { method: "POST", body: { category, monthlyLimit } }),
    update: (id: number, category: string, monthlyLimit: number) =>
      apiRequest<BudgetStatus>(`/budgets/${id}`, { method: "PUT", body: { category, monthlyLimit } }),
    remove: (id: number) => apiRequest<void>(`/budgets/${id}`, { method: "DELETE" }),
    alerts: (month?: string) => apiRequest<BudgetAlert[]>(`/budgets/alerts${buildQuery({ month })}`),
  },
  dashboard: (months = 12) => apiRequest<Dashboard>(`/dashboard${buildQuery({ months })}`),
  forecast: (horizon = 3) => apiRequest<Forecast>(`/forecast${buildQuery({ horizon })}`),
  anomalies: {
    scan: () => apiRequest<ScanResult>("/anomalies/scan", { method: "POST" }),
    list: () => apiRequest<Transaction[]>("/anomalies"),
    dismiss: (transactionId: number) =>
      apiRequest<Transaction>(`/anomalies/${transactionId}/dismiss`, { method: "POST" }),
  },
  recurring: () => apiRequest<RecurringPayment[]>("/recurring"),
  goals: {
    list: () => apiRequest<Goal[]>("/goals"),
    create: (g: GoalInput) => apiRequest<Goal>("/goals", { method: "POST", body: g }),
    update: (id: number, g: GoalInput) => apiRequest<Goal>(`/goals/${id}`, { method: "PUT", body: g }),
    remove: (id: number) => apiRequest<void>(`/goals/${id}`, { method: "DELETE" }),
    suggestion: (id: number) => apiRequest<GoalSuggestion>(`/goals/${id}/suggestion`),
  },
  assistant: {
    query: (question: string) =>
      apiRequest<AssistantResult>("/assistant/query", { method: "POST", body: { question } }),
    examples: () => apiRequest<string[]>("/assistant/examples"),
    history: () => apiRequest<AssistantHistoryItem[]>("/assistant/history"),
  },
  audit: (page: number, size: number) => apiRequest<Page<AuditEntry>>(`/audit${buildQuery({ page, size })}`),
  admin: {
    mlStatus: () => apiRequest<MlStatus>("/admin/ml/status"),
    retrain: () => apiRequest<RetrainResult>("/admin/ml/retrain", { method: "POST" }),
  },
};
