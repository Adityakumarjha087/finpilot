package com.finpilot.importing;

import java.util.List;

/** A parsed spreadsheet: header row plus data rows, all as trimmed strings. */
public record RawTable(List<String> headers, List<List<String>> rows) {
    public int indexOf(String header) {
        if (header == null) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(header.trim())) {
                return i;
            }
        }
        return -1;
    }
}
