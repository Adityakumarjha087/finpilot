import { useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Check, Clock, Copy, Cpu } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { axisProps, chartColor, gridProps, tooltipStyle } from "@/lib/chart";
import { formatCell, formatNumber, type ResultCell } from "@/lib/format";
import type { AssistantResult } from "@/lib/types";

export function CopyButton({ text, label = "Copy SQL" }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Button
      variant="ghost"
      size="sm"
      aria-label={copied ? "Copied" : label}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        } catch {
          /* clipboard unavailable */
        }
      }}
    >
      {copied ? <Check /> : <Copy />} {copied ? "Copied" : "Copy"}
    </Button>
  );
}

function ResultChart({ result }: { result: AssistantResult }) {
  const { chart, columns, rows } = result;
  const xKey = chart.x && columns.includes(chart.x) ? chart.x : columns[0];
  const yKey = chart.y && columns.includes(chart.y) ? chart.y : columns.find((_, i) => typeof rows[0]?.[i] === "number") ?? columns[1];
  if (!xKey || !yKey) return null;
  const xi = columns.indexOf(xKey);
  const yi = columns.indexOf(yKey);
  const data = rows.map((r) => ({ x: String(r[xi] ?? ""), y: typeof r[yi] === "number" ? (r[yi] as number) : Number(r[yi] ?? 0) }));
  const fmt = (v: unknown) => formatCell(Number(v), yKey);

  if (chart.type === "pie") {
    const sorted = [...data].sort((a, b) => b.y - a.y);
    const slices = sorted.length > 8 ? [...sorted.slice(0, 7), { x: "Other", y: sorted.slice(7).reduce((s, d) => s + d.y, 0) }] : sorted;
    return (
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={slices} dataKey="y" nameKey="x" innerRadius="50%" outerRadius="85%" stroke="var(--card)" strokeWidth={2}>
            {slices.map((s, i) => (
              <Cell key={s.x} fill={chartColor(i)} />
            ))}
          </Pie>
          <Tooltip contentStyle={tooltipStyle} formatter={fmt} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
        </PieChart>
      </ResponsiveContainer>
    );
  }
  if (chart.type === "line") {
    return (
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data}>
          <CartesianGrid {...gridProps} />
          <XAxis dataKey="x" {...axisProps} />
          <YAxis {...axisProps} width={72} />
          <Tooltip contentStyle={tooltipStyle} formatter={fmt} />
          <Line type="monotone" dataKey="y" name={yKey} stroke={chartColor(0)} strokeWidth={2} dot={{ r: 3 }} />
        </LineChart>
      </ResponsiveContainer>
    );
  }
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data}>
        <CartesianGrid {...gridProps} />
        <XAxis dataKey="x" {...axisProps} />
        <YAxis {...axisProps} width={72} />
        <Tooltip contentStyle={tooltipStyle} formatter={fmt} cursor={{ fill: "var(--muted)", opacity: 0.5 }} />
        <Bar dataKey="y" name={yKey} fill={chartColor(0)} radius={[4, 4, 0, 0]} maxBarSize={48} />
      </BarChart>
    </ResponsiveContainer>
  );
}

export function ResultTable({ columns, rows }: { columns: string[]; rows: ResultCell[][] }) {
  return (
    <div className="max-h-80 overflow-auto rounded-lg border">
      <Table aria-label="Query result" data-testid="assistant-table">
        <TableHeader>
          <TableRow>
            {columns.map((c) => (
              <TableHead key={c} className="normal-case">
                {c}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((r, i) => (
            <TableRow key={i}>
              {columns.map((c, j) => (
                <TableCell key={c} className={typeof r[j] === "number" ? "text-right tabular-nums" : undefined}>
                  {formatCell(r[j] ?? null, c)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

export function AssistantResultView({ result }: { result: AssistantResult }) {
  const { chart, columns, rows } = result;
  const single = rows[0]?.[0] ?? null;
  const showChart = (chart.type === "bar" || chart.type === "line" || chart.type === "pie") && rows.length > 0 && columns.length >= 2;

  return (
    <Card data-testid="assistant-result">
      <CardHeader className="gap-2">
        <CardTitle className="text-base">{result.question}</CardTitle>
        <div className="flex flex-wrap gap-2 text-xs">
          <Badge variant="secondary">
            <Cpu className="size-3" aria-hidden /> {result.provider}
          </Badge>
          <Badge variant="secondary">
            <Clock className="size-3" aria-hidden /> {formatNumber(result.durationMs)} ms
          </Badge>
          <Badge variant="outline">
            {rows.length} row{rows.length === 1 ? "" : "s"}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {result.summary && <p className="text-sm leading-relaxed" data-testid="assistant-summary">{result.summary}</p>}

        {chart.type === "number" && rows.length > 0 ? (
          <div className="rounded-lg border bg-muted/30 p-6 text-center" data-testid="assistant-number">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">{columns[0]}</p>
            <p className="mt-1 text-4xl font-semibold tabular-nums">{formatCell(single, columns[0] ?? "")}</p>
          </div>
        ) : showChart ? (
          <div className="h-72" data-testid="assistant-chart" data-chart-type={chart.type}>
            <ResultChart result={result} />
          </div>
        ) : null}

        {rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">The query returned no rows.</p>
        ) : chart.type !== "number" || rows.length > 1 || columns.length > 1 ? (
          <ResultTable columns={columns} rows={rows} />
        ) : null}

        <div>
          <div className="mb-1 flex items-center justify-between">
            <h4 className="text-sm font-medium">Generated SQL</h4>
            <CopyButton text={result.sql} />
          </div>
          <pre className="overflow-x-auto rounded-lg bg-zinc-950 p-4 text-xs leading-relaxed text-zinc-100" data-testid="assistant-sql">
            <code>{result.sql}</code>
          </pre>
        </div>
      </CardContent>
    </Card>
  );
}
