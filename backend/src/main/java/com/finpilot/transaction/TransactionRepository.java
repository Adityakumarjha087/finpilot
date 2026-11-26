package com.finpilot.transaction;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<BankTransaction, Long>,
        JpaSpecificationExecutor<BankTransaction> {

    Optional<BankTransaction> findByIdAndUserId(Long id, Long userId);

    @Query("select t.dedupHash from BankTransaction t where t.userId = :userId and t.dedupHash in :hashes")
    Set<String> findExistingHashes(@Param("userId") Long userId, @Param("hashes") Collection<String> hashes);

    List<BankTransaction> findByUserIdAndPostedAtGreaterThanEqualOrderByPostedAtAsc(Long userId, Instant from);

    List<BankTransaction> findByUserIdAndAnomalyStatusOrderByAnomalyScoreDesc(Long userId, AnomalyStatus status);

    long countByUserIdAndAnomalyStatus(Long userId, AnomalyStatus status);

    long countByUserId(Long userId);

    @Modifying
    @Query("""
            update BankTransaction t set t.category = :category, t.categorySource = com.finpilot.transaction.CategorySource.RULE,
                   t.categoryConfidence = 1.0
            where t.userId = :userId and t.merchant = :merchant and t.categorySource <> com.finpilot.transaction.CategorySource.USER
            """)
    int applyMerchantRule(@Param("userId") Long userId, @Param("merchant") String merchant,
                          @Param("category") String category);
}
