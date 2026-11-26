package com.finpilot.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerchantNormalizerTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "UPI/SWIGGY/123456789/Payment|SWIGGY",
            "UPI-swiggy-swiggy@okhdfc-998877|SWIGGY",
            "POS 4707XXXXXX4295 SALARY ACME TECHNOLOGIES CHENNAI|SALARY ACME TECHNOLOGIES",
            "Pos 5279Xxxxxx4443 Subway Pune|SUBWAY",
            "NETFLIX.COM|NETFLIX",
            "DOMINOSPIZZA*123456|DOMINOSPIZZA",
            "ACH D- NETFLIX-778899|NETFLIX",
            "12345|UNKNOWN",
            "' '|UNKNOWN"})
    void normalises(String input, String expected) {
        assertThat(MerchantNormalizer.normalise(input)).isEqualTo(expected);
    }
}
