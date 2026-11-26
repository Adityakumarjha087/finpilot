import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route } from "react-router";
import { LoginPage } from "./login";
import { ApiError } from "@/lib/api-client";
import { tokens } from "@/lib/tokens";
import { apiMock, resetApiMock } from "@/test/api-mock";
import { demoUser, renderWithProviders } from "@/test/utils";

vi.mock("@/lib/api", async () => ({ api: (await import("@/test/api-mock")).apiMock }));

const home = <Route path="/" element={<h1>Home page</h1>} />;

beforeEach(() => resetApiMock());

describe("<LoginPage>", () => {
  it("validates input before calling the API", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />, { route: "/login", path: "/login", extraRoutes: home });
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Enter a valid email address")).toBeInTheDocument();
    expect(screen.getByText("Password is required")).toBeInTheDocument();
    expect(apiMock.auth.login).not.toHaveBeenCalled();
  });

  it("fills demo credentials, signs in and stores tokens", async () => {
    const user = userEvent.setup();
    apiMock.auth.login.mockResolvedValue({ mfaRequired: false, accessToken: "AT", refreshToken: "RT", expiresIn: 900, user: demoUser });
    renderWithProviders(<LoginPage />, { route: "/login", path: "/login", extraRoutes: home });
    expect(screen.getByTestId("demo-hint")).toHaveTextContent("demo@finpilot.dev");
    await user.click(screen.getByRole("button", { name: "Fill in" }));
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("heading", { name: "Home page" })).toBeInTheDocument();
    expect(apiMock.auth.login).toHaveBeenCalledWith("demo@finpilot.dev", "DemoPass123!");
    expect(tokens.getAccess()).toBe("AT");
    expect(localStorage.getItem("finpilot.refreshToken")).toBe("RT");
  });

  it("asks for a TOTP code when MFA is required", async () => {
    const user = userEvent.setup();
    apiMock.auth.login.mockResolvedValue({ mfaRequired: true, mfaToken: "MFA-1" });
    apiMock.auth.loginMfa.mockResolvedValue({ mfaRequired: false, accessToken: "AT", refreshToken: "RT", user: { ...demoUser, mfaEnabled: true } });
    renderWithProviders(<LoginPage />, { route: "/login", path: "/login", extraRoutes: home });

    await user.type(screen.getByLabelText("Email"), "demo@finpilot.dev");
    await user.type(screen.getByLabelText("Password"), "DemoPass123!");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    const codeInput = await screen.findByLabelText("Authentication code");
    await user.type(codeInput, "12a3");
    await user.click(screen.getByRole("button", { name: "Verify" }));
    await waitFor(() => expect(codeInput).toHaveAttribute("aria-invalid", "true"));
    expect(apiMock.auth.loginMfa).not.toHaveBeenCalled();

    await user.type(codeInput, "456");
    await user.click(screen.getByRole("button", { name: "Verify" }));
    await waitFor(() => expect(apiMock.auth.loginMfa).toHaveBeenCalledWith("MFA-1", "123456"));
    expect(await screen.findByRole("heading", { name: "Home page" })).toBeInTheDocument();
  });

  it("shows a friendly rate-limit message", async () => {
    const user = userEvent.setup();
    apiMock.auth.login.mockRejectedValue(new ApiError({ status: 429, title: "Too Many Requests" }, 30));
    renderWithProviders(<LoginPage />, { route: "/login", path: "/login", extraRoutes: home });
    await user.type(screen.getByLabelText("Email"), "demo@finpilot.dev");
    await user.type(screen.getByLabelText("Password"), "wrong");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Too many requests. Please try again in 30 seconds.");
  });
});
