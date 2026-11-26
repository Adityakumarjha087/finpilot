package com.finpilot.budget;

import java.math.BigDecimal;

public enum AlertLevel {
    OK, WARNING, EXCEEDED;

    static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.80");

    public static AlertLevel of(BigDecimal ratio) {
        if (ratio.compareTo(BigDecimal.ONE) >= 0) {
            return EXCEEDED;
        }
        return ratio.compareTo(WARNING_THRESHOLD) >= 0 ? WARNING : OK;
    }
}
