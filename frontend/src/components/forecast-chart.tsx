import { Area, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { axisProps, chartColor, gridProps, tooltipStyle } from "@/lib/chart";
import { buildForecastSeries } from "@/lib/forecast";
import { formatMoney, formatMoneyCompact, formatMonth } from "@/lib/format";
import type { ForecastPoint, HistoryPoint } from "@/lib/types";

export function ForecastChart({ history, forecast, confidence }: { history: HistoryPoint[]; forecast: ForecastPoint[]; confidence: number }) {
  const data = buildForecastSeries(history, forecast).map((r) => ({ ...r, label: formatMonth(r.month) }));
  const fmt = (v: unknown) => (Array.isArray(v) ? `${formatMoney(Number(v[0]))} – ${formatMoney(Number(v[1]))}` : formatMoney(Number(v)));
  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={data}>
        <CartesianGrid {...gridProps} />
        <XAxis dataKey="label" {...axisProps} />
        <YAxis {...axisProps} tickFormatter={formatMoneyCompact} width={64} />
        <Tooltip contentStyle={tooltipStyle} formatter={fmt} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Area
          type="monotone"
          dataKey="band"
          name={`${Math.round(confidence * 100)} % confidence`}
          stroke="none"
          fill={chartColor(0)}
          fillOpacity={0.18}
          connectNulls={false}
          isAnimationActive={false}
        />
        <Line type="monotone" dataKey="actual" name="Actual" stroke={chartColor(0)} strokeWidth={2} dot={{ r: 3 }} connectNulls={false} />
        <Line
          type="monotone"
          dataKey="mean"
          name="Forecast"
          stroke={chartColor(0)}
          strokeWidth={2}
          strokeDasharray="6 4"
          dot={{ r: 3 }}
          connectNulls={false}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
