package com.finpilot.transaction;

import com.finpilot.audit.AuditService;
import com.finpilot.common.ApiException;
import com.finpilot.common.PageResponse;
import com.finpilot.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private static final Set<String> SORTABLE = Set.of("postedAt", "amount", "merchant", "category");
    private final TransactionRepository repo;
    private final CategoryCorrectionRepository corrections;
    private final MerchantRuleRepository rules;
    private final AuditService audit;
    private final ZoneId zone;

    public TransactionService(TransactionRepository repo, CategoryCorrectionRepository corrections,
                              MerchantRuleRepository rules, AuditService audit, ZoneId appZone) {
        this.repo = repo;
        this.corrections = corrections;
        this.rules = rules;
        this.audit = audit;
        this.zone = appZone;
    }

    public record Filter(String category, String q, LocalDate from, LocalDate to, Boolean flagged) {}

    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> list(long userId, Filter f, int page, int size, String sort) {
        Sort order = parseSort(sort);
        Specification<BankTransaction> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("userId"), userId));
            if (f.category() != null && !f.category().isBlank()) {
                p.add(cb.equal(root.get("category"), f.category()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("description")), like, '\\'),
                        cb.like(cb.lower(root.get("merchant")), like, '\\')));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("postedAt"), f.from().atStartOfDay(zone).toInstant()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("postedAt"), f.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (Boolean.TRUE.equals(f.flagged())) {
                p.add(cb.equal(root.get("anomalyStatus"), AnomalyStatus.FLAGGED));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repo.findAll(spec, PageRequest.of(page, size, order)), TransactionDto::of);
    }

    static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "postedAt").and(Sort.by(Sort.Direction.DESC, "id"));
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw ApiException.badRequest("Unsupported sort field: " + field);
        }
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    /** Updates category and/or notes. A category change is stored as a correction and a merchant rule. */
    @Transactional
    public TransactionDto update(AuthenticatedUser user, long id, String category, String notes) {
        BankTransaction t = repo.findByIdAndUserId(id, user.id()).orElseThrow(() -> ApiException.notFound("Transaction"));
        if (category != null && !category.equals(t.getCategory())) {
            if (!Categories.isValid(category)) {
                throw ApiException.badRequest("Unknown category: " + category);
            }
            String previous = t.getCategory();
            corrections.save(new CategoryCorrection(t, previous, category));
            t.categorise(category, CategorySource.USER, 1.0);
            MerchantRule rule = rules.findByUserIdAndMerchant(user.id(), t.getMerchant())
                    .orElseGet(() -> new MerchantRule(user.id(), t.getMerchant(), category));
            rule.setCategory(category);
            rules.save(rule);
            repo.save(t);
            int propagated = repo.applyMerchantRule(user.id(), t.getMerchant(), category);
            audit.record(user.id(), user.email(), "CATEGORY_CORRECTED", "TRANSACTION", id,
                    previous + " -> " + category + " (applied to " + propagated + " similar)");
        }
        if (notes != null) {
            t.setNotes(notes.isBlank() ? null : notes);
        }
        return TransactionDto.of(repo.save(t));
    }

    @Transactional
    public void delete(AuthenticatedUser user, long id) {
        BankTransaction t = repo.findByIdAndUserId(id, user.id()).orElseThrow(() -> ApiException.notFound("Transaction"));
        repo.delete(t);
        audit.record(user.id(), user.email(), "TRANSACTION_DELETED", "TRANSACTION", id, null);
    }
}
