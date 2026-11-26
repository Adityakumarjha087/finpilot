package com.finpilot.importing;

import java.util.List;
import java.util.Locale;

/** Guesses a column mapping from header names so most bank exports import with one click. */
public final class MappingSuggester {
    private MappingSuggester() {}

    public static ColumnMapping suggest(RawTable table) {
        List<String> h = table.headers();
        String date = find(h, "txn date", "transaction date", "value date", "posting date", "posted", "date");
        String time = find(h, "time");
        String description = find(h, "narration", "description", "details", "particulars", "remarks", "memo",
                "merchant", "payee");
        String debit = find(h, "withdrawal", "debit", "paid out", "dr");
        String credit = find(h, "deposit", "credit", "paid in", "cr");
        String amount = (debit == null && credit == null) ? find(h, "amount", "value", "amt") : null;
        String dateFormat = null;
        if (date != null) {
            int idx = table.indexOf(date);
            List<String> samples = table.rows().stream().limit(25).map(r -> r.get(idx)).toList();
            dateFormat = DateParsing.detectFormat(samples);
        }
        return new ColumnMapping(date, time, description, amount, debit, credit, dateFormat, false);
    }

    private static String find(List<String> headers, String... candidates) {
        for (String c : candidates) {
            for (String header : headers) {
                String norm = header.toLowerCase(Locale.ROOT).trim();
                if (norm.equals(c) || norm.startsWith(c + " ") || norm.contains(c) && c.length() > 3) {
                    return header;
                }
            }
        }
        return null;
    }
}
