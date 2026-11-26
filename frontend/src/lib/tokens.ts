/**
 * Token storage. The short-lived access token is kept only in memory; the rotating
 * refresh token is persisted in localStorage so a reload can restore the session.
 */
const REFRESH_KEY = "finpilot.refreshToken";

let accessToken: string | null = null;

function safeStorage(): Storage | null {
  try {
    return typeof window !== "undefined" ? window.localStorage : null;
  } catch {
    return null;
  }
}

export const tokens = {
  getAccess(): string | null {
    return accessToken;
  },
  setAccess(token: string | null) {
    accessToken = token;
  },
  getRefresh(): string | null {
    return safeStorage()?.getItem(REFRESH_KEY) ?? null;
  },
  setRefresh(token: string | null) {
    const s = safeStorage();
    if (!s) return;
    if (token) s.setItem(REFRESH_KEY, token);
    else s.removeItem(REFRESH_KEY);
  },
  set(access: string | null | undefined, refresh: string | null | undefined) {
    accessToken = access ?? null;
    this.setRefresh(refresh ?? null);
  },
  clear() {
    accessToken = null;
    this.setRefresh(null);
  },
};
