import { useQuery } from "@tanstack/react-query";
import { Repeat } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState, LoadingRows } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { formatDate, formatMoney } from "@/lib/format";
import type { Cadence } from "@/lib/types";

const CADENCE: Record<Cadence, string> = { WEEKLY: "Weekly", MONTHLY: "Monthly", QUARTERLY: "Quarterly", YEARLY: "Yearly" };

export function SubscriptionsPage() {
  const { data, isPending, error, refetch } = useQuery({ queryKey: ["recurring"], queryFn: api.recurring });
  const subs = data?.filter((r) => r.subscription) ?? [];
  const totalSubs = subs.reduce((s, r) => s + r.monthlyCost, 0);
  const totalAll = data?.reduce((s, r) => s + r.monthlyCost, 0) ?? 0;

  return (
    <div className="space-y-4">
      <PageHeader title="Subscriptions" description="Recurring payments detected from your transaction history" />
      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <CardHeader>
            <CardDescription>Subscriptions per month</CardDescription>
            <CardTitle className="text-2xl tabular-nums" data-testid="subscriptions-total">
              {isPending ? "…" : formatMoney(totalSubs)}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>All recurring payments per month</CardDescription>
            <CardTitle className="text-2xl tabular-nums">{isPending ? "…" : formatMoney(totalAll)}</CardTitle>
          </CardHeader>
        </Card>
      </div>
      <Card>
        <CardContent className="p-0">
          {isPending ? (
            <LoadingRows rows={5} className="p-4" />
          ) : error ? (
            <ErrorState error={error} onRetry={() => refetch()} className="m-4" />
          ) : data.length === 0 ? (
            <EmptyState className="m-4" icon={<Repeat className="size-8" />} title="No recurring payments detected" />
          ) : (
            <Table aria-label="Recurring payments">
              <TableHeader>
                <TableRow>
                  <TableHead>Merchant</TableHead>
                  <TableHead>Cadence</TableHead>
                  <TableHead className="text-right">Average</TableHead>
                  <TableHead className="text-right">Monthly cost</TableHead>
                  <TableHead>Last charged</TableHead>
                  <TableHead>Next expected</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...data]
                  .sort((a, b) => b.monthlyCost - a.monthlyCost)
                  .map((r) => (
                    <TableRow key={`${r.merchant}-${r.cadence}`}>
                      <TableCell>
                        <div className="flex items-center gap-2 font-medium">
                          {r.merchant}
                          {r.subscription && <Badge>Subscription</Badge>}
                        </div>
                        <div className="text-xs text-muted-foreground">
                          {r.category} · {r.occurrences} charges
                        </div>
                      </TableCell>
                      <TableCell>{CADENCE[r.cadence] ?? r.cadence}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatMoney(r.averageAmount)}</TableCell>
                      <TableCell className="text-right font-medium tabular-nums">{formatMoney(r.monthlyCost)}</TableCell>
                      <TableCell className="whitespace-nowrap">{formatDate(r.lastCharged)}</TableCell>
                      <TableCell className="whitespace-nowrap">{formatDate(r.nextExpected)}</TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
