package com.finpilot.importing;

import java.math.BigDecimal;

/** Parses amounts like "1,234.50", "(450.00)", "₹ 99", "-12", "12.00 DR", "5.00 CR". */
public final class Amounts {
    private static final java.util.regex.Pattern EUROPEAN =
            java.util.regex.Pattern.compile("[-+]?\\d{1,3}(\\.\\d{3})*,\\d{1,2}");

    private Amounts() {}

    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing amount");
        }
        String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
        boolean negative = false;
        if (s.endsWith("DR")) {
            negative = true;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("CR")) {
            s = s.substring(0, s.length() - 2);
        }
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1);
        }
        s = s.replaceAll("[₹$€£\\s]|INR|RS\\.?", "");
        if (EUROPEAN.matcher(s).matches()) {
            s = s.replace(".", "").replace(',', '.'); // 1.234,56 -> 1234.56
        } else {
            s = s.replace(",", ""); // 1,234.56 and Indian 1,20,000.00
        }
        if (s.startsWith("-")) {
            negative = !negative;
            s = s.substring(1);
        } else if (s.startsWith("+")) {
            s = s.substring(1);
        }
        if (!s.matches("\\d+(\\.\\d+)?")) {
            throw new IllegalArgumentException("invalid amount '" + raw + "'");
        }
        BigDecimal value = new BigDecimal(s);
        return negative ? value.negate() : value;
    }

    public static BigDecimal parseOptional(String raw) {
        return raw == null || raw.isBlank() || raw.trim().equals("-") ? BigDecimal.ZERO : parse(raw);
    }
}
