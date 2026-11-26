import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, CheckCircle2, Pencil, Plus, Sparkles, Target, Trash2, XCircle } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState } from "@/components/states";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/api-client";
import { formatDate, formatMoney, formatPercent } from "@/lib/format";
import type { Goal, GoalInput } from "@/lib/types";

const goalSchema = z
  .object({
    name: z.string().trim().min(1, "Name is required").max(100),
    targetAmount: z.number({ error: "Target must be a number" }).positive("Target must be greater than zero"),
    savedAmount: z.number({ error: "Saved must be a number" }).min(0, "Saved cannot be negative"),
    targetDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Pick a target date"),
  })
  .refine((g) => g.savedAmount <= g.targetAmount, { message: "Saved amount cannot exceed the target", path: ["savedAmount"] });

function GoalDialog({ open, onOpenChange, editing }: { open: boolean; onOpenChange: (o: boolean) => void; editing: Goal | null }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(editing?.name ?? "");
  const [target, setTarget] = useState(editing ? String(editing.targetAmount) : "");
  const [saved, setSaved] = useState(editing ? String(editing.savedAmount) : "0");
  const [date, setDate] = useState(editing?.targetDate ?? "");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (g: GoalInput) => (editing ? api.goals.update(editing.id, g) : api.goals.create(g)),
    onSuccess: () => {
      toast.success(editing ? "Goal updated" : "Goal created");
      queryClient.invalidateQueries({ queryKey: ["goals"] });
      onOpenChange(false);
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const parsed = goalSchema.safeParse({
      name,
      targetAmount: target === "" ? NaN : Number(target),
      savedAmount: saved === "" ? 0 : Number(saved),
      targetDate: date,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Invalid input");
      return;
    }
    setError(null);
    mutation.mutate(parsed.data);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? "Edit goal" : "New savings goal"}</DialogTitle>
          <DialogDescription>What are you saving for?</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate className="space-y-4" aria-label={editing ? "Edit goal" : "New goal"}>
          {error && <Alert variant="destructive">{error}</Alert>}
          <div className="space-y-1.5">
            <Label htmlFor="goal-name">Name</Label>
            <Input id="goal-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Emergency fund" />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="goal-target">Target amount (₹)</Label>
              <Input id="goal-target" type="number" min={1} step="any" value={target} onChange={(e) => setTarget(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="goal-saved">Saved so far (₹)</Label>
              <Input id="goal-saved" type="number" min={0} step="any" value={saved} onChange={(e) => setSaved(e.target.value)} />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="goal-date">Target date</Label>
            <Input id="goal-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {editing ? "Save" : "Create goal"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function SuggestionDialog({ goal, onClose }: { goal: Goal | null; onClose: () => void }) {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["goals", goal?.id, "suggestion"],
    queryFn: () => api.goals.suggestion(goal!.id),
    enabled: !!goal,
  });
  return (
    <Dialog open={!!goal} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="size-5 text-primary" aria-hidden /> AI suggestion — {goal?.name}
          </DialogTitle>
          <DialogDescription>Based on your recent income and spending.</DialogDescription>
        </DialogHeader>
        {isPending ? (
          <div className="space-y-2" role="status" aria-label="Loading">
            <Skeleton className="h-16" />
            <Skeleton className="h-32" />
          </div>
        ) : error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : (
          <div className="space-y-4" data-testid="goal-suggestion">
            <dl className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg border p-3">
                <dt className="text-xs text-muted-foreground">Required monthly</dt>
                <dd className="text-lg font-semibold tabular-nums">{formatMoney(data.requiredMonthly)}</dd>
              </div>
              <div className="rounded-lg border p-3">
                <dt className="text-xs text-muted-foreground">Avg monthly surplus</dt>
                <dd className="text-lg font-semibold tabular-nums">{formatMoney(data.averageMonthlySurplus)}</dd>
              </div>
              <div className="rounded-lg border p-3">
                <dt className="text-xs text-muted-foreground">Feasibility</dt>
                <dd className="mt-1">
                  {data.feasible ? (
                    <Badge variant="success">
                      <CheckCircle2 className="size-3" aria-hidden /> Feasible
                    </Badge>
                  ) : (
                    <Badge variant="destructive">
                      <XCircle className="size-3" aria-hidden /> Needs cuts
                    </Badge>
                  )}
                </dd>
              </div>
            </dl>
            {data.cuts.length > 0 && (
              <Table aria-label="Suggested cuts">
                <TableHeader>
                  <TableRow>
                    <TableHead>Category</TableHead>
                    <TableHead className="text-right">Current / month</TableHead>
                    <TableHead className="text-right">Suggested</TableHead>
                    <TableHead className="text-right">Saving</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.cuts.map((c) => (
                    <TableRow key={c.category}>
                      <TableCell>{c.category}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatMoney(c.currentMonthly)}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatMoney(c.suggestedMonthly)}</TableCell>
                      <TableCell className="text-right font-medium tabular-nums text-success">{formatMoney(c.saving)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
            <div className="whitespace-pre-line rounded-lg bg-muted/50 p-4 text-sm leading-relaxed">{data.advice}</div>
            <p className="text-xs text-muted-foreground">Provider: {data.provider}</p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

export function GoalsPage() {
  const queryClient = useQueryClient();
  const { data, isPending, error, refetch } = useQuery({ queryKey: ["goals"], queryFn: api.goals.list });
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Goal | null>(null);
  const [dialogKey, setDialogKey] = useState(0);
  const [suggestFor, setSuggestFor] = useState<Goal | null>(null);

  const remove = useMutation({
    mutationFn: (id: number) => api.goals.remove(id),
    onSuccess: () => {
      toast.success("Goal deleted");
      queryClient.invalidateQueries({ queryKey: ["goals"] });
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const open = (g: Goal | null) => {
    setEditing(g);
    setDialogKey((k) => k + 1);
    setDialogOpen(true);
  };

  return (
    <div className="space-y-4">
      <PageHeader
        title="Goals"
        description="Savings goals with AI-powered plans"
        actions={
          <Button onClick={() => open(null)}>
            <Plus /> New goal
          </Button>
        }
      />
      {isPending ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" role="status" aria-label="Loading">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-48" />
          ))}
        </div>
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState
          icon={<Target className="size-8" />}
          title="No goals yet"
          description="Create a savings goal and get a monthly plan."
          action={
            <Button size="sm" onClick={() => open(null)}>
              <Plus /> New goal
            </Button>
          }
        />
      ) : (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="Goals">
          {data.map((g) => (
            <li key={g.id}>
              <Card className="flex h-full flex-col" data-testid="goal-card">
                <CardHeader className="flex-row items-start justify-between space-y-0">
                  <CardTitle className="text-base">{g.name}</CardTitle>
                  <div className="-mr-2 -mt-2 flex">
                    <Button variant="ghost" size="icon" aria-label={`Edit ${g.name}`} onClick={() => open(g)}>
                      <Pencil />
                    </Button>
                    <Button variant="ghost" size="icon" aria-label={`Delete ${g.name}`} onClick={() => remove.mutate(g.id)}>
                      <Trash2 />
                    </Button>
                  </div>
                </CardHeader>
                <CardContent className="flex-1 space-y-3">
                  <div className="flex items-baseline justify-between text-sm">
                    <span className="text-lg font-semibold tabular-nums">{formatMoney(g.savedAmount)}</span>
                    <span className="text-muted-foreground">of {formatMoney(g.targetAmount)}</span>
                  </div>
                  <Progress value={g.progress} label={`${g.name} progress`} indicatorClassName={g.progress >= 1 ? "bg-success" : undefined} />
                  <div className="flex flex-wrap justify-between gap-2 text-xs text-muted-foreground">
                    <span>{formatPercent(g.progress)} saved</span>
                    <span className="flex items-center gap-1">
                      <CalendarClock className="size-3" aria-hidden /> {formatDate(g.targetDate)} · {g.monthsRemaining} months left
                    </span>
                  </div>
                  <p className="text-sm">
                    Save <strong className="tabular-nums">{formatMoney(g.requiredMonthly)}</strong> / month to reach it.
                  </p>
                </CardContent>
                <CardFooter>
                  <Button variant="secondary" size="sm" className="w-full" onClick={() => setSuggestFor(g)}>
                    <Sparkles /> Get AI suggestion
                  </Button>
                </CardFooter>
              </Card>
            </li>
          ))}
        </ul>
      )}
      <GoalDialog key={dialogKey} open={dialogOpen} onOpenChange={setDialogOpen} editing={editing} />
      <SuggestionDialog goal={suggestFor} onClose={() => setSuggestFor(null)} />
    </div>
  );
}
