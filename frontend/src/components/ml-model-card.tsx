import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BrainCircuit, Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { ErrorState, LoadingRows } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/api-client";
import { formatDateTime, formatNumber, formatPercent } from "@/lib/format";

export function MlModelCard() {
  const queryClient = useQueryClient();
  const status = useQuery({ queryKey: ["admin", "ml"], queryFn: api.admin.mlStatus });
  const retrain = useMutation({
    mutationFn: api.admin.retrain,
    onSuccess: (r) => {
      toast.success(`Model ${r.modelVersion} trained`, {
        description: `${formatNumber(r.corrections)} corrections · hold-out accuracy ${formatPercent(r.holdoutAccuracy)}`,
      });
      queryClient.invalidateQueries({ queryKey: ["admin", "ml"] });
    },
    onError: (err) => toast.error(`Retrain failed: ${errorMessage(err)}`),
  });

  return (
    <Card data-testid="ml-model-card">
      <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2 text-base">
            <BrainCircuit className="size-4 text-primary" aria-hidden /> Categorisation model
          </CardTitle>
          <CardDescription>User corrections feed an active-learning retrain loop.</CardDescription>
        </div>
        <Button onClick={() => retrain.mutate()} disabled={retrain.isPending}>
          {retrain.isPending ? <Loader2 className="animate-spin" /> : <RefreshCw />} Retrain
        </Button>
      </CardHeader>
      <CardContent>
        {status.isPending ? (
          <LoadingRows rows={2} />
        ) : status.error ? (
          <ErrorState error={status.error} onRetry={() => status.refetch()} />
        ) : (
          <dl className="grid grid-cols-2 gap-4 text-sm md:grid-cols-3">
            {[
              ["Version", status.data.modelVersion],
              ["Trained at", formatDateTime(status.data.trainedAt)],
              ["Hold-out accuracy", formatPercent(status.data.holdoutAccuracy)],
              ["Training samples", formatNumber(status.data.nSamples)],
              ["Corrections", formatNumber(status.data.nCorrections)],
              ["Pending corrections", formatNumber(status.data.pendingCorrections)],
            ].map(([k, v]) => (
              <div key={k}>
                <dt className="text-muted-foreground">{k}</dt>
                <dd className="font-medium tabular-nums">{v}</dd>
              </div>
            ))}
          </dl>
        )}
      </CardContent>
    </Card>
  );
}
