package com.finpilot.audit;

import com.finpilot.common.PageResponse;
import com.finpilot.security.AuthenticatedUser;
import com.finpilot.security.CurrentUser;
import com.finpilot.user.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit log")
@Validated
public class AuditController {
    private final AuditRepository repo;

    public AuditController(AuditRepository repo) {
        this.repo = repo;
    }

    public record AuditEventDto(long id, String action, String entityType, String entityId, String ip, String details,
                                Instant createdAt, String userEmail) {
        static AuditEventDto of(AuditEvent e) {
            return new AuditEventDto(e.getId(), e.getAction(), e.getEntityType(), e.getEntityId(), e.getIp(),
                    e.getDetails(), e.getCreatedAt(), e.getUserEmail());
        }
    }

    @GetMapping
    public PageResponse<AuditEventDto> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "25") @Min(1) @Max(200) int size) {
        AuthenticatedUser me = CurrentUser.get();
        var pageable = PageRequest.of(page, size);
        var result = me.role() == Role.ADMIN ? repo.findAllByOrderByCreatedAtDesc(pageable)
                : repo.findByUserIdOrderByCreatedAtDesc(me.id(), pageable);
        return PageResponse.of(result, AuditEventDto::of);
    }
}
