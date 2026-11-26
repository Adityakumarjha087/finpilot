import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import type { Role } from "@/lib/types";

export function FullPageSpinner() {
  return (
    <div className="flex min-h-screen items-center justify-center" role="status" aria-label="Loading">
      <Loader2 className="size-8 animate-spin text-primary" />
    </div>
  );
}

export function ProtectedRoute({ children, role }: { children: ReactNode; role?: Role }) {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === "loading") return <FullPageSpinner />;
  if (status !== "authenticated" || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (role && user.role !== role) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  if (status === "loading") return <FullPageSpinner />;
  if (status === "authenticated") return <Navigate to="/" replace />;
  return <>{children}</>;
}
