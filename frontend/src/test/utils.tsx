import type { ReactElement, ReactNode } from "react";
import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/lib/auth";
import { ThemeProvider } from "@/lib/theme";
import type { Page, Transaction, User } from "@/lib/types";

export function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
  });
}

export const demoUser: User = {
  id: 1,
  email: "demo@finpilot.dev",
  displayName: "Demo User",
  role: "USER",
  mfaEnabled: false,
  currency: "INR",
};

export const adminUser: User = { ...demoUser, id: 2, email: "admin@finpilot.dev", displayName: "Admin", role: "ADMIN" };

interface Options {
  route?: string;
  path?: string;
  extraRoutes?: ReactNode;
  queryClient?: QueryClient;
}

export function renderWithProviders(ui: ReactElement, { route = "/", path = "*", extraRoutes, queryClient = makeQueryClient() }: Options = {}) {
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <TooltipProvider delayDuration={0}>
          <AuthProvider>
            <MemoryRouter initialEntries={[route]}>
              <Routes>
                <Route path={path} element={ui} />
                {extraRoutes}
              </Routes>
            </MemoryRouter>
          </AuthProvider>
          <Toaster />
        </TooltipProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}

export function tx(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 1,
    postedAt: "2026-11-03",
    description: "UPI/SWIGGY/1234/Payment",
    merchant: "Swiggy",
    amount: -450.5,
    category: "Food",
    categorySource: "ML",
    categoryConfidence: 0.92,
    notes: null,
    anomaly: { status: "NONE", score: null, reasons: [] },
    ...overrides,
  };
}

export function page<T>(content: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1, ...overrides };
}

export function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json", ...headers },
  });
}
