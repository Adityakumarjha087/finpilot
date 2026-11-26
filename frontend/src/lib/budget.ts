import type { AlertLevel } from "./types";

/** WARNING at >= 80 %, EXCEEDED at >= 100 %. `percent` is a ratio (0.8 = 80 %). */
export function alertLevelFor(percent: number): AlertLevel {
  if (percent >= 1) return "EXCEEDED";
  if (percent >= 0.8) return "WARNING";
  return "OK";
}

export function progressColorClass(level: AlertLevel): string {
  switch (level) {
    case "EXCEEDED":
      return "bg-destructive";
    case "WARNING":
      return "bg-warning";
    default:
      return "bg-success";
  }
}

export function badgeVariantFor(level: AlertLevel): "success" | "warning" | "destructive" {
  return level === "EXCEEDED" ? "destructive" : level === "WARNING" ? "warning" : "success";
}
