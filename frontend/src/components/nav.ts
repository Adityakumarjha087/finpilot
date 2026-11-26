import {
  AlertTriangle,
  BrainCircuit,
  FileUp,
  LayoutDashboard,
  LineChart,
  MessageSquareText,
  PiggyBank,
  Receipt,
  Repeat,
  ScrollText,
  Settings,
  Target,
  type LucideIcon,
} from "lucide-react";
import type { Role } from "@/lib/types";

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  role?: Role;
}

export const NAV_ITEMS: NavItem[] = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard },
  { to: "/transactions", label: "Transactions", icon: Receipt },
  { to: "/import", label: "Import", icon: FileUp },
  { to: "/budgets", label: "Budgets", icon: PiggyBank },
  { to: "/forecast", label: "Forecast", icon: LineChart },
  { to: "/anomalies", label: "Anomalies", icon: AlertTriangle },
  { to: "/ask", label: "Ask", icon: MessageSquareText },
  { to: "/subscriptions", label: "Subscriptions", icon: Repeat },
  { to: "/goals", label: "Goals", icon: Target },
  { to: "/audit", label: "Audit log", icon: ScrollText },
  { to: "/admin/ml", label: "ML model", icon: BrainCircuit, role: "ADMIN" },
  { to: "/settings", label: "Settings", icon: Settings },
];

export function navItemsFor(role: Role | undefined): NavItem[] {
  return NAV_ITEMS.filter((i) => !i.role || i.role === role);
}

