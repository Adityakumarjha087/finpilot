import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, Loader2, Radar, X } from "lucide-react";
import { toast } from "sonner";
import { CategoryBadge } from "@/components/category-badge";
import { Money } from "@/components/money";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState, LoadingRows } from "@/components/states";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/api-client";
import { formatDate, formatNumber } from "@/lib/format";
import type { Transaction } from "@/lib/types";

export function AnomaliesPage() {
  const queryClient = useQueryClient();
  const { data, isPending, error, refetch } = useQuery({ queryKey: ["anomalies"], queryFn: api.anomalies.list });

  const scan = useMutation({
    mutationFn: api.anomalies.scan,
    onSuccess: (r) => {
      toast.success(`Scanned ${formatNumber(r.scanned)} transactions, flagged ${formatNumber(r.flagged)}`);
      queryClient.invalidateQueries({ queryKey: ["anomalies"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const dismiss = useMutation({
    mutationFn: (id: number) => api.anomalies.dismiss(id),
    onSuccess: (_t, id) => {
      queryClient.setQueryData<Transaction[]>(["anomalies"], (old) => old?.filter((t) => t.id !== id));
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      toast.success("Anomaly dismissed");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  return (
    <div className="space-y-4">
      <PageHeader
        title="Anomalies"
        description="Unusual transactions detected by the anomaly model"
        actions={
          <Button onClick={() => scan.mutate()} disabled={scan.isPending} data-testid="scan-now">
            {scan.isPending ? <Loader2 className="animate-spin" /> : <Radar />} Scan now
          </Button>
        }
      />

      {scan.data && (
        <Alert variant="success" data-testid="scan-result">
          <CheckCircle2 />
          <span>
            Scanned <strong>{formatNumber(scan.data.scanned)}</strong> transactions and flagged{" "}
            <strong>{formatNumber(scan.data.flagged)}</strong>.
          </span>
        </Alert>
      )}

      {isPending ? (
        <LoadingRows rows={4} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState icon={<CheckCircle2 className="size-8" />} title="Nothing unusual" description="No flagged transactions. Run a scan after importing new data." />
      ) : (
        <ul className="space-y-3" aria-label="Flagged transactions">
          {data.map((t) => (
            <li key={t.id}>
              <Card data-testid="anomaly-card">
                <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0 space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <AlertTriangle className="size-4 text-amber-600 dark:text-warning" aria-hidden />
                      <span className="font-medium">{t.merchant || t.description}</span>
                      <CategoryBadge category={t.category} source={t.categorySource} confidence={t.categoryConfidence} />
                      {t.anomaly.score != null && <Badge variant="warning">Score {t.anomaly.score.toFixed(2)}</Badge>}
                    </div>
                    <p className="text-sm text-muted-foreground">
                      {formatDate(t.postedAt)} · {t.description}
                    </p>
                    {t.anomaly.reasons.length > 0 && (
                      <ul className="list-disc space-y-0.5 pl-5 text-sm" aria-label="Reasons">
                        {t.anomaly.reasons.map((r) => (
                          <li key={r}>{r}</li>
                        ))}
                      </ul>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-3 sm:flex-col sm:items-end">
                    <Money value={t.amount} className="text-lg font-semibold" />
                    <Button variant="outline" size="sm" onClick={() => dismiss.mutate(t.id)} disabled={dismiss.isPending && dismiss.variables === t.id}>
                      <X /> Dismiss
                    </Button>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
