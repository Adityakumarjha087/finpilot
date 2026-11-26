package com.finpilot.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryCorrectionRepository extends JpaRepository<CategoryCorrection, Long> {
    long countByUsedInTrainingFalse();

    List<CategoryCorrection> findByCategoryNot(String category);
}
