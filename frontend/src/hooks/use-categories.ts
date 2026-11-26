import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";

export const DEFAULT_CATEGORIES = [
  "Food",
  "Groceries",
  "Rent",
  "Travel",
  "Transport",
  "Shopping",
  "Bills",
  "Entertainment",
  "Health",
  "Subscriptions",
  "Education",
  "Income",
  "Transfers",
  "Uncategorized",
];

export function useCategories(): string[] {
  const { data } = useQuery({ queryKey: ["categories"], queryFn: api.categories, staleTime: 60 * 60 * 1000 });
  return data && data.length ? data : DEFAULT_CATEGORIES;
}
