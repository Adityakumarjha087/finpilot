package com.finpilot.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    Page<AuditEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditEvent> findByAction(String action);
}
