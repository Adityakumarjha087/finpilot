import { useMemo, useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from "@tanstack/react-table";
import { ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight, NotebookPen, Search, Sparkles, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { AnomalyIndicator } from "@/components/anomaly-indicator";
import { CategoryBadge } from "@/components/category-badge";
import { Money } from "@/components/money";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState, LoadingRows } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input, Textarea } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useCategories } from "@/hooks/use-categories";
import { useDebounced } from "@/hooks/use-debounced";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/api-client";
import { formatDate, formatNumber } from "@/lib/format";
import type { Page, Transaction, TransactionQuery } from "@/lib/types";

const PAGE_SIZES = [10, 25, 50, 100];
const SORTABLE = new Set(["postedAt", "amount", "merchant", "category"]);

export function TransactionsPage() {
  const queryClient = useQueryClient();
  const categories = useCategories();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [sorting, setSorting] = useState<SortingState>([{ id: "postedAt", desc: true }]);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [flagged, setFlagged] = useState(false);
  const [notesFor, setNotesFor] = useState<Transaction | null>(null);
  const [notesDraft, setNotesDraft] = useState("");
  const [deleteFor, setDeleteFor] = useState<Transaction | null>(null);

  const q = useDebounced(search.trim(), 300);
  const sort = sorting[0] ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : "postedAt,desc";
  const query: TransactionQuery = { page, size, sort, q, category, from, to, flagged };
  const queryKey = ["transactions", query] as const;

  const { data, isPending, isFetching, error, refetch } = useQuery({
    queryKey,
    queryFn: () => api.transactions.list(query),
    placeholderData: keepPreviousData,
  });

  const patchCache = (id: number, patch: Partial<Transaction>) => {
    queryClient.setQueriesData<Page<Transaction>>({ queryKey: ["transactions"] }, (old) =>
      old ? { ...old, content: old.content.map((t) => (t.id === id ? { ...t, ...patch } : t)) } : old,
    );
  };

  const updateMutation = useMutation({
    mutationFn: ({ id, patch }: { id: number; patch: { category?: string; notes?: string | null } }) =>
      api.transactions.update(id, patch),
    onMutate: async ({ id, patch }) => {
      await queryClient.cancelQueries({ queryKey: ["transactions"] });
      const snapshot = queryClient.getQueriesData<Page<Transaction>>({ queryKey: ["transactions"] });
      const optimistic: Partial<Transaction> = { ...patch };
      if (patch.category) {
        optimistic.categorySource = "USER";
        optimistic.categoryConfidence = null;
      }
      patchCache(id, optimistic);
      return { snapshot };
    },
    onError: (err, _vars, ctx) => {
      ctx?.snapshot.forEach(([key, value]) => queryClient.setQueryData(key, value));
      toast.error(`Update failed: ${errorMessage(err)}`);
    },
    onSuccess: (updated, { patch }) => {
      patchCache(updated.id, updated);
      if (patch.category) {
        toast.success(`Category set to ${patch.category}`, {
          description: "Thanks! This correction teaches the model and creates a merchant rule.",
        });
      } else {
        toast.success("Notes saved");
      }
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.transactions.remove(id),
    onSuccess: () => {
      toast.success("Transaction deleted");
      setDeleteFor(null);
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (err) => toast.error(`Delete failed: ${errorMessage(err)}`),
  });

  const columns = useMemo<ColumnDef<Transaction>[]>(
    () => [
      {
        id: "postedAt",
        accessorKey: "postedAt",
        header: "Date",
        cell: ({ row }) => <span className="whitespace-nowrap tabular-nums">{formatDate(row.original.postedAt)}</span>,
      },
      {
        id: "merchant",
        accessorKey: "merchant",
        header: "Merchant",
        cell: ({ row }) => (
          <div className="min-w-48 max-w-80">
            <div className="flex items-center gap-1.5 font-medium">
              <span className="truncate">{row.original.merchant || row.original.description}</span>
              <AnomalyIndicator anomaly={row.original.anomaly} />
            </div>
            <div className="truncate text-xs text-muted-foreground" title={row.original.description}>
              {row.original.description}
            </div>
            {row.original.notes && <div className="mt-0.5 truncate text-xs italic text-muted-foreground">“{row.original.notes}”</div>}
          </div>
        ),
      },
      {
        id: "category",
        accessorKey: "category",
        header: "Category",
        cell: ({ row }) => {
          const t = row.original;
          const label = `Category for ${t.merchant || t.description} (teaches the model)`;
          return (
            <div className="flex min-w-44 flex-col items-start gap-1.5">
              <CategoryBadge category={t.category} source={t.categorySource} confidence={t.categoryConfidence} />
              <NativeSelect
                aria-label={label}
                title="Changing the category teaches the model"
                value={t.category}
                className="w-40"
                onChange={(e) => updateMutation.mutate({ id: t.id, patch: { category: e.target.value } })}
              >
                {(categories.includes(t.category) ? categories : [t.category, ...categories]).map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </NativeSelect>
            </div>
          );
        },
      },
      {
        id: "amount",
        accessorKey: "amount",
        header: () => <span className="block text-right">Amount</span>,
        cell: ({ row }) => <Money value={row.original.amount} className="block text-right font-medium whitespace-nowrap" />,
      },
      {
        id: "actions",
        header: () => <span className="sr-only">Actions</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Edit notes for ${row.original.merchant || row.original.description}`}
              onClick={() => {
                setNotesFor(row.original);
                setNotesDraft(row.original.notes ?? "");
              }}
            >
              <NotebookPen />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Delete ${row.original.merchant || row.original.description}`}
              onClick={() => setDeleteFor(row.original)}
            >
              <Trash2 />
            </Button>
          </div>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [categories],
  );

  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data: data?.content ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    manualSorting: true,
    enableSortingRemoval: false,
    pageCount: data?.totalPages ?? -1,
    state: { sorting, pagination: { pageIndex: page, pageSize: size } },
    onSortingChange: (updater) => {
      setSorting(updater);
      setPage(0);
    },
    getRowId: (row) => String(row.id),
  });

  const resetPage = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };

  const totalPages = data?.totalPages ?? 0;

  return (
    <div>
      <PageHeader title="Transactions" description="Search, filter and correct categories. Every correction teaches the model." />

      <Card className="mb-4">
        <CardContent className="grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(3,minmax(0,1fr))_auto]">
          <div className="space-y-1.5">
            <Label htmlFor="tx-search">Search</Label>
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
              <Input
                id="tx-search"
                type="search"
                placeholder="Description or merchant"
                className="pl-8"
                value={search}
                onChange={(e) => resetPage(setSearch)(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="tx-category">Category</Label>
            <NativeSelect id="tx-category" value={category} onChange={(e) => resetPage(setCategory)(e.target.value)}>
              <option value="">All categories</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="tx-from">From</Label>
            <Input id="tx-from" type="date" value={from} max={to || undefined} onChange={(e) => resetPage(setFrom)(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="tx-to">To</Label>
            <Input id="tx-to" type="date" value={to} min={from || undefined} onChange={(e) => resetPage(setTo)(e.target.value)} />
          </div>
          <div className="flex items-end gap-2 pb-2">
            <Switch id="tx-flagged" checked={flagged} onCheckedChange={(v) => resetPage(setFlagged)(v)} />
            <Label htmlFor="tx-flagged">Flagged only</Label>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          {isPending ? (
            <LoadingRows rows={8} className="p-4" />
          ) : error ? (
            <ErrorState error={error} onRetry={() => refetch()} className="m-4" />
          ) : data.content.length === 0 ? (
            <EmptyState
              className="m-4"
              title="No transactions found"
              description={q || category || from || to || flagged ? "Try clearing some filters." : "Import a statement to get started."}
            />
          ) : (
            <Table aria-label="Transactions" data-testid="transactions-table" aria-busy={isFetching}>
              <TableHeader>
                {table.getHeaderGroups().map((hg) => (
                  <TableRow key={hg.id} className="hover:bg-transparent">
                    {hg.headers.map((header) => {
                      const sortable = SORTABLE.has(header.column.id);
                      const dir = header.column.getIsSorted();
                      return (
                        <TableHead
                          key={header.id}
                          aria-sort={dir === "asc" ? "ascending" : dir === "desc" ? "descending" : sortable ? "none" : undefined}
                        >
                          {sortable ? (
                            <button
                              type="button"
                              className="inline-flex cursor-pointer items-center gap-1 uppercase hover:text-foreground"
                              onClick={header.column.getToggleSortingHandler()}
                            >
                              {flexRender(header.column.columnDef.header, header.getContext())}
                              {dir === "asc" ? (
                                <ArrowUp className="size-3" aria-hidden />
                              ) : dir === "desc" ? (
                                <ArrowDown className="size-3" aria-hidden />
                              ) : (
                                <ArrowUpDown className="size-3 opacity-50" aria-hidden />
                              )}
                            </button>
                          ) : (
                            flexRender(header.column.columnDef.header, header.getContext())
                          )}
                        </TableHead>
                      );
                    })}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.map((row) => (
                  <TableRow key={row.id} data-testid="transaction-row">
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
        {data && data.totalElements > 0 && (
          <div className="flex flex-col items-center justify-between gap-3 border-t p-3 text-sm sm:flex-row">
            <p className="text-muted-foreground">
              {formatNumber(data.totalElements)} transactions · page {data.page + 1} of {Math.max(totalPages, 1)}
            </p>
            <div className="flex items-center gap-2">
              <Label htmlFor="tx-size" className="text-muted-foreground">
                Rows
              </Label>
              <NativeSelect
                id="tx-size"
                className="w-20"
                value={String(size)}
                onChange={(e) => {
                  setSize(Number(e.target.value));
                  setPage(0);
                }}
              >
                {PAGE_SIZES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </NativeSelect>
              <Button variant="outline" size="icon" aria-label="Previous page" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                <ChevronLeft />
              </Button>
              <Button
                variant="outline"
                size="icon"
                aria-label="Next page"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight />
              </Button>
            </div>
          </div>
        )}
      </Card>
      <p className="mt-3 flex items-center gap-1.5 text-xs text-muted-foreground">
        <Sparkles className="size-3" aria-hidden /> Percentages next to a category are the ML model's confidence.
      </p>

      <Dialog open={!!notesFor} onOpenChange={(o) => !o && setNotesFor(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit notes</DialogTitle>
            <DialogDescription>{notesFor?.description}</DialogDescription>
          </DialogHeader>
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (!notesFor) return;
              updateMutation.mutate({ id: notesFor.id, patch: { notes: notesDraft.trim() || null } });
              setNotesFor(null);
            }}
          >
            <div className="space-y-1.5">
              <Label htmlFor="tx-notes">Notes</Label>
              <Textarea id="tx-notes" maxLength={500} value={notesDraft} onChange={(e) => setNotesDraft(e.target.value)} />
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setNotesFor(null)}>
                Cancel
              </Button>
              <Button type="submit">Save notes</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!deleteFor} onOpenChange={(o) => !o && setDeleteFor(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete transaction?</DialogTitle>
            <DialogDescription>
              {deleteFor?.description} — this cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteFor(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => deleteFor && deleteMutation.mutate(deleteFor.id)}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
