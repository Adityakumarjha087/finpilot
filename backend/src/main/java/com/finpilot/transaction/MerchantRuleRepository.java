package com.finpilot.transaction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRuleRepository extends JpaRepository<MerchantRule, Long> {
    List<MerchantRule> findByUserId(Long userId);

    Optional<MerchantRule> findByUserIdAndMerchant(Long userId, String merchant);
}
