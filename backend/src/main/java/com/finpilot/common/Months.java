package com.finpilot.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Small helpers around {@link YearMonth} in the application time zone. */
public final class Months {
    private Months() {}

    public static YearMonth parseOrCurrent(String value, Clock clock, ZoneId zone) {
        if (value == null || value.isBlank()) {
            return YearMonth.now(clock.withZone(zone));
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("month must be formatted as YYYY-MM");
        }
    }

    public static LocalDate today(Clock clock, ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }
}
