package com.finpilot.transaction;

import java.util.List;
import java.util.Set;

public final class Categories {
    public static final String UNCATEGORIZED = "Uncategorized";
    public static final List<String> ALL = List.of("Food", "Groceries", "Rent", "Travel", "Transport", "Shopping",
            "Bills", "Entertainment", "Health", "Subscriptions", "Education", "Income", "Transfers", UNCATEGORIZED);
    /** Categories that are not "spending" for budgets/forecasts. */
    public static final Set<String> NON_SPENDING = Set.of("Income", "Transfers");
    /** Categories a savings plan may suggest trimming. */
    public static final List<String> DISCRETIONARY = List.of("Food", "Shopping", "Entertainment", "Travel",
            "Subscriptions");

    private Categories() {}

    public static boolean isValid(String category) {
        return category != null && ALL.contains(category);
    }
}
