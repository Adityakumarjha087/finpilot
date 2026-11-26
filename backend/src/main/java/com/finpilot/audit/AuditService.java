package com.finpilot.audit;

import com.finpilot.common.ClientIp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Append-only security/audit trail. Written in its own transaction so it survives business rollbacks. */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger("audit");
    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String email, String action, String entityType, Object entityId, String details) {
        String ip = ClientIp.current();
        String safeDetails = details == null ? null : details.length() > 1000 ? details.substring(0, 1000) : details;
        repo.save(new AuditEvent(userId, email, action, entityType, entityId == null ? null : entityId.toString(), ip,
                safeDetails));
        log.info("audit action={} user={} entity={}:{}", action, userId, entityType, entityId);
    }
}
