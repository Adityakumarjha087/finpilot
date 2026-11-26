package com.finpilot.importing;

import java.time.Instant;
import java.util.List;

public final class ImportDtos {
    private ImportDtos() {}

    public record PreviewResponse(String fileName, List<String> headers, List<List<String>> sampleRows, int totalRows,
                                  ColumnMapping suggestedMapping) {}

    public record RowError(int row, String message) {}

    public record ImportResult(long batchId, String fileName, int totalRows, int imported, int duplicates, int failed,
                               List<RowError> errors) {}

    public record ImportBatchDto(long id, String fileName, Instant importedAt, int totalRows, int imported,
                                 int duplicates, int failed) {
        static ImportBatchDto of(ImportBatch b) {
            return new ImportBatchDto(b.getId(), b.getFileName(), b.getImportedAt(), b.getTotalRows(),
                    b.getImported(), b.getDuplicates(), b.getFailed());
        }
    }

    /** A normalised statement row ready to persist (used by both file import and the demo seeder). */
    public record ParsedRow(int rowNumber, java.time.LocalDateTime postedAt, String description,
                            java.math.BigDecimal amount) {}
}
