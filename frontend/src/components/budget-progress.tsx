import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { alertLevelFor, badgeVariantFor, progressColorClass } from "@/lib/budget";
import { formatMoney, formatPercent } from "@/lib/format";
import type { AlertLevel } from "@/lib/types";

const LEVEL_LABEL: Record<AlertLevel, string> = { OK: "On track", WARNING: "Warning", EXCEEDED: "Exceeded" };

export function BudgetProgress({
  category,
  spent,
  limit,
  percent,
  level,
}: {
  category: string;
  spent: number;
  limit: number;
  percent: number;
  level?: AlertLevel;
}) {
  const lvl = level ?? alertLevelFor(percent);
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="tabular-nums">
          {formatMoney(spent)} <span className="text-muted-foreground">of {formatMoney(limit)}</span>
        </span>
        <Badge variant={badgeVariantFor(lvl)} data-testid="budget-level">
          {LEVEL_LABEL[lvl]} · {formatPercent(percent)}
        </Badge>
      </div>
      <Progress
        value={percent}
        label={`${category} budget used`}
        indicatorClassName={progressColorClass(lvl)}
        className="budget-progress"
      />
    </div>
  );
}
