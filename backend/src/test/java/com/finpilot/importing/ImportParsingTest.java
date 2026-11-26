package com.finpilot.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finpilot.common.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ImportParsingTest {
    private final StatementParser parser = new StatementParser();

    private RawTable csv(String s) {
        return parser.parse("s.csv", new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void amountsInManyFormats() {
        assertThat(Amounts.parse("1,234.50")).isEqualByComparingTo("1234.50");
        assertThat(Amounts.parse("(450.00)")).isEqualByComparingTo("-450");
        assertThat(Amounts.parse("₹ 99")).isEqualByComparingTo("99");
        assertThat(Amounts.parse("-12")).isEqualByComparingTo("-12");
        assertThat(Amounts.parse("12.00 DR")).isEqualByComparingTo("-12");
        assertThat(Amounts.parse("5.00 CR")).isEqualByComparingTo("5");
        assertThat(Amounts.parse("+7")).isEqualByComparingTo("7");
        assertThat(Amounts.parseOptional("")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(Amounts.parseOptional("-")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThatThrownBy(() -> Amounts.parse("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Amounts.parse(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datesAndTimes() {
        assertThat(DateParsing.parse("01/09/2026", "08:46", "dd/MM/yyyy")).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 46));
        assertThat(DateParsing.parse("2026-02-03", null, null)).isEqualTo(LocalDateTime.of(2026, 2, 3, 0, 0));
        assertThat(DateParsing.parse("2026-02-03 14:05:09", null, null)).isEqualTo(LocalDateTime.of(2026, 2, 3, 14, 5, 9));
        assertThat(DateParsing.parse("03 Feb 2026", "2:30 pm", null)).isEqualTo(LocalDateTime.of(2026, 2, 3, 14, 30));
        assertThat(DateParsing.parse("03/02/2026 10:30", null, "dd/MM/yyyy")).isEqualTo(LocalDateTime.of(2026, 2, 3, 0, 0));
        assertThatThrownBy(() -> DateParsing.parse("31/02/2026", null, "dd/MM/yyyy"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateParsing.parse("", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateParsing.parse("2026-01-01", "25:99", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(DateParsing.detectFormat(List.of("13/01/2026", "02/02/2026"))).isEqualTo("dd/MM/yyyy");
        assertThat(DateParsing.detectFormat(List.of("01/13/2026"))).isEqualTo("MM/dd/yyyy");
        assertThat(DateParsing.detectFormat(List.of("garbage"))).isNull();
    }

    @Test
    void csvWithSemicolonsBomAndRaggedRows() {
        RawTable t = csv("﻿Date;Description;Amount\n2026-01-01;Coffee;-120\n\n2026-01-02;Salary\n");
        assertThat(t.headers()).containsExactly("Date", "Description", "Amount");
        assertThat(t.rows()).hasSize(2);
        assertThat(t.rows().get(1)).containsExactly("2026-01-02", "Salary", "");
        assertThat(t.indexOf("amount")).isEqualTo(2);
        assertThat(t.indexOf("nope")).isEqualTo(-1);
        assertThat(t.indexOf(null)).isEqualTo(-1);
    }

    @Test
    void rejectsBadFiles() {
        assertThatThrownBy(() -> csv("only,header\n")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("x.pdf", new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("x.xlsx", new ByteArrayInputStream("nope".getBytes())))
                .isInstanceOf(ApiException.class).hasMessageContaining("not a valid");
    }

    @Test
    void xlsxWithDatesAndNumbers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet();
            var h = sheet.createRow(0);
            h.createCell(0).setCellValue("Date");
            h.createCell(1).setCellValue("Narration");
            h.createCell(2).setCellValue("Amount");
            var r = sheet.createRow(1);
            var dateCell = r.createCell(0);
            dateCell.setCellValue(LocalDateTime.of(2026, 3, 4, 10, 15));
            var style = wb.createCellStyle();
            style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
            dateCell.setCellStyle(style);
            r.createCell(1).setCellValue("UBER TRIP");
            r.createCell(2).setCellValue(-250.5);
            wb.write(out);
        }
        RawTable t = parser.parse("s.xlsx", new ByteArrayInputStream(out.toByteArray()));
        assertThat(t.rows().getFirst()).containsExactly("2026-03-04 10:15:00", "UBER TRIP", "-250.5");
        assertThat(DateParsing.parse(t.rows().getFirst().getFirst(), null, null))
                .isEqualTo(LocalDateTime.of(2026, 3, 4, 10, 15));
    }

    @Test
    void suggestsMappingForBankLayout() {
        RawTable t = csv("Txn Date,Time,Narration,Withdrawal Amt,Deposit Amt,Closing Balance\n"
                + "01/09/2026,08:46,SALARY,,1000.00,1000\n02/09/2026,09:00,SWIGGY,200.00,,800\n");
        ColumnMapping m = MappingSuggester.suggest(t);
        assertThat(m.date()).isEqualTo("Txn Date");
        assertThat(m.time()).isEqualTo("Time");
        assertThat(m.description()).isEqualTo("Narration");
        assertThat(m.debit()).isEqualTo("Withdrawal Amt");
        assertThat(m.credit()).isEqualTo("Deposit Amt");
        assertThat(m.amount()).isNull();
        assertThat(m.dateFormat()).isEqualTo("dd/MM/yyyy");
        ColumnMapping simple = MappingSuggester.suggest(csv("Date,Description,Amount\n2026-01-01,x,-1\n"));
        assertThat(simple.amount()).isEqualTo("Amount");
        assertThat(simple.dateFormat()).isEqualTo("yyyy-MM-dd");
    }

    @Test
    void mappingValidation() {
        assertThatThrownBy(() -> new ColumnMapping("d", null, "x", null, null, null, null, false).validate())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ColumnMapping("d", null, "x", "a", "b", null, null, false).validate())
                .isInstanceOf(IllegalArgumentException.class);
        new ColumnMapping("d", null, "x", "a", null, null, null, false).validate();
    }
}
