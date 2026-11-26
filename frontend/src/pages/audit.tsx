import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { MlModelCard } from "@/components/ml-model-card";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState, LoadingRows } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDateTime, formatNumber } from "@/lib/format";

const SIZE = 25;

export function AuditPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";
  const [page, setPage] = useState(0);
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["audit", page],
    queryFn: () => api.audit(page, SIZE),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-4">
      <PageHeader title="Audit log" description={isAdmin ? "Security-relevant activity across all users" : "Security-relevant activity on your account"} />
      {isAdmin && <MlModelCard />}
      <Card>
        <CardContent className="p-0">
          {isPending ? (
            <LoadingRows rows={8} className="p-4" />
          ) : error ? (
            <ErrorState error={error} onRetry={() => refetch()} className="m-4" />
          ) : data.content.length === 0 ? (
            <EmptyState className="m-4" title="No activity recorded" />
          ) : (
            <Table aria-label="Audit log">
              <TableHeader>
                <TableRow>
                  <TableHead>When</TableHead>
                  <TableHead>Action</TableHead>
                  {isAdmin && <TableHead>User</TableHead>}
                  <TableHead>Entity</TableHead>
                  <TableHead>IP</TableHead>
                  <TableHead>Details</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell className="whitespace-nowrap">{formatDateTime(e.createdAt)}</TableCell>
                    <TableCell>
                      <Badge variant="secondary" className="font-mono">
                        {e.action}
                      </Badge>
                    </TableCell>
                    {isAdmin && <TableCell>{e.userEmail}</TableCell>}
                    <TableCell className="whitespace-nowrap">
                      {e.entityType}
                      {e.entityId != null && <span className="text-muted-foreground"> #{e.entityId}</span>}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{e.ip ?? "—"}</TableCell>
                    <TableCell className="max-w-80 truncate text-xs text-muted-foreground" title={e.details ?? undefined}>
                      {e.details ?? "—"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
        {data && data.totalElements > 0 && (
          <div className="flex items-center justify-between border-t p-3 text-sm">
            <span className="text-muted-foreground">
              {formatNumber(data.totalElements)} entries · page {data.page + 1} of {Math.max(1, data.totalPages)}
            </span>
            <div className="flex gap-2">
              <Button variant="outline" size="icon" aria-label="Previous page" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                <ChevronLeft />
              </Button>
              <Button
                variant="outline"
                size="icon"
                aria-label="Next page"
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight />
              </Button>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}

export function AdminMlPage() {
  return (
    <div className="space-y-4">
      <PageHeader title="ML model" description="Status and retraining of the transaction categoriser" />
      <MlModelCard />
    </div>
  );
}
