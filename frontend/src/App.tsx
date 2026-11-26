import { lazy, Suspense, type ReactNode } from "react";
import { BrowserRouter, Route, Routes } from "react-router";
import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { AppShell } from "@/components/app-shell";
import { FullPageSpinner, ProtectedRoute, PublicOnlyRoute } from "@/components/protected-route";
import { LoadingBlock } from "@/components/states";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/lib/auth";
import { ThemeProvider, useTheme } from "@/lib/theme";
import { LoginPage } from "@/pages/login";
import { RegisterPage } from "@/pages/register";
import { NotFoundPage } from "@/pages/not-found";

const DashboardPage = lazy(() => import("@/pages/dashboard").then((m) => ({ default: m.DashboardPage })));
const TransactionsPage = lazy(() => import("@/pages/transactions").then((m) => ({ default: m.TransactionsPage })));
const ImportPage = lazy(() => import("@/pages/import").then((m) => ({ default: m.ImportPage })));
const BudgetsPage = lazy(() => import("@/pages/budgets").then((m) => ({ default: m.BudgetsPage })));
const ForecastPage = lazy(() => import("@/pages/forecast").then((m) => ({ default: m.ForecastPage })));
const AnomaliesPage = lazy(() => import("@/pages/anomalies").then((m) => ({ default: m.AnomaliesPage })));
const AskPage = lazy(() => import("@/pages/ask").then((m) => ({ default: m.AskPage })));
const SubscriptionsPage = lazy(() => import("@/pages/subscriptions").then((m) => ({ default: m.SubscriptionsPage })));
const GoalsPage = lazy(() => import("@/pages/goals").then((m) => ({ default: m.GoalsPage })));
const SettingsPage = lazy(() => import("@/pages/settings").then((m) => ({ default: m.SettingsPage })));
const AuditPage = lazy(() => import("@/pages/audit").then((m) => ({ default: m.AuditPage })));
const AdminMlPage = lazy(() => import("@/pages/audit").then((m) => ({ default: m.AdminMlPage })));

function Lazy({ children }: { children: ReactNode }) {
  return <Suspense fallback={<LoadingBlock className="h-96" />}>{children}</Suspense>;
}

function ThemedToaster() {
  const { resolved } = useTheme();
  return <Toaster theme={resolved} richColors closeButton position="bottom-right" />;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <PublicOnlyRoute>
            <LoginPage />
          </PublicOnlyRoute>
        }
      />
      <Route
        path="/register"
        element={
          <PublicOnlyRoute>
            <RegisterPage />
          </PublicOnlyRoute>
        }
      />
      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Lazy><DashboardPage /></Lazy>} />
        <Route path="transactions" element={<Lazy><TransactionsPage /></Lazy>} />
        <Route path="import" element={<Lazy><ImportPage /></Lazy>} />
        <Route path="budgets" element={<Lazy><BudgetsPage /></Lazy>} />
        <Route path="forecast" element={<Lazy><ForecastPage /></Lazy>} />
        <Route path="anomalies" element={<Lazy><AnomaliesPage /></Lazy>} />
        <Route path="ask" element={<Lazy><AskPage /></Lazy>} />
        <Route path="subscriptions" element={<Lazy><SubscriptionsPage /></Lazy>} />
        <Route path="goals" element={<Lazy><GoalsPage /></Lazy>} />
        <Route path="settings" element={<Lazy><SettingsPage /></Lazy>} />
        <Route path="audit" element={<Lazy><AuditPage /></Lazy>} />
        <Route
          path="admin/ml"
          element={
            <ProtectedRoute role="ADMIN">
              <Lazy>
                <AdminMlPage />
              </Lazy>
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function App({ queryClient }: { queryClient: QueryClient }) {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <TooltipProvider delayDuration={150}>
          <AuthProvider>
            <BrowserRouter>
              <Suspense fallback={<FullPageSpinner />}>
                <AppRoutes />
              </Suspense>
            </BrowserRouter>
          </AuthProvider>
          <ThemedToaster />
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
