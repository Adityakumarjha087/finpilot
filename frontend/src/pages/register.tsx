import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { Loader2 } from "lucide-react";
import { z } from "zod";
import { AuthLayout } from "./auth-layout";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, errorMessage } from "@/lib/api-client";
import { useAuth } from "@/lib/auth";

const registerSchema = z.object({
  displayName: z.string().trim().min(1, "Name is required").max(80),
  email: z.email("Enter a valid email address"),
  password: z
    .string()
    .min(10, "Password must be at least 10 characters")
    .regex(/[A-Za-z]/, "Password must contain a letter")
    .regex(/\d/, "Password must contain a digit"),
});

type Field = "displayName" | "email" | "password";

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState<Record<Field, string>>({ displayName: "", email: "", password: "" });
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (f: Field) => (e: React.ChangeEvent<HTMLInputElement>) => setValues((v) => ({ ...v, [f]: e.target.value }));

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);
    const parsed = registerSchema.safeParse(values);
    if (!parsed.success) {
      const fe: Partial<Record<Field, string>> = {};
      for (const issue of parsed.error.issues) fe[issue.path[0] as Field] ??= issue.message;
      setErrors(fe);
      return;
    }
    setErrors({});
    setBusy(true);
    try {
      await register(parsed.data.email, parsed.data.password, parsed.data.displayName);
      navigate("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setErrors(err.fieldErrors as Partial<Record<Field, string>>);
      setFormError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const field = (f: Field, label: string, type: string, autoComplete: string) => (
    <div className="space-y-2">
      <Label htmlFor={`reg-${f}`}>{label}</Label>
      <Input
        id={`reg-${f}`}
        type={type}
        autoComplete={autoComplete}
        value={values[f]}
        onChange={set(f)}
        aria-invalid={!!errors[f]}
        aria-describedby={errors[f] ? `reg-${f}-error` : undefined}
      />
      {errors[f] && (
        <p id={`reg-${f}-error`} className="text-xs text-destructive">
          {errors[f]}
        </p>
      )}
    </div>
  );

  return (
    <AuthLayout title="Create your account" subtitle="Start tracking, budgeting and forecasting in minutes">
      <Card>
        <CardContent className="pt-6">
          <form onSubmit={onSubmit} noValidate className="space-y-4" aria-label="Create account">
            {formError && <Alert variant="destructive">{formError}</Alert>}
            {field("displayName", "Name", "text", "name")}
            {field("email", "Email", "email", "email")}
            {field("password", "Password", "password", "new-password")}
            <p className="text-xs text-muted-foreground">At least 10 characters, with letters and digits.</p>
            <Button type="submit" className="w-full" disabled={busy}>
              {busy && <Loader2 className="animate-spin" />} Create account
            </Button>
          </form>
        </CardContent>
      </Card>
      <p className="mt-4 text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link to="/login" className="font-medium text-primary hover:underline">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  );
}
