package com.finpilot.importing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;

/** Parses the many date/time spellings found in bank exports. */
public final class DateParsing {
    static final List<String> FORMATS = List.of("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "MM/dd/yyyy",
            "dd/MM/yy", "dd-MMM-yyyy", "dd MMM yyyy", "d/M/yyyy", "yyyy/MM/dd");
    private static final List<String> TIME_FORMATS = List.of("HH:mm:ss", "HH:mm", "H:mm", "hh:mm a", "h:mm a");

    private DateParsing() {}

    static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern.replace("yyyy", "uuuu").replace("yy", "uu"), Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    /** Picks the first format that parses every non-empty sample, or {@code null}. */
    public static String detectFormat(List<String> samples) {
        for (String f : FORMATS) {
            boolean all = true;
            int seen = 0;
            for (String s : samples) {
                if (s == null || s.isBlank()) {
                    continue;
                }
                seen++;
                if (tryDate(datePart(s), f) == null) {
                    all = false;
                    break;
                }
            }
            if (all && seen > 0) {
                return f;
            }
        }
        return null;
    }

    public static LocalDateTime parse(String dateValue, String timeValue, String pattern) {
        if (dateValue == null || dateValue.isBlank()) {
            throw new IllegalArgumentException("missing date");
        }
        String value = dateValue.trim();
        LocalTime embedded = null;
        if (value.length() >= 16 && (value.charAt(10) == ' ' || value.charAt(10) == 'T')) {
            try {
                LocalDateTime dt = LocalDateTime.parse(value.replace(' ', 'T'));
                embedded = dt.toLocalTime();
                value = value.substring(0, 10);
            } catch (DateTimeParseException ignored) {
                // not ISO date-time; fall through
            }
        }
        String datePart = datePart(value);
        LocalDate date = null;
        if (pattern != null && !pattern.isBlank()) {
            date = tryDate(datePart, pattern);
        } else {
            for (String f : FORMATS) {
                date = tryDate(datePart, f);
                if (date != null) {
                    break;
                }
            }
        }
        if (date == null) {
            throw new IllegalArgumentException("unrecognised date '" + dateValue + "'");
        }
        LocalTime time = embedded != null ? embedded : parseTime(timeValue);
        return date.atTime(time);
    }

    static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        for (String f : TIME_FORMATS) {
            try {
                return LocalTime.parse(value.trim().toUpperCase(Locale.ROOT), DateTimeFormatter.ofPattern(f, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("unrecognised time '" + value + "'");
    }

    private static final java.util.regex.Pattern TRAILING_TIME =
            java.util.regex.Pattern.compile("[ T]+\\d{1,2}:\\d{2}(:\\d{2})?(\\s*[AaPp][Mm])?$");

    /** Strips a trailing time ("01/02/2026 10:30") while keeping "01 Feb 2026" intact. */
    private static String datePart(String s) {
        return TRAILING_TIME.matcher(s.trim()).replaceFirst("");
    }

    private static LocalDate tryDate(String value, String pattern) {
        try {
            return LocalDate.parse(value, formatter(pattern));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }
}
