import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiRequest, buildQuery, refreshSession, setUnauthorizedHandler } from "./api-client";
import { tokens } from "./tokens";
import { jsonResponse } from "@/test/utils";

const fetchMock = vi.fn<typeof fetch>();

function authHeader(call: Parameters<typeof fetch>): string | undefined {
  return (call[1]?.headers as Record<string, string> | undefined)?.Authorization;
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
  tokens.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  setUnauthorizedHandler(null);
});

describe("apiRequest", () => {
  it("attaches the bearer token and parses JSON", async () => {
    tokens.set("A1", "R1");
    fetchMock.mockResolvedValueOnce(jsonResponse({ ok: true }));
    await expect(apiRequest("/dashboard")).resolves.toEqual({ ok: true });
    const call = fetchMock.mock.calls[0]!;
    expect(call[0]).toBe("/api/v1/dashboard");
    expect(authHeader(call)).toBe("Bearer A1");
  });

  it("on 401 refreshes once, stores the rotated refresh token and retries", async () => {
    tokens.set("OLD", "R1");
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 401, title: "Unauthorized" }, 401))
      .mockResolvedValueOnce(jsonResponse({ mfaRequired: false, accessToken: "NEW", refreshToken: "R2" }))
      .mockResolvedValueOnce(jsonResponse([1, 2]));

    await expect(apiRequest("/goals")).resolves.toEqual([1, 2]);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1]![0]).toBe("/api/v1/auth/refresh");
    expect(JSON.parse(String(fetchMock.mock.calls[1]![1]!.body))).toEqual({ refreshToken: "R1" });
    expect(authHeader(fetchMock.mock.calls[2]!)).toBe("Bearer NEW");
    expect(tokens.getRefresh()).toBe("R2");
  });

  it("shares a single refresh between concurrent 401s", async () => {
    tokens.set("OLD", "R1");
    let refreshCalls = 0;
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith("/auth/refresh")) {
        refreshCalls++;
        await new Promise((r) => setTimeout(r, 10));
        return jsonResponse({ mfaRequired: false, accessToken: "NEW", refreshToken: "R2" });
      }
      const auth = (init?.headers as Record<string, string>).Authorization;
      return auth === "Bearer NEW" ? jsonResponse({ url }) : jsonResponse({ status: 401 }, 401);
    });

    const results = await Promise.all([apiRequest("/a"), apiRequest("/b"), apiRequest("/c")]);
    expect(results).toEqual([{ url: "/api/v1/a" }, { url: "/api/v1/b" }, { url: "/api/v1/c" }]);
    expect(refreshCalls).toBe(1);
  });

  it("logs out when the refresh fails", async () => {
    tokens.set("OLD", "R1");
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 401 }, 401))
      .mockResolvedValueOnce(jsonResponse({ status: 401, detail: "refresh token revoked" }, 401));

    await expect(apiRequest("/goals")).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(tokens.getAccess()).toBeNull();
    expect(tokens.getRefresh()).toBeNull();
  });

  it("does not try to refresh for unauthenticated endpoints", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ status: 401, detail: "Bad credentials" }, 401));
    await expect(apiRequest("/auth/login", { method: "POST", body: {}, auth: false })).rejects.toThrow("Bad credentials");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("turns 429 into a friendly message using Retry-After", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ status: 429, title: "Too Many Requests" }, 429, { "Retry-After": "42" }));
    const err = await apiRequest("/auth/login", { method: "POST", body: {}, auth: false }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).retryAfter).toBe(42);
    expect((err as ApiError).message).toBe("Too many requests. Please try again in 42 seconds.");
  });

  it("exposes RFC 7807 detail and field errors", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ status: 422, title: "Validation failed", detail: "Invalid input", errors: { email: "taken" } }, 422),
    );
    const err = (await apiRequest("/auth/register", { method: "POST", body: {}, auth: false }).catch((e: unknown) => e)) as ApiError;
    expect(err.message).toBe("Invalid input");
    expect(err.fieldErrors).toEqual({ email: "taken" });
  });

  it("returns undefined for 204 and maps network failures", async () => {
    tokens.set("A", "R");
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(apiRequest("/goals/1", { method: "DELETE" })).resolves.toBeUndefined();
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await expect(apiRequest("/goals")).rejects.toMatchObject({ status: 0 });
  });

  it("refreshSession resolves false without a refresh token", async () => {
    await expect(refreshSession()).resolves.toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("buildQuery", () => {
  it("skips empty values", () => {
    expect(buildQuery({ page: 0, size: 25, q: "", category: undefined, flagged: false, sort: "amount,asc" })).toBe(
      "?page=0&size=25&sort=amount%2Casc",
    );
    expect(buildQuery({})).toBe("");
  });
});
