import { AlertTriangle } from "lucide-react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { Transaction } from "@/lib/types";

export function AnomalyIndicator({ anomaly }: { anomaly: Transaction["anomaly"] }) {
  if (anomaly.status !== "FLAGGED") return null;
  const label = `Flagged as unusual${anomaly.score != null ? ` (score ${anomaly.score.toFixed(2)})` : ""}`;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button type="button" className="inline-flex cursor-help text-amber-600 dark:text-warning" aria-label={`${label}: ${anomaly.reasons.join("; ")}`} data-testid="anomaly-indicator">
          <AlertTriangle className="size-4" aria-hidden />
        </button>
      </TooltipTrigger>
      <TooltipContent>
        <p className="font-medium">{label}</p>
        {anomaly.reasons.length > 0 && (
          <ul className="mt-1 list-disc pl-4">
            {anomaly.reasons.map((r) => (
              <li key={r}>{r}</li>
            ))}
          </ul>
        )}
      </TooltipContent>
    </Tooltip>
  );
}
