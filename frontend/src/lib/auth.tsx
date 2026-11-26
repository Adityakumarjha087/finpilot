import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { refreshSession, setSessionRefreshedHandler, setUnauthorizedHandler } from "./api-client";
import { tokens } from "./tokens";
import type { LoginResponse, User } from "./types";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  /** Returns the raw response; when `mfaRequired` the caller must collect a code. */
  login: (email: string, password: string) => Promise<LoginResponse>;
  verifyMfa: (mfaToken: string, code: string) => Promise<LoginResponse>;
  register: (email: string, password: string, displayName: string) => Promise<LoginResponse>;
  logout: () => Promise<void>;
  setUser: (u: User) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUserState] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>(() =>
    tokens.getAccess() || tokens.getRefresh() ? "loading" : "anonymous",
  );

  const dropSession = useCallback(() => {
    tokens.clear();
    setUserState(null);
    setStatus("anonymous");
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(dropSession);
    setSessionRefreshedHandler((res) => {
      if (res.user) setUserState(res.user);
    });
    return () => {
      setUnauthorizedHandler(null);
      setSessionRefreshedHandler(null);
    };
  }, [dropSession]);

  // Restore a session from the persisted refresh token on first load.
  useEffect(() => {
    if (status !== "loading") return;
    let cancelled = false;
    (async () => {
      try {
        if (!tokens.getAccess()) {
          const ok = await refreshSession();
          if (!ok) throw new Error("no session");
        }
        const me = await api.auth.me();
        if (!cancelled) {
          setUserState(me);
          setStatus("authenticated");
        }
      } catch {
        if (!cancelled) {
          tokens.clear();
          setStatus("anonymous");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [status]);

  const applyLogin = useCallback((res: LoginResponse) => {
    if (!res.mfaRequired && res.accessToken) {
      tokens.set(res.accessToken, res.refreshToken);
      if (res.user) {
        setUserState(res.user);
        setStatus("authenticated");
      } else {
        setStatus("loading");
      }
    }
    return res;
  }, []);

  const login = useCallback(
    async (email: string, password: string) => applyLogin(await api.auth.login(email, password)),
    [applyLogin],
  );
  const verifyMfa = useCallback(
    async (mfaToken: string, code: string) => applyLogin(await api.auth.loginMfa(mfaToken, code)),
    [applyLogin],
  );
  const register = useCallback(
    async (email: string, password: string, displayName: string) =>
      applyLogin(await api.auth.register(email, password, displayName)),
    [applyLogin],
  );

  const logout = useCallback(async () => {
    const refresh = tokens.getRefresh();
    try {
      if (refresh) await api.auth.logout(refresh);
    } catch {
      /* best effort */
    }
    dropSession();
  }, [dropSession]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, verifyMfa, register, logout, setUser: setUserState }),
    [status, user, login, verifyMfa, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
