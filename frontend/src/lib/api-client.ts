import { tokens } from "./tokens";
import type { LoginResponse, Problem } from "./types";

/**
 * Origin of the API. Empty (same origin, proxied by nginx / the Vite dev server) unless the SPA is hosted
 * separately from the backend, in which case VITE_API_URL is baked in at build time.
 */
export function apiBase(origin: string | undefined): string {
  return `${(origin ?? "").trim().replace(/\/+$/, "")}/api/v1`;
}

export const API_BASE = apiBase(import.meta.env.VITE_API_URL);

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;
  readonly retryAfter: number | null;

  constructor(problem: Problem, retryAfter: number | null = null) {
    super(describeProblem(problem, retryAfter));
    this.name = "ApiError";
    this.status = problem.status;
    this.problem = problem;
    this.retryAfter = retryAfter;
  }

  get fieldErrors(): Record<string, string> {
    return this.problem.errors ?? {};
  }
}

function describeProblem(p: Problem, retryAfter: number | null): string {
  if (p.status === 429) {
    const wait = retryAfter != null ? ` Please try again in ${retryAfter} second${retryAfter === 1 ? "" : "s"}.` : " Please try again shortly.";
    return `Too many requests.${wait}`;
  }
  if (p.status === 0) return p.detail ?? "Network error: the server could not be reached.";
  return p.detail || p.title || `Request failed (${p.status})`;
}

/** Human readable message for any thrown value. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return "Something went wrong";
}

type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler | null = null;
type SessionHandler = (res: LoginResponse) => void;
let onSessionRefreshed: SessionHandler | null = null;

/** Called when refresh fails: the app should drop the session and go to /login. */
export function setUnauthorizedHandler(fn: UnauthorizedHandler | null) {
  onUnauthorized = fn;
}

/** Called after a successful token refresh (e.g. to update the cached user). */
export function setSessionRefreshedHandler(fn: SessionHandler | null) {
  onSessionRefreshed = fn;
}

let refreshInFlight: Promise<boolean> | null = null;

/**
 * Single-flight refresh: concurrent 401s share one `/auth/refresh` call.
 * Resolves true when a new access token was obtained.
 */
export function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  const refreshToken = tokens.getRefresh();
  if (!refreshToken) return Promise.resolve(false);
  refreshInFlight = (async () => {
    try {
      const res = await fetch(`${API_BASE}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return false;
      const body = (await res.json()) as LoginResponse;
      if (!body.accessToken) return false;
      tokens.set(body.accessToken, body.refreshToken ?? refreshToken);
      onSessionRefreshed?.(body);
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  formData?: FormData;
  /** Attach bearer token and attempt refresh on 401 (default true). */
  auth?: boolean;
  signal?: AbortSignal;
}

async function toProblem(res: Response): Promise<Problem> {
  const fallback: Problem = { status: res.status, title: res.statusText || `HTTP ${res.status}` };
  try {
    const text = await res.text();
    if (!text) return fallback;
    const parsed = JSON.parse(text) as Partial<Problem>;
    return { ...fallback, ...parsed, status: parsed.status ?? res.status };
  } catch {
    return fallback;
  }
}

function parseRetryAfter(res: Response): number | null {
  const h = res.headers.get("Retry-After");
  if (!h) return null;
  const n = Number(h);
  if (Number.isFinite(n)) return Math.max(0, Math.round(n));
  const date = Date.parse(h);
  return Number.isNaN(date) ? null : Math.max(0, Math.round((date - Date.now()) / 1000));
}

export function buildQuery(params: Record<string, string | number | boolean | null | undefined>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "" || v === false) continue;
    sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}

export async function apiRequest<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, formData, auth = true, signal } = opts;

  const send = () => {
    const headers: Record<string, string> = { Accept: "application/json, application/problem+json" };
    if (body !== undefined && !formData) headers["Content-Type"] = "application/json";
    const access = tokens.getAccess();
    if (auth && access) headers.Authorization = `Bearer ${access}`;
    return fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: formData ?? (body !== undefined ? JSON.stringify(body) : undefined),
      signal,
    });
  };

  let res: Response;
  try {
    // With only a refresh token (e.g. after reload) refresh first instead of a guaranteed 401.
    if (auth && !tokens.getAccess() && tokens.getRefresh()) await refreshSession();
    res = await send();
    if (res.status === 401 && auth) {
      const refreshed = await refreshSession();
      if (refreshed) {
        res = await send();
      }
      if (!refreshed || res.status === 401) {
        tokens.clear();
        onUnauthorized?.();
      }
    }
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError({ status: 0, title: "Network error", detail: "Network error: the server could not be reached." });
  }

  if (!res.ok) {
    throw new ApiError(await toProblem(res), res.status === 429 ? parseRetryAfter(res) : null);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
