import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import { Route, Routes } from "react-router";
import { AppShell } from "./app-shell";
import { ProtectedRoute } from "./protected-route";
import { apiMock, resetApiMock, signIn } from "@/test/api-mock";
import { adminUser, demoUser, renderWithProviders } from "@/test/utils";

vi.mock("@/lib/api", async () => ({ api: (await import("@/test/api-mock")).apiMock }));

const login = <Route path="/login" element={<h1>Login screen</h1>} />;

beforeEach(() => resetApiMock());

describe("<ProtectedRoute>", () => {
  it("redirects anonymous users to /login", async () => {
    renderWithProviders(
      <ProtectedRoute>
        <h1>Secret</h1>
      </ProtectedRoute>,
      { route: "/transactions", path: "/transactions", extraRoutes: login },
    );
    expect(await screen.findByRole("heading", { name: "Login screen" })).toBeInTheDocument();
    expect(screen.queryByText("Secret")).not.toBeInTheDocument();
  });

  it("restores the session from a stored refresh token", async () => {
    signIn(demoUser);
    renderWithProviders(
      <ProtectedRoute>
        <h1>Secret</h1>
      </ProtectedRoute>,
      { route: "/x", path: "/x", extraRoutes: login },
    );
    expect(await screen.findByRole("heading", { name: "Secret" })).toBeInTheDocument();
    expect(apiMock.auth.me).toHaveBeenCalled();
  });

  it("sends a USER away from admin-only routes", async () => {
    signIn(demoUser);
    renderWithProviders(
      <ProtectedRoute role="ADMIN">
        <h1>Admin area</h1>
      </ProtectedRoute>,
      { route: "/admin/ml", path: "/admin/ml", extraRoutes: <Route path="/" element={<h1>Dashboard</h1>} /> },
    );
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
  });
});

function Shell() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<p>content</p>} />
      </Route>
    </Routes>
  );
}

describe("<AppShell> navigation", () => {
  it("hides admin-only items for USER", async () => {
    signIn(demoUser);
    renderWithProviders(
      <ProtectedRoute>
        <Shell />
      </ProtectedRoute>,
    );
    const nav = await screen.findByRole("navigation", { name: "Main" });
    expect(within(nav).getByRole("link", { name: "Transactions" })).toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "ML model" })).not.toBeInTheDocument();
  });

  it("shows admin-only items for ADMIN", async () => {
    signIn(adminUser);
    renderWithProviders(
      <ProtectedRoute>
        <Shell />
      </ProtectedRoute>,
    );
    const nav = await screen.findByRole("navigation", { name: "Main" });
    expect(within(nav).getByRole("link", { name: "ML model" })).toHaveAttribute("href", "/admin/ml");
  });
});
