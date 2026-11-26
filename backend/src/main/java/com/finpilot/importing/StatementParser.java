package com.finpilot.importing;

import com.finpilot.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Parses CSV (auto-detecting , ; or tab) and XLSX statements into a {@link RawTable}. */
@Component
public class StatementParser {
    static final int MAX_ROWS = 20_000;
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RawTable parse(String fileName, InputStream in) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx")) {
                return parseXlsx(in);
            }
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return parseCsv(decode(in.readAllBytes()));
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw ApiException.badRequest("Could not read the file: it is not a valid CSV/XLSX statement");
        }
        throw ApiException.badRequest("Unsupported file type; upload a .csv or .xlsx statement");
    }

    /**
     * Decodes CSV bytes: honours UTF-8/UTF-16 byte-order marks, otherwise strict UTF-8 and, if the bytes are not
     * valid UTF-8 (typical for Excel "CSV" exports on Windows), falls back to Windows-1252.
     */
    static String decode(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xFE && (bytes[1] & 0xff) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xFF && (bytes[1] & 0xff) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        }
    }

    RawTable parseCsv(String content) throws IOException {
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setTrim(true)
                .setIgnoreEmptyLines(true).get();
        List<List<String>> all = new ArrayList<>();
        try (Reader reader = new java.io.StringReader(text); CSVParser parser = format.parse(reader)) {
            for (CSVRecord rec : parser) {
                List<String> row = new ArrayList<>();
                rec.forEach(v -> row.add(v == null ? "" : v.trim()));
                if (row.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                all.add(row);
                if (all.size() > MAX_ROWS + 1) {
                    throw ApiException.badRequest("Statements are limited to " + MAX_ROWS + " rows");
                }
            }
        }
        return toTable(all);
    }

    private static char detectDelimiter(String text) {
        String first = text.lines().findFirst().orElse("");
        char best = ',';
        long bestCount = first.chars().filter(c -> c == ',').count();
        for (char c : new char[] {';', '\t', '|'}) {
            long n = first.chars().filter(ch -> ch == c).count();
            if (n > bestCount) {
                best = c;
                bestCount = n;
            }
        }
        return best;
    }

    RawTable parseXlsx(InputStream in) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            List<List<String>> all = new ArrayList<>();
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                short last = row.getLastCellNum();
                for (int c = 0; c < Math.max(last, 0); c++) {
                    Cell cell = row.getCell(c);
                    values.add(cellText(cell, fmt));
                }
                if (values.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                all.add(values);
                if (all.size() > MAX_ROWS + 1) {
                    throw ApiException.badRequest("Statements are limited to " + MAX_ROWS + " rows");
                }
            }
            return toTable(all);
        }
    }

    private static String cellText(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().format(ISO_DATE_TIME);
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return java.math.BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return fmt.formatCellValue(cell).trim();
    }

    private static RawTable toTable(List<List<String>> all) {
        if (all.size() < 2) {
            throw ApiException.badRequest("The statement needs a header row and at least one transaction");
        }
        List<String> headers = all.get(0).stream().map(String::trim).toList();
        if (headers.stream().allMatch(String::isEmpty)) {
            throw ApiException.badRequest("The first row must contain column headers");
        }
        int width = headers.size();
        List<List<String>> rows = new ArrayList<>();
        for (List<String> r : all.subList(1, all.size())) {
            List<String> padded = new ArrayList<>(r.subList(0, Math.min(r.size(), width)));
            while (padded.size() < width) {
                padded.add("");
            }
            rows.add(padded);
        }
        return new RawTable(headers, rows);
    }
}
