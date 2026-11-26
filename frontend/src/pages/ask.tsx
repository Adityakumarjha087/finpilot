import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { History, Loader2, Send } from "lucide-react";
import { AssistantResultView } from "@/components/assistant-result";
import { PageHeader } from "@/components/page-header";
import { EmptyState, ErrorState, LoadingRows } from "@/components/states";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { api } from "@/lib/api";
import { errorMessage } from "@/lib/api-client";
import { formatDateTime } from "@/lib/format";

const MAX_LEN = 300;

export function AskPage() {
  const queryClient = useQueryClient();
  const [question, setQuestion] = useState("");
  const examples = useQuery({ queryKey: ["assistant", "examples"], queryFn: api.assistant.examples, staleTime: Infinity });
  const history = useQuery({ queryKey: ["assistant", "history"], queryFn: api.assistant.history });

  const ask = useMutation({
    mutationFn: (q: string) => api.assistant.query(q),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["assistant", "history"] }),
  });

  const submit = (q: string) => {
    const trimmed = q.trim();
    if (!trimmed || trimmed.length > MAX_LEN) return;
    setQuestion(trimmed);
    ask.mutate(trimmed);
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    submit(question);
  };

  return (
    <div className="space-y-4">
      <PageHeader title="Ask" description="Ask questions about your money in plain English. FinPilot writes safe, read-only SQL for you." />
      <Card>
        <CardContent className="space-y-3 p-4">
          <form onSubmit={onSubmit} className="flex flex-col gap-2 sm:flex-row" aria-label="Ask a question">
            <Label htmlFor="ask-question" className="sr-only">
              Question
            </Label>
            <Input
              id="ask-question"
              data-testid="ask-input"
              placeholder="e.g. What were my top 5 merchants last month?"
              maxLength={MAX_LEN}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              className="h-10"
            />
            <Button type="submit" className="h-10" disabled={ask.isPending || !question.trim()} data-testid="ask-submit">
              {ask.isPending ? <Loader2 className="animate-spin" /> : <Send />} Ask
            </Button>
          </form>
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>{question.length}/{MAX_LEN}</span>
          </div>
          {examples.data && examples.data.length > 0 && (
            <div className="flex flex-wrap gap-2" aria-label="Example questions" role="group">
              {examples.data.map((ex) => (
                <button
                  key={ex}
                  type="button"
                  onClick={() => submit(ex)}
                  className="cursor-pointer rounded-full border bg-secondary/60 px-3 py-1 text-xs transition-colors hover:border-primary/50 hover:bg-primary/10"
                >
                  {ex}
                </button>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {ask.isPending && (
        <div className="space-y-3" role="status" aria-label="Thinking">
          <Skeleton className="h-8 w-2/3" />
          <Skeleton className="h-64" />
        </div>
      )}
      {ask.error && !ask.isPending && <Alert variant="destructive">{errorMessage(ask.error)}</Alert>}
      {ask.data && !ask.isPending && <AssistantResultView result={ask.data} />}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <History className="size-4" aria-hidden /> Recent questions
          </CardTitle>
        </CardHeader>
        <CardContent>
          {history.isPending ? (
            <LoadingRows rows={3} />
          ) : history.error ? (
            <ErrorState error={history.error} onRetry={() => history.refetch()} />
          ) : history.data.length === 0 ? (
            <EmptyState title="No questions yet" description="Try one of the examples above." />
          ) : (
            <ul className="divide-y" aria-label="Question history">
              {history.data.map((h, i) => (
                <li key={`${h.createdAt}-${i}`} className="flex flex-col gap-1 py-2 sm:flex-row sm:items-center sm:justify-between">
                  <button type="button" className="cursor-pointer text-left text-sm font-medium hover:text-primary" onClick={() => submit(h.question)}>
                    {h.question}
                  </button>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {h.rowCount} rows · {formatDateTime(h.createdAt)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
