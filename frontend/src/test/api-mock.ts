import { vi } from "vitest";
import { tokens } from "@/lib/tokens";
import type { User } from "@/lib/types";

/** A fully mocked `api` object; use with `vi.mock("@/lib/api", ...)`. */
export const apiMock = {
  auth: {
    login: vi.fn(),
    loginMfa: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
    mfaSetup: vi.fn(),
    mfaEnable: vi.fn(),
    mfaDisable: vi.fn(),
  },
  categories: vi.fn(),
  transactions: { list: vi.fn(), update: vi.fn(), remove: vi.fn() },
  imports: { preview: vi.fn(), run: vi.fn(), history: vi.fn() },
  budgets: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), alerts: vi.fn() },
  dashboard: vi.fn(),
  forecast: vi.fn(),
  anomalies: { scan: vi.fn(), list: vi.fn(), dismiss: vi.fn() },
  recurring: vi.fn(),
  goals: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), suggestion: vi.fn() },
  assistant: { query: vi.fn(), examples: vi.fn(), history: vi.fn() },
  audit: vi.fn(),
  admin: { mlStatus: vi.fn(), retrain: vi.fn() },
};

/** Pretend a user is logged in: AuthProvider sees an access token and calls `me`. */
export function signIn(user: User) {
  tokens.set("access-token", "refresh-token");
  apiMock.auth.me.mockResolvedValue(user);
  apiMock.categories.mockResolvedValue(["Food", "Groceries", "Rent", "Shopping", "Income", "Uncategorized"]);
}

export function resetApiMock() {
  const walk = (o: Record<string, unknown>) => {
    for (const v of Object.values(o)) {
      if (typeof v === "function" && "mockReset" in v) (v as ReturnType<typeof vi.fn>).mockReset();
      else if (v && typeof v === "object") walk(v as Record<string, unknown>);
    }
  };
  walk(apiMock);
  tokens.clear();
}
