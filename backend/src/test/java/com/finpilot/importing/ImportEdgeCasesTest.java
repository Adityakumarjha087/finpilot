package com.finpilot.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finpilot.common.ApiException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** Statement-parsing edge cases: encodings, BOMs, delimiters, number formats, empty and oversized files. */
class ImportEdgeCasesTest {
    private final StatementParser parser = new StatementParser();

    private RawTable parse(byte[] bytes) {
        return parser.parse("s.csv", new ByteArrayInputStream(bytes));
    }

    @Test
    void utf8BomIsStripped() {
        RawTable t = parse(("﻿Date,Narration,Amount\n2026-01-01,Café Mocha,-120\n").getBytes(StandardCharsets.UTF_8));
        assertThat(t.headers().getFirst()).isEqualTo("Date");
        assertThat(t.rows().getFirst().get(1)).isEqualTo("Café Mocha");
    }

    @Test
    void utf16LittleAndBigEndianWithBom() {
        String csv = "Date,Narration,Amount\n2026-01-01,Crème brûlée ₹,-120\n";
        byte[] le = concat(new byte[] {(byte) 0xFF, (byte) 0xFE}, csv.getBytes(StandardCharsets.UTF_16LE));
        byte[] be = concat(new byte[] {(byte) 0xFE, (byte) 0xFF}, csv.getBytes(StandardCharsets.UTF_16BE));
        assertThat(parse(le).rows().getFirst().get(1)).isEqualTo("Crème brûlée ₹");
        assertThat(parse(be).rows().getFirst().get(1)).isEqualTo("Crème brûlée ₹");
    }

    @Test
    void windows1252FallbackForNonUtf8Bytes() {
        byte[] cp1252 = "Date,Narration,Amount\n2026-01-01,Café — Zürich,-120\n".getBytes(Charset.forName("windows-1252"));
        assertThat(parse(cp1252).rows().getFirst().get(1)).isEqualTo("Café — Zürich");
    }

    @Test
    void tabPipeAndSemicolonDelimitersWithQuotedFields() {
        assertThat(parse("Date\tNarration\tAmount\n2026-01-01\tA\t-1\n".getBytes()).rows().getFirst()).containsExactly("2026-01-01", "A", "-1");
        assertThat(parse("Date|Narration|Amount\n2026-01-01|B|-2\n".getBytes()).rows().getFirst()).containsExactly("2026-01-01", "B", "-2");
        RawTable semi = parse("Date;Narration;Amount\n01.02.2026;\"Shop; Inc\";\"-1.234,56\"\n".getBytes());
        assertThat(semi.rows().getFirst()).containsExactly("01.02.2026", "Shop; Inc", "-1.234,56");
        assertThat(Amounts.parse(semi.rows().getFirst().get(2))).isEqualByComparingTo("-1234.56");
        assertThat(DateParsing.parse("01.02.2026", null, null).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 2, 1));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1,234.50|1234.50", "1,20,000.00|120000.00", "1.234,56|1234.56", "12,5|12.5", "1,500|1500",
            "-0.01|-0.01", "(1,000.00)|-1000.00", "INR 2,500|2500", "Rs. 99.90|99.90", "€ 10,00|10.00",
            "0.10|0.10", "1000000000.99|1000000000.99"})
    void amountsAreExactDecimals(String raw, String expected) {
        BigDecimal v = Amounts.parse(raw);
        assertThat(v).isEqualByComparingTo(expected);
        assertThat(v.scale()).isLessThanOrEqualTo(2);
    }

    @Test
    void decimalMoneyHasNoFloatingPointDrift() {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < 10; i++) {
            sum = sum.add(Amounts.parse("0.10"));
        }
        assertThat(sum).isEqualByComparingTo("1.00"); // 0.1 * 10 in double is 0.9999999999999999
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-03-04|yyyy-MM-dd", "04/03/2026|dd/MM/yyyy", "04-03-2026|dd-MM-yyyy", "04.03.2026|dd.MM.yyyy",
            "03/14/2026|MM/dd/yyyy", "04/03/26|dd/MM/yy", "04-Mar-2026|dd-MMM-yyyy", "04 Mar 2026|dd MMM yyyy",
            "2026/03/04|yyyy/MM/dd"})
    void dateFormats(String value, String format) {
        assertThat(DateParsing.parse(value, null, format).getYear()).isEqualTo(2026);
    }

    @Test
    void emptyHeaderOnlyAndBlankFilesAreRejected() {
        assertThatThrownBy(() -> parse(new byte[0])).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse("\n\n  \n".getBytes())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse("Date,Narration,Amount\n".getBytes())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse(",,\n1,2,3\n".getBytes())).isInstanceOf(ApiException.class)
                .hasMessageContaining("headers");
    }

    @Test
    void hugeFilesAreRejectedAboveTheRowLimit() {
        StringBuilder sb = new StringBuilder("Date,Narration,Amount\n");
        for (int i = 0; i <= StatementParser.MAX_ROWS + 1; i++) {
            sb.append("2026-01-01,X,-1\n");
        }
        assertThatThrownBy(() -> parse(sb.toString().getBytes())).isInstanceOf(ApiException.class)
                .hasMessageContaining("limited");
        StringBuilder ok = new StringBuilder("Date,Narration,Amount\n");
        for (int i = 0; i < StatementParser.MAX_ROWS; i++) {
            ok.append("2026-01-01,X,-1\n");
        }
        assertThat(parse(ok.toString().getBytes()).rows()).hasSize(StatementParser.MAX_ROWS);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
