import { useId, useRef, useState, type DragEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { CheckCircle2, FileSpreadsheet, Loader2, RotateCcw, Upload } from "lucide-react";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState, LoadingRows } from "@/components/states";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/api-client";
import { formatDateTime, formatNumber } from "@/lib/format";
import { draftFromSuggestion, draftToMapping, validateDraft, validateImportFile, type MappingDraft } from "@/lib/mapping";
import type { ImportPreview, ImportResult } from "@/lib/types";
import { cn } from "@/lib/utils";

function ColumnSelect({
  label,
  value,
  headers,
  onChange,
  optional,
  testId,
}: {
  label: string;
  value: string;
  headers: string[];
  onChange: (v: string) => void;
  optional?: boolean;
  testId: string;
}) {
  const id = useId();
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>
        {label} {optional && <span className="font-normal text-muted-foreground">(optional)</span>}
      </Label>
      <NativeSelect id={id} value={value} onChange={(e) => onChange(e.target.value)} data-testid={testId}>
        <option value="">{optional ? "— none —" : "Select a column"}</option>
        {headers.map((h) => (
          <option key={h} value={h}>
            {h}
          </option>
        ))}
      </NativeSelect>
    </div>
  );
}

function ResultSummary({ result }: { result: ImportResult }) {
  const stats = [
    { label: "Imported", value: result.imported, tone: "text-success" },
    { label: "Duplicates", value: result.duplicates, tone: "text-muted-foreground" },
    { label: "Failed", value: result.failed, tone: result.failed ? "text-destructive" : "text-muted-foreground" },
  ];
  return (
    <Card data-testid="import-result">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <CheckCircle2 className="size-5 text-success" aria-hidden /> Import complete
        </CardTitle>
        <CardDescription>
          {result.fileName} · {formatNumber(result.totalRows)} rows processed
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <dl className="grid grid-cols-3 gap-3">
          {stats.map((s) => (
            <div key={s.label} className="rounded-lg border p-3 text-center">
              <dt className="text-xs text-muted-foreground">{s.label}</dt>
              <dd className={cn("text-2xl font-semibold tabular-nums", s.tone)} data-testid={`import-${s.label.toLowerCase()}`}>
                {formatNumber(s.value)}
              </dd>
            </div>
          ))}
        </dl>
        {result.errors.length > 0 && (
          <div>
            <h4 className="mb-2 text-sm font-medium">Errors</h4>
            <ul className="max-h-48 space-y-1 overflow-y-auto text-sm" aria-label="Import errors">
              {result.errors.map((e, i) => (
                <li key={`${e.row}-${i}`} className="rounded border border-destructive/30 bg-destructive/5 px-2 py-1">
                  Row {e.row}: {e.message}
                </li>
              ))}
            </ul>
          </div>
        )}
        <Button asChild variant="outline" size="sm">
          <Link to="/transactions">View transactions</Link>
        </Button>
      </CardContent>
    </Card>
  );
}

function ImportHistory() {
  const { data, isPending, error, refetch } = useQuery({ queryKey: ["imports"], queryFn: api.imports.history });
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Import history</CardTitle>
      </CardHeader>
      <CardContent>
        {isPending ? (
          <LoadingRows rows={3} />
        ) : error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : data.length === 0 ? (
          <EmptyState title="No imports yet" />
        ) : (
          <Table aria-label="Import history">
            <TableHeader>
              <TableRow>
                <TableHead>File</TableHead>
                <TableHead>Imported at</TableHead>
                <TableHead className="text-right">Rows</TableHead>
                <TableHead className="text-right">Imported</TableHead>
                <TableHead className="text-right">Duplicates</TableHead>
                <TableHead className="text-right">Failed</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((b) => (
                <TableRow key={b.id}>
                  <TableCell className="font-medium">{b.fileName}</TableCell>
                  <TableCell className="whitespace-nowrap">{formatDateTime(b.importedAt)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatNumber(b.totalRows)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatNumber(b.imported)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatNumber(b.duplicates)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatNumber(b.failed)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

export function ImportPage() {
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [draft, setDraft] = useState<MappingDraft | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const previewMutation = useMutation({
    mutationFn: (f: File) => api.imports.preview(f),
    onSuccess: (p) => {
      setPreview(p);
      setDraft(draftFromSuggestion(p.suggestedMapping, p.headers));
    },
  });

  const importMutation = useMutation({
    mutationFn: ({ f, d }: { f: File; d: MappingDraft }) => api.imports.run(f, draftToMapping(d)),
    onSuccess: (r) => {
      setResult(r);
      toast.success(`Imported ${r.imported} transactions`);
      for (const key of ["imports", "transactions", "dashboard", "budgets", "forecast", "anomalies", "recurring"]) {
        queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
  });

  const pick = (f: File | undefined | null) => {
    if (!f) return;
    const problem = validateImportFile(f);
    setResult(null);
    setPreview(null);
    setDraft(null);
    setSubmitted(false);
    importMutation.reset();
    if (problem) {
      setFile(null);
      setFileError(problem);
      return;
    }
    setFileError(null);
    setFile(f);
    previewMutation.mutate(f);
  };

  const reset = () => {
    setFile(null);
    setPreview(null);
    setDraft(null);
    setResult(null);
    setSubmitted(false);
    setFileError(null);
    previewMutation.reset();
    importMutation.reset();
    if (inputRef.current) inputRef.current.value = "";
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    pick(e.dataTransfer.files?.[0]);
  };

  const update = (patch: Partial<MappingDraft>) => setDraft((d) => (d ? { ...d, ...patch } : d));
  const problems = draft ? validateDraft(draft) : [];

  return (
    <div className="space-y-4">
      <PageHeader title="Import" description="Upload a bank or card statement (.csv or .xlsx, up to 5 MB). Re-importing a file is safe — duplicates are skipped." />

      {!preview && !result && (
        <Card>
          <CardContent className="p-5">
            <label
              htmlFor="import-file"
              onDragOver={(e) => {
                e.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={onDrop}
              data-testid="drop-zone"
              className={cn(
                "flex cursor-pointer flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed p-10 text-center transition-colors hover:border-primary/60 hover:bg-primary/5",
                dragging && "border-primary bg-primary/10",
              )}
            >
              {previewMutation.isPending ? (
                <Loader2 className="size-8 animate-spin text-primary" aria-hidden />
              ) : (
                <Upload className="size-8 text-muted-foreground" aria-hidden />
              )}
              <span className="font-medium">{previewMutation.isPending ? `Reading ${file?.name}…` : "Drop a statement here or click to browse"}</span>
              <span className="text-xs text-muted-foreground">CSV or XLSX, max 5 MB</span>
              <input
                ref={inputRef}
                id="import-file"
                type="file"
                accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                className="sr-only"
                aria-label="Statement file"
                data-testid="import-file-input"
                onChange={(e) => pick(e.target.files?.[0])}
              />
            </label>
            {fileError && (
              <Alert variant="destructive" className="mt-3">
                {fileError}
              </Alert>
            )}
            {previewMutation.error && (
              <Alert variant="destructive" className="mt-3">
                Could not read the file: {errorMessage(previewMutation.error)}
              </Alert>
            )}
          </CardContent>
        </Card>
      )}

      {preview && draft && !result && (
        <Card data-testid="mapping-form">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <FileSpreadsheet className="size-5 text-primary" aria-hidden /> Map columns — {preview.fileName}
            </CardTitle>
            <CardDescription>
              {formatNumber(preview.totalRows)} rows detected. We pre-selected the columns we recognised; check them before importing.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form
              aria-label="Column mapping"
              className="space-y-5"
              onSubmit={(e) => {
                e.preventDefault();
                setSubmitted(true);
                if (problems.length || !file) return;
                importMutation.mutate({ f: file, d: draft });
              }}
            >
              <div className="grid gap-4 sm:grid-cols-3">
                <ColumnSelect testId="map-date" label="Date" value={draft.date} headers={preview.headers} onChange={(v) => update({ date: v })} />
                <ColumnSelect testId="map-time" label="Time" optional value={draft.time} headers={preview.headers} onChange={(v) => update({ time: v })} />
                <ColumnSelect
                  testId="map-description"
                  label="Description"
                  value={draft.description}
                  headers={preview.headers}
                  onChange={(v) => update({ description: v })}
                />
              </div>

              <fieldset className="space-y-3 rounded-lg border p-4">
                <legend className="px-1 text-sm font-medium">Amount</legend>
                <div className="flex flex-wrap gap-4 text-sm">
                  <label className="flex items-center gap-2">
                    <input type="radio" name="amount-mode" checked={draft.mode === "single"} onChange={() => update({ mode: "single" })} />
                    Single signed amount column
                  </label>
                  <label className="flex items-center gap-2">
                    <input type="radio" name="amount-mode" checked={draft.mode === "split"} onChange={() => update({ mode: "split" })} />
                    Separate debit / credit columns
                  </label>
                </div>
                {draft.mode === "single" ? (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <ColumnSelect testId="map-amount" label="Amount" value={draft.amount} headers={preview.headers} onChange={(v) => update({ amount: v })} />
                    <label className="flex items-center gap-2 self-end pb-2 text-sm">
                      <input type="checkbox" checked={draft.invertSign} onChange={(e) => update({ invertSign: e.target.checked })} />
                      Invert sign (expenses are exported as positive numbers)
                    </label>
                  </div>
                ) : (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <ColumnSelect
                      testId="map-debit"
                      label="Debit / withdrawal"
                      value={draft.debit}
                      headers={preview.headers}
                      onChange={(v) => update({ debit: v })}
                    />
                    <ColumnSelect
                      testId="map-credit"
                      label="Credit / deposit"
                      value={draft.credit}
                      headers={preview.headers}
                      onChange={(v) => update({ credit: v })}
                    />
                  </div>
                )}
              </fieldset>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="date-format">Date format</Label>
                  <Input
                    id="date-format"
                    placeholder="dd/MM/yyyy"
                    disabled={draft.autoDate}
                    value={draft.autoDate ? "" : draft.dateFormat}
                    onChange={(e) => update({ dateFormat: e.target.value })}
                    data-testid="map-date-format"
                  />
                  <label className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={draft.autoDate} onChange={(e) => update({ autoDate: e.target.checked })} />
                    Auto-detect date format
                  </label>
                  <p className="text-xs text-muted-foreground">Java pattern, e.g. dd/MM/yyyy, yyyy-MM-dd, dd-MMM-yy.</p>
                </div>
              </div>

              {submitted && problems.length > 0 && (
                <Alert variant="destructive">
                  <ul className="list-disc pl-4">
                    {problems.map((p) => (
                      <li key={p}>{p}</li>
                    ))}
                  </ul>
                </Alert>
              )}
              {importMutation.error && <Alert variant="destructive">Import failed: {errorMessage(importMutation.error)}</Alert>}

              <div>
                <h4 className="mb-2 text-sm font-medium">Sample rows</h4>
                <div className="rounded-lg border">
                  <Table aria-label="Sample rows">
                    <TableHeader>
                      <TableRow>
                        {preview.headers.map((h) => (
                          <TableHead key={h} className="normal-case">
                            {h}
                          </TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {preview.sampleRows.map((row, i) => (
                        <TableRow key={i}>
                          {preview.headers.map((h, j) => (
                            <TableCell key={h} className="whitespace-nowrap text-xs">
                              {row[j] ?? ""}
                            </TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </div>

              <div className="flex flex-wrap gap-2">
                <Button type="submit" disabled={importMutation.isPending} data-testid="import-submit">
                  {importMutation.isPending ? <Loader2 className="animate-spin" /> : <Upload />} Import {formatNumber(preview.totalRows)} rows
                </Button>
                <Button variant="outline" onClick={reset}>
                  <RotateCcw /> Choose another file
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {result && (
        <>
          <ResultSummary result={result} />
          <Button variant="outline" onClick={reset}>
            <Upload /> Import another file
          </Button>
        </>
      )}

      <ImportHistory />
    </div>
  );
}
