package com.finpilot.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Extracts a stable merchant key from a noisy bank narration ("UPI/SWIGGY/1234/Payment" -> "SWIGGY"). */
public final class MerchantNormalizer {
    private static final Pattern REFS = Pattern.compile("\\d{3,}");
    private static final Pattern SEPARATORS = Pattern.compile("[/*\\-_|:,#]+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9@ ]");
    private static final Set<String> NOISE = Set.of("UPI", "POS", "NEFT", "IMPS", "RTGS", "ACH", "D", "CR", "DR", "VIN",
            "BIL", "ONL", "CARD", "PURCHASE", "PAYMENT", "PAY", "IN", "COM", "WWW", "TXN", "REF", "INR", "TO", "FROM",
            "BANGALORE", "BENGALURU", "MUMBAI", "DELHI", "PUNE", "HYDERABAD", "CHENNAI", "KOLKATA", "GURGAON", "NOIDA");

    private MerchantNormalizer() {}

    public static String normalise(String description) {
        if (description == null || description.isBlank()) {
            return "UNKNOWN";
        }
        String text = description.toUpperCase(Locale.ROOT);
        text = REFS.matcher(text).replaceAll(" ");
        text = SEPARATORS.matcher(text).replaceAll(" ");
        text = text.replace('.', ' ');
        text = NON_ALNUM.matcher(text).replaceAll(" ");
        List<String> kept = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty() || token.length() == 1 || NOISE.contains(token) || token.contains("@")
                    || token.contains("XXXX") || token.chars().allMatch(Character::isDigit)) {
                continue;
            }
            if (!kept.contains(token)) {
                kept.add(token);
            }
            if (kept.size() == 3) {
                break;
            }
        }
        return kept.isEmpty() ? "UNKNOWN" : String.join(" ", kept);
    }
}
