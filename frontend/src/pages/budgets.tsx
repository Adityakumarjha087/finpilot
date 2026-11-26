import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, ChevronLeft, ChevronRight, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";
import { BudgetProgress } from "@/components/budget-progress";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState } from "@/components/states";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { useCategories } from "@/hooks/use-categories";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/api-client";
import { currentMonth, formatMonth, shiftMonth } from "@/lib/format";
import type { BudgetStatus } from "@/lib/types";

const budgetSchema = z.object({
  category: z.string().min(1, "Choose a category"),
  monthlyLimit: z.number({ error: "Enter a number" }).positive("Limit must be greater than zero"),
});

function BudgetDialog({
  open,
  onOpenChange,
  editing,
  existing,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  editing: BudgetStatus | null;
  existing: string[];
}) {
  const queryClient = useQueryClient();
  const categories = useCategories().filter((c) => c !== "Income" && c !== "Transfers");
  const [category, setCategory] = useState(editing?.category ?? "");
  const [limit, setLimit] = useState(editing ? String(editing.monthlyLimit) : "");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (v: { category: string; monthlyLimit: number }) =>
      editing ? api.budgets.update(editing.id, v.category, v.monthlyLimit) : api.budgets.create(v.category, v.monthlyLimit),
    onSuccess: () => {
      toast.success(editing ? "Budget updated" : "Budget created");
      queryClient.invalidateQueries({ queryKey: ["budgets"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      onOpenChange(false);
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const parsed = budgetSchema.safeParse({ category, monthlyLimit: limit === "" ? NaN : Number(limit) });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Invalid input");
      return;
    }
    setError(null);
    mutation.mutate(parsed.data);
  };

  const options = categories.filter((c) => c === editing?.category || !existing.includes(c));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? "Edit budget" : "New budget"}</DialogTitle>
          <DialogDescription>Monthly spending limit for a category. You get a warning at 80 % and an alert at 100 %.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate className="space-y-4" aria-label={editing ? "Edit budget" : "New budget"}>
          {error && <Alert variant="destructive">{error}</Alert>}
          <div className="space-y-1.5">
            <Label htmlFor="budget-category">Category</Label>
            <NativeSelect id="budget-category" value={category} onChange={(e) => setCategory(e.target.value)}>
              <option value="">Select a category</option>
              {options.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="budget-limit">Monthly limit (₹)</Label>
            <Input id="budget-limit" type="number" inputMode="decimal" min={1} step="any" value={limit} onChange={(e) => setLimit(e.target.value)} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {editing ? "Save" : "Create budget"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function BudgetsPage() {
  const queryClient = useQueryClient();
  const [month, setMonth] = useState(currentMonth());
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<BudgetStatus | null>(null);
  const [dialogKey, setDialogKey] = useState(0);

  const budgets = useQuery({ queryKey: ["budgets", month], queryFn: () => api.budgets.list(month) });
  const alerts = useQuery({ queryKey: ["budgets", "alerts", month], queryFn: () => api.budgets.alerts(month) });

  const remove = useMutation({
    mutationFn: (id: number) => api.budgets.remove(id),
    onSuccess: () => {
      toast.success("Budget deleted");
      queryClient.invalidateQueries({ queryKey: ["budgets"] });
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const openDialog = (b: BudgetStatus | null) => {
    setEditing(b);
    setDialogKey((k) => k + 1);
    setDialogOpen(true);
  };

  return (
    <div className="space-y-4">
      <PageHeader
        title="Budgets"
        description="Monthly limits per category"
        actions={
          <>
            <div className="flex items-center gap-1">
              <Button variant="outline" size="icon" aria-label="Previous month" onClick={() => setMonth((m) => shiftMonth(m, -1))}>
                <ChevronLeft />
              </Button>
              <Label htmlFor="budget-month" className="sr-only">
                Month
              </Label>
              <Input
                id="budget-month"
                type="month"
                className="w-40"
                value={month}
                onChange={(e) => e.target.value && setMonth(e.target.value)}
              />
              <Button variant="outline" size="icon" aria-label="Next month" onClick={() => setMonth((m) => shiftMonth(m, 1))}>
                <ChevronRight />
              </Button>
            </div>
            <Button onClick={() => openDialog(null)}>
              <Plus /> New budget
            </Button>
          </>
        }
      />

      {alerts.data && alerts.data.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Alerts for {formatMonth(month)}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {alerts.data.map((a) => (
              <Alert key={a.budgetId} variant={a.alertLevel === "EXCEEDED" ? "destructive" : "warning"}>
                <AlertTriangle />
                <span>
                  <strong>{a.category}:</strong> {a.message}
                </span>
              </Alert>
            ))}
          </CardContent>
        </Card>
      )}

      {budgets.isPending ? (
        <div className="grid gap-4 md:grid-cols-2" role="status" aria-label="Loading">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-32" />
          ))}
        </div>
      ) : budgets.error ? (
        <ErrorState error={budgets.error} onRetry={() => budgets.refetch()} />
      ) : budgets.data.length === 0 ? (
        <EmptyState
          title="No budgets yet"
          description="Set a monthly limit for a category to get warnings before you overspend."
          action={
            <Button size="sm" onClick={() => openDialog(null)}>
              <Plus /> New budget
            </Button>
          }
        />
      ) : (
        <ul className="grid gap-4 md:grid-cols-2" aria-label="Budgets">
          {budgets.data.map((b) => (
            <li key={b.id}>
              <Card data-testid="budget-card">
                <CardHeader className="flex-row items-center justify-between space-y-0">
                  <CardTitle className="text-base">{b.category}</CardTitle>
                  <div className="flex gap-1">
                    <Button variant="ghost" size="icon" aria-label={`Edit ${b.category} budget`} onClick={() => openDialog(b)}>
                      <Pencil />
                    </Button>
                    <Button variant="ghost" size="icon" aria-label={`Delete ${b.category} budget`} onClick={() => remove.mutate(b.id)}>
                      <Trash2 />
                    </Button>
                  </div>
                </CardHeader>
                <CardContent>
                  <BudgetProgress category={b.category} spent={b.spent} limit={b.monthlyLimit} percent={b.percent} level={b.alertLevel} />
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}

      <BudgetDialog
        key={dialogKey}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        editing={editing}
        existing={budgets.data?.map((b) => b.category) ?? []}
      />
    </div>
  );
}
