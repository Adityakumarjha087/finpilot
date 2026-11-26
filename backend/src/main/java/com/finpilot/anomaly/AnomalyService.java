package com.finpilot.anomaly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpilot.audit.AuditService;
import com.finpilot.common.ApiException;
import com.finpilot.importing.ImportService.TransactionsImported;
import com.finpilot.ml.MlClient;
import com.finpilot.ml.MlDtos.AnomalyOut;
import com.finpilot.ml.MlDtos.AnomalyRequest;
import com.finpilot.ml.MlDtos.AnomalyTxn;
import com.finpilot.ml.MlUnavailableException;
import com.finpilot.security.AuthenticatedUser;
import com.finpilot.transaction.AnomalyStatus;
import com.finpilot.transaction.BankTransaction;
import com.finpilot.transaction.TransactionDto;
import com.finpilot.transaction.TransactionRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class AnomalyService {
    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);
    private static final Duration LOOKBACK = Duration.ofDays(400);
    private final TransactionRepository repo;
    private final MlClient ml;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public AnomalyService(TransactionRepository repo, MlClient ml, AuditService audit, ObjectMapper json, Clock clock) {
        this.repo = repo;
        this.ml = ml;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    public record ScanResult(int scanned, int flagged) {}

    /** Re-scores the last ~13 months. Dismissed transactions stay dismissed. */
    @Transactional
    public ScanResult scan(long userId) {
        List<BankTransaction> txns = repo.findByUserIdAndPostedAtGreaterThanEqualOrderByPostedAtAsc(userId,
                clock.instant().minus(LOOKBACK));
        if (txns.isEmpty()) {
            return new ScanResult(0, 0);
        }
        List<AnomalyTxn> payload = txns.stream()
                .map(t -> new AnomalyTxn(t.getId().toString(), t.getPostedAt(), t.getAmount().doubleValue(),
                        t.getCategory(), t.getMerchant()))
                .toList();
        List<AnomalyOut> found = ml.anomalies(new AnomalyRequest(payload)).anomalies();
        Map<String, AnomalyOut> byId = found.stream().collect(Collectors.toMap(AnomalyOut::id, Function.identity()));
        int flagged = 0;
        for (BankTransaction t : txns) {
            if (t.getAnomalyStatus() == AnomalyStatus.DISMISSED) {
                continue;
            }
            AnomalyOut a = byId.get(t.getId().toString());
            if (a != null) {
                t.flag(a.score(), toJson(a.reasons()));
                flagged++;
            } else if (t.getAnomalyStatus() == AnomalyStatus.FLAGGED) {
                t.clearAnomaly();
            }
        }
        repo.saveAll(txns);
        return new ScanResult(txns.size(), flagged);
    }

    @Transactional
    public ScanResult scan(AuthenticatedUser user) {
        ScanResult r = scan(user.id());
        audit.record(user.id(), user.email(), "ANOMALY_SCAN", "TRANSACTION", null,
                "scanned=" + r.scanned() + " flagged=" + r.flagged());
        return r;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImport(TransactionsImported event) {
        try {
            ScanResult r = scan(event.userId());
            log.info("post-import anomaly scan user={} scanned={} flagged={}", event.userId(), r.scanned(), r.flagged());
        } catch (MlUnavailableException e) {
            log.warn("post-import anomaly scan skipped: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> flagged(long userId) {
        return repo.findByUserIdAndAnomalyStatusOrderByAnomalyScoreDesc(userId, AnomalyStatus.FLAGGED).stream()
                .map(TransactionDto::of).toList();
    }

    @Transactional
    public TransactionDto dismiss(AuthenticatedUser user, long transactionId) {
        BankTransaction t = repo.findByIdAndUserId(transactionId, user.id())
                .orElseThrow(() -> ApiException.notFound("Transaction"));
        t.dismissAnomaly();
        audit.record(user.id(), user.email(), "ANOMALY_DISMISSED", "TRANSACTION", transactionId, null);
        return TransactionDto.of(repo.save(t));
    }

    private String toJson(List<String> reasons) {
        try {
            return json.writeValueAsString(reasons);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
