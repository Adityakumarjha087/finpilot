package com.finpilot.assistant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantQueryRepository extends JpaRepository<AssistantQuery, Long> {
    List<AssistantQuery> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
