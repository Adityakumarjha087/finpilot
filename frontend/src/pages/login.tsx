import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { Info, Loader2, ShieldCheck } from "lucide-react";
import { z } from "zod";
import { AuthLayout } from "./auth-layout";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorMessage } from "@/lib/api-client";
import { useAuth } from "@/lib/auth";

export const DEMO_EMAIL = "demo@finpilot.dev";
export const DEMO_PASSWORD = "DemoPass123!";

const loginSchema = z.object({
  email: z.email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

const mfaSchema = z.object({ code: z.string().regex(/^\d{6}$/, "Enter the 6-digit code from your authenticator app") });

type FieldErrors = Partial<Record<"email" | "password" | "code", string>>;

export function LoginPage() {
  const { login, verifyMfa } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);
    const parsed = loginSchema.safeParse({ email, password });
    if (!parsed.success) {
      const fe: FieldErrors = {};
      for (const issue of parsed.error.issues) fe[issue.path[0] as "email" | "password"] = issue.message;
      setErrors(fe);
      return;
    }
    setErrors({});
    setBusy(true);
    try {
      const res = await login(parsed.data.email, parsed.data.password);
      if (res.mfaRequired && res.mfaToken) {
        setMfaToken(res.mfaToken);
      } else {
        navigate(from, { replace: true });
      }
    } catch (err) {
      setFormError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const onSubmitMfa = async (e: FormEvent) => {
    e.preventDefault();
    if (!mfaToken) return;
    setFormError(null);
    const parsed = mfaSchema.safeParse({ code: code.trim() });
    if (!parsed.success) {
      setErrors({ code: parsed.error.issues[0]?.message });
      return;
    }
    setErrors({});
    setBusy(true);
    try {
      await verifyMfa(mfaToken, parsed.data.code);
      navigate(from, { replace: true });
    } catch (err) {
      setFormError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (mfaToken) {
    return (
      <AuthLayout title="Two-factor authentication" subtitle="Enter the 6-digit code from your authenticator app">
        <Card>
          <CardContent className="pt-6">
            <form onSubmit={onSubmitMfa} noValidate className="space-y-4" aria-label="Two-factor authentication">
              {formError && <Alert variant="destructive">{formError}</Alert>}
              <div className="space-y-2">
                <Label htmlFor="mfa-code">Authentication code</Label>
                <Input
                  id="mfa-code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  autoFocus
                  placeholder="123456"
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                  aria-invalid={!!errors.code}
                  aria-describedby={errors.code ? "mfa-code-error" : undefined}
                />
                {errors.code && (
                  <p id="mfa-code-error" className="text-xs text-destructive">
                    {errors.code}
                  </p>
                )}
              </div>
              <Button type="submit" className="w-full" disabled={busy}>
                {busy ? <Loader2 className="animate-spin" /> : <ShieldCheck />} Verify
              </Button>
              <Button
                variant="link"
                className="w-full"
                onClick={() => {
                  setMfaToken(null);
                  setCode("");
                  setFormError(null);
                }}
              >
                Back to sign in
              </Button>
            </form>
          </CardContent>
        </Card>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to your FinPilot account">
      <Card>
        <CardContent className="pt-6">
          <form onSubmit={onSubmit} noValidate className="space-y-4" aria-label="Sign in">
            {formError && <Alert variant="destructive">{formError}</Alert>}
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? "email-error" : undefined}
              />
              {errors.email && (
                <p id="email-error" className="text-xs text-destructive">
                  {errors.email}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? "password-error" : undefined}
              />
              {errors.password && (
                <p id="password-error" className="text-xs text-destructive">
                  {errors.password}
                </p>
              )}
            </div>
            <Button type="submit" className="w-full" disabled={busy}>
              {busy && <Loader2 className="animate-spin" />} Sign in
            </Button>
          </form>
          <div className="mt-4 flex gap-2 rounded-md border border-dashed bg-muted/40 p-3 text-xs text-muted-foreground" data-testid="demo-hint">
            <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
            <div>
              Demo account: <code className="font-mono text-foreground">{DEMO_EMAIL}</code> /{" "}
              <code className="font-mono text-foreground">{DEMO_PASSWORD}</code>
              <Button
                variant="link"
                size="sm"
                className="h-auto px-1 py-0 text-xs"
                onClick={() => {
                  setEmail(DEMO_EMAIL);
                  setPassword(DEMO_PASSWORD);
                }}
              >
                Fill in
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
      <p className="mt-4 text-center text-sm text-muted-foreground">
        No account?{" "}
        <Link to="/register" className="font-medium text-primary hover:underline">
          Create one
        </Link>
      </p>
    </AuthLayout>
  );
}
