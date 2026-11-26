package com.finpilot.importing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "import_batches")
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();
    @Column(name = "total_rows", nullable = false)
    private int totalRows;
    @Column(nullable = false)
    private int imported;
    @Column(nullable = false)
    private int duplicates;
    @Column(nullable = false)
    private int failed;

    protected ImportBatch() {}

    public ImportBatch(Long userId, String fileName, int totalRows) {
        this.userId = userId;
        this.fileName = fileName;
        this.totalRows = totalRows;
    }

    public void complete(int imported, int duplicates, int failed) {
        this.imported = imported;
        this.duplicates = duplicates;
        this.failed = failed;
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public Instant getImportedAt() { return importedAt; }
    public int getTotalRows() { return totalRows; }
    public int getImported() { return imported; }
    public int getDuplicates() { return duplicates; }
    public int getFailed() { return failed; }
}
