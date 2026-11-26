import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { AlertTriangle, ArrowDownRight, ArrowUpRight, Bell, PiggyBank, Scale, type LucideIcon } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState } from "@/components/states";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api } from "@/lib/api";
import { axisProps, chartColor, foldSlices, gridProps, tooltipStyle } from "@/lib/chart";
import { formatMoney, formatMoneyCompact, formatMonth, formatNumber, formatPercent } from "@/lib/format";
import { cn } from "@/lib/utils";

interface KpiProps {
  label: string;
  value: string;
  icon: LucideIcon;
  hint?: string;
  tone?: "default" | "good" | "bad" | "warn";
  testId: string;
}

function Kpi({ label, value, icon: Icon, hint, tone = "default", testId }: KpiProps) {
  return (
    <Card data-testid={testId} className="kpi-card">
      <CardContent className="flex items-start justify-between gap-3 p-5">
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="mt-1 truncate text-2xl font-semibold tabular-nums">{value}</p>
          {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
        </div>
        <span
          className={cn(
            "flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary",
            tone === "good" && "bg-success/12 text-success",
            tone === "bad" && "bg-destructive/12 text-destructive",
            tone === "warn" && "bg-warning/20 text-amber-700 dark:text-warning",
          )}
          aria-hidden
        >
          <Icon className="size-4" />
        </span>
      </CardContent>
    </Card>
  );
}

function ChartCard({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      <CardContent className="h-72">{children}</CardContent>
    </Card>
  );
}

const moneyTooltip = (v: unknown) => formatMoney(Number(v));

export function DashboardPage() {
  const { data, isPending, error, refetch } = useQuery({ queryKey: ["dashboard"], queryFn: () => api.dashboard(12) });

  if (isPending) {
    return (
      <div>
        <PageHeader title="Dashboard" description="Your money at a glance" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" role="status" aria-label="Loading">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-80" />
          ))}
        </div>
      </div>
    );
  }
  if (error) {
    return (
      <div>
        <PageHeader title="Dashboard" />
        <ErrorState error={error} onRetry={() => refetch()} />
      </div>
    );
  }

  const { totals } = data;
  const monthly = data.monthly.map((m) => ({ ...m, label: formatMonth(m.month) }));
  const netWorth = data.netWorth.map((m) => ({ ...m, label: formatMonth(m.month) }));
  const slices = foldSlices(data.categoryBreakdown, (c) => c.category);
  const empty = data.transactionCount === 0;

  return (
    <div className="space-y-4">
      <PageHeader
        title="Dashboard"
        description="Your money at a glance — current month"
        actions={
          <Button asChild variant="outline" size="sm">
            <Link to="/import">Import statement</Link>
          </Button>
        }
      />

      {data.budgetAlerts > 0 && (
        <Alert variant="warning" data-testid="budget-alert-banner">
          <Bell />
          <div className="flex flex-1 flex-wrap items-center justify-between gap-2">
            <span>
              {data.budgetAlerts} budget{data.budgetAlerts === 1 ? " is" : "s are"} at or above 80 % of the limit this month.
            </span>
            <Link to="/budgets" className="font-medium underline underline-offset-4">
              Review budgets
            </Link>
          </div>
        </Alert>
      )}

      <section aria-label="Key figures" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <Kpi testId="kpi-income" label="Income" value={formatMoney(totals.income)} icon={ArrowUpRight} tone="good" />
        <Kpi testId="kpi-expense" label="Expenses" value={formatMoney(totals.expense)} icon={ArrowDownRight} tone="bad" />
        <Kpi
          testId="kpi-net"
          label="Net"
          value={formatMoney(totals.net)}
          icon={Scale}
          tone={totals.net >= 0 ? "good" : "bad"}
        />
        <Kpi
          testId="kpi-savings-rate"
          label="Savings rate"
          value={formatPercent(totals.savingsRate)}
          icon={PiggyBank}
          hint="Net ÷ income"
        />
        <Kpi
          testId="kpi-anomalies"
          label="Flagged anomalies"
          value={formatNumber(data.flaggedAnomalies)}
          icon={AlertTriangle}
          tone={data.flaggedAnomalies > 0 ? "warn" : "default"}
        />
        <Kpi
          testId="kpi-budget-alerts"
          label="Budget alerts"
          value={formatNumber(data.budgetAlerts)}
          icon={Bell}
          tone={data.budgetAlerts > 0 ? "warn" : "default"}
        />
      </section>

      {empty ? (
        <EmptyState
          title="No transactions yet"
          description="Import a bank statement (CSV or XLSX) to see charts, budgets and forecasts."
          action={
            <Button asChild size="sm">
              <Link to="/import">Import a statement</Link>
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <ChartCard title="Income vs expenses" description="By month">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={monthly} barGap={2}>
                <CartesianGrid {...gridProps} />
                <XAxis dataKey="label" {...axisProps} />
                <YAxis {...axisProps} tickFormatter={formatMoneyCompact} width={64} />
                <Tooltip contentStyle={tooltipStyle} formatter={moneyTooltip} cursor={{ fill: "var(--muted)", opacity: 0.5 }} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="income" name="Income" fill={chartColor(0)} radius={[4, 4, 0, 0]} maxBarSize={24} />
                <Bar dataKey="expense" name="Expenses" fill={chartColor(1)} radius={[4, 4, 0, 0]} maxBarSize={24} />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>

          <ChartCard title="Spending by category" description="Current month">
            {slices.length === 0 ? (
              <EmptyState title="No spending this month" className="h-full" />
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={slices}
                    dataKey="value"
                    nameKey="name"
                    innerRadius="55%"
                    outerRadius="85%"
                    paddingAngle={1}
                    stroke="var(--card)"
                    strokeWidth={2}
                  >
                    {slices.map((s, i) => (
                      <Cell key={s.name} fill={chartColor(i)} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={tooltipStyle} formatter={moneyTooltip} />
                  <Legend layout="vertical" align="right" verticalAlign="middle" wrapperStyle={{ fontSize: 12 }} />
                </PieChart>
              </ResponsiveContainer>
            )}
          </ChartCard>

          <ChartCard title="Monthly net" description="Income minus expenses">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={monthly}>
                <CartesianGrid {...gridProps} />
                <XAxis dataKey="label" {...axisProps} />
                <YAxis {...axisProps} tickFormatter={formatMoneyCompact} width={64} />
                <Tooltip contentStyle={tooltipStyle} formatter={moneyTooltip} />
                <Line type="monotone" dataKey="net" name="Net" stroke={chartColor(0)} strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }} />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>

          <ChartCard title="Net worth" description="Opening balance plus cumulative net">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={netWorth}>
                <defs>
                  <linearGradient id="nw" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={chartColor(2)} stopOpacity={0.35} />
                    <stop offset="100%" stopColor={chartColor(2)} stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid {...gridProps} />
                <XAxis dataKey="label" {...axisProps} />
                <YAxis {...axisProps} tickFormatter={formatMoneyCompact} width={64} />
                <Tooltip contentStyle={tooltipStyle} formatter={moneyTooltip} />
                <Area type="monotone" dataKey="value" name="Net worth" stroke={chartColor(2)} strokeWidth={2} fill="url(#nw)" />
              </AreaChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>
      )}
    </div>
  );
}
