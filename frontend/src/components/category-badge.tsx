import { Sparkles, UserCheck, Wand2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { formatPercent } from "@/lib/format";
import type { CategorySource } from "@/lib/types";

const SOURCE_LABEL: Record<CategorySource, string> = {
  ML: "Predicted by the ML model",
  USER: "Set by you",
  RULE: "Matched a merchant rule",
  NONE: "Not categorised",
};

export function CategoryBadge({
  category,
  source,
  confidence,
}: {
  category: string;
  source: CategorySource;
  confidence: number | null;
}) {
  const Icon = source === "ML" ? Sparkles : source === "USER" ? UserCheck : source === "RULE" ? Wand2 : null;
  const variant = category === "Uncategorized" || source === "NONE" ? "secondary" : source === "USER" ? "success" : "default";
  const conf = source === "ML" && confidence != null ? ` ${formatPercent(confidence)}` : "";
  return (
    <Badge variant={variant} data-testid="category-badge" title={`${SOURCE_LABEL[source]}${conf ? ` (confidence${conf})` : ""}`}>
      {Icon && <Icon className="size-3" aria-hidden />}
      {category}
      {conf && <span className="opacity-70">·{conf}</span>}
    </Badge>
  );
}
