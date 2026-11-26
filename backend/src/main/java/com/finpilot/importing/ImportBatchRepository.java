package com.finpilot.importing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findTop50ByUserIdOrderByImportedAtDesc(Long userId);
}
