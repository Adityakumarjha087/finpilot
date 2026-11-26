import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { QRCodeSVG } from "qrcode.react";
import { Loader2, Monitor, Moon, ShieldCheck, ShieldOff, Sun } from "lucide-react";
import { toast } from "sonner";
import { CopyButton } from "@/components/assistant-result";
import { PageHeader } from "@/components/page-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/api-client";
import { useAuth } from "@/lib/auth";
import { useTheme, type Theme } from "@/lib/theme";
import { cn } from "@/lib/utils";

function CodeForm({ label, submitLabel, busy, onSubmit }: { label: string; submitLabel: string; busy: boolean; onSubmit: (code: string) => void }) {
  const [code, setCode] = useState("");
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (/^\d{6}$/.test(code)) onSubmit(code);
  };
  return (
    <form onSubmit={submit} className="flex flex-col gap-2 sm:flex-row sm:items-end" aria-label={submitLabel}>
      <div className="space-y-1.5">
        <Label htmlFor={`code-${submitLabel}`}>{label}</Label>
        <Input
          id={`code-${submitLabel}`}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          className="w-40"
          value={code}
          placeholder="123456"
          onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
        />
      </div>
      <Button type="submit" disabled={busy || code.length !== 6}>
        {busy && <Loader2 className="animate-spin" />} {submitLabel}
      </Button>
    </form>
  );
}

function TwoFactorCard() {
  const { user, setUser } = useAuth();
  const setup = useMutation({ mutationFn: api.auth.mfaSetup });
  const enable = useMutation({
    mutationFn: (code: string) => api.auth.mfaEnable(code),
    onSuccess: (u) => {
      setUser(u);
      setup.reset();
      toast.success("Two-factor authentication enabled");
    },
  });
  const disable = useMutation({
    mutationFn: (code: string) => api.auth.mfaDisable(code),
    onSuccess: (u) => {
      setUser(u);
      toast.success("Two-factor authentication disabled");
    },
  });
  const err = setup.error ?? enable.error ?? disable.error;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          Two-factor authentication
          {user?.mfaEnabled ? <Badge variant="success">On</Badge> : <Badge variant="secondary">Off</Badge>}
        </CardTitle>
        <CardDescription>Protect your account with a time-based code from an authenticator app.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {err && <Alert variant="destructive">{errorMessage(err)}</Alert>}
        {user?.mfaEnabled ? (
          <div className="space-y-3">
            <p className="flex items-center gap-2 text-sm">
              <ShieldCheck className="size-4 text-success" aria-hidden /> 2FA is active. Enter a current code to turn it off.
            </p>
            <CodeForm label="Authenticator code" submitLabel="Disable 2FA" busy={disable.isPending} onSubmit={(c) => disable.mutate(c)} />
          </div>
        ) : setup.data ? (
          <div className="space-y-4">
            <div className="flex flex-col gap-4 sm:flex-row">
              <div className="w-fit rounded-lg bg-white p-3" data-testid="mfa-qr">
                <QRCodeSVG value={setup.data.otpauthUri} size={168} title="Authenticator QR code" />
              </div>
              <div className="min-w-0 space-y-2 text-sm">
                <p>Scan the QR code with Google Authenticator, 1Password, Authy or similar. Or enter the secret manually:</p>
                <div className="flex items-center gap-2">
                  <code className="break-all rounded bg-muted px-2 py-1 font-mono text-xs" data-testid="mfa-secret">
                    {setup.data.secret}
                  </code>
                  <CopyButton text={setup.data.secret} label="Copy secret" />
                </div>
                <details>
                  <summary className="cursor-pointer text-xs text-muted-foreground">Show otpauth URI</summary>
                  <code className="mt-1 block break-all rounded bg-muted px-2 py-1 font-mono text-xs">{setup.data.otpauthUri}</code>
                </details>
              </div>
            </div>
            <CodeForm label="Code from the app" submitLabel="Enable 2FA" busy={enable.isPending} onSubmit={(c) => enable.mutate(c)} />
          </div>
        ) : (
          <Button onClick={() => setup.mutate()} disabled={setup.isPending}>
            {setup.isPending ? <Loader2 className="animate-spin" /> : <ShieldOff />} Set up 2FA
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

const THEMES: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
];

export function SettingsPage() {
  const { user } = useAuth();
  const { theme, setTheme } = useTheme();
  return (
    <div className="space-y-4">
      <PageHeader title="Settings" />
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Profile</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">Name</dt>
              <dd className="font-medium">{user?.displayName}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Email</dt>
              <dd className="font-medium">{user?.email}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Role</dt>
              <dd>
                <Badge variant={user?.role === "ADMIN" ? "default" : "secondary"}>{user?.role}</Badge>
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Currency</dt>
              <dd className="font-medium">{user?.currency ?? "INR"}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>
      <TwoFactorCard />
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Appearance</CardTitle>
          <CardDescription>System follows your operating system setting.</CardDescription>
        </CardHeader>
        <CardContent>
          <div role="radiogroup" aria-label="Theme" className="inline-flex rounded-lg border p-1">
            {THEMES.map((t) => (
              <button
                key={t.value}
                type="button"
                role="radio"
                aria-checked={theme === t.value}
                onClick={() => setTheme(t.value)}
                className={cn(
                  "flex cursor-pointer items-center gap-1.5 rounded-md px-3 py-1.5 text-sm",
                  theme === t.value ? "bg-primary text-primary-foreground" : "hover:bg-accent",
                )}
              >
                <t.icon className="size-4" aria-hidden /> {t.label}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
