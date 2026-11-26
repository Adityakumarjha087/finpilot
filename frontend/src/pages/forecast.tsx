import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ForecastChart } from "@/components/forecast-chart";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { formatDateTime, formatMoney, formatMonth } from "@/lib/format";

export function ForecastPage() {
  const { data, isPending, error, refetch } = useQuery({ queryKey: ["forecast", 3], queryFn: () => api.forecast(3) });
  const [selected, setSelected] = useState<string>("");

  if (isPending) {
    return (
      <div className="space-y-4">
        <PageHeader title="Forecast" description="Expected spending for the next 3 months" />
        <LoadingBlock className="h-80" />
        <LoadingBlock className="h-80" />
      </div>
    );
  }
  if (error) {
    return (
      <div>
        <PageHeader title="Forecast" />
        <ErrorState error={error} onRetry={() => refetch()} />
      </div>
    );
  }
  if (data.categories.length === 0 && data.total.history.length === 0) {
    return (
      <div>
        <PageHeader title="Forecast" />
        <EmptyState title="Not enough history yet" description="Import a few months of transactions to unlock forecasts." />
      </div>
    );
  }

  const cat = data.categories.find((c) => c.category === selected) ?? data.categories[0];

  return (
    <div className="space-y-4">
      <PageHeader
        title="Forecast"
        description={`Expected spending for the next ${data.horizon} months · generated ${formatDateTime(data.generatedAt)}`}
      />

      <Card>
        <CardHeader className="gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-1.5">
            <CardTitle className="text-base">By category</CardTitle>
            <CardDescription>
              History (solid) and forecast (dashed) with a shaded {Math.round(data.confidence * 100)} % confidence band.
            </CardDescription>
          </div>
          <div className="flex items-end gap-3">
            {cat && (
              <Badge variant="secondary" data-testid="forecast-method">
                Method: {cat.method}
              </Badge>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="forecast-category">Category</Label>
              <NativeSelect id="forecast-category" className="w-48" value={cat?.category ?? ""} onChange={(e) => setSelected(e.target.value)}>
                {data.categories.map((c) => (
                  <option key={c.category} value={c.category}>
                    {c.category}
                  </option>
                ))}
              </NativeSelect>
            </div>
          </div>
        </CardHeader>
        <CardContent className="h-80">
          {cat ? <ForecastChart history={cat.history} forecast={cat.forecast} confidence={data.confidence} /> : <EmptyState title="No categories" />}
        </CardContent>
        {cat && cat.forecast.length > 0 && (
          <CardContent>
            <Table aria-label={`${cat.category} forecast`}>
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead className="text-right">Expected</TableHead>
                  <TableHead className="text-right">Range</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {cat.forecast.map((f) => (
                  <TableRow key={f.month}>
                    <TableCell>{formatMonth(f.month)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatMoney(f.mean)}</TableCell>
                    <TableCell className="text-right tabular-nums text-muted-foreground">
                      {formatMoney(f.lower)} – {formatMoney(f.upper)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        )}
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Total spending</CardTitle>
          <CardDescription>All expense categories combined</CardDescription>
        </CardHeader>
        <CardContent className="h-80">
          <ForecastChart history={data.total.history} forecast={data.total.forecast} confidence={data.confidence} />
        </CardContent>
      </Card>
    </div>
  );
}
