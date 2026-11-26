package com.finpilot.importing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Which header of the uploaded statement holds which field. Either {@code amount} or {@code debit}/{@code credit}. */
public record ColumnMapping(
        @NotBlank @Size(max = 100) String date,
        @Size(max = 100) String time,
        @NotBlank @Size(max = 100) String description,
        @Size(max = 100) String amount,
        @Size(max = 100) String debit,
        @Size(max = 100) String credit,
        @Size(max = 40) String dateFormat,
        Boolean invertSign) {

    public boolean usesSplitColumns() {
        return isSet(debit) || isSet(credit);
    }

    public void validate() {
        if (!isSet(amount) && !usesSplitColumns()) {
            throw new IllegalArgumentException("Map either an amount column or debit/credit columns");
        }
        if (isSet(amount) && usesSplitColumns()) {
            throw new IllegalArgumentException("Map an amount column OR debit/credit columns, not both");
        }
    }

    static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}
