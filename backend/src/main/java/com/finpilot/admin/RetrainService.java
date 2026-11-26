package com.finpilot.admin;

import com.finpilot.audit.AuditService;
import com.finpilot.config.AppProperties;
import com.finpilot.ml.MlClient;
import com.finpilot.ml.MlDtos.Correction;
import com.finpilot.ml.MlDtos.ModelInfo;
import com.finpilot.ml.MlDtos.TrainRequest;
import com.finpilot.ml.MlUnavailableException;
import com.finpilot.transaction.Categories;
import com.finpilot.transaction.CategoryCorrection;
import com.finpilot.transaction.CategoryCorrectionRepository;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active-learning loop: user corrections accumulate in {@code category_corrections}; a scheduled job retrains
 * the categoriser (synthetic base corpus + all corrections, up-weighted) once enough new ones are pending.
 */
@Service
public class RetrainService {
    private static final Logger log = LoggerFactory.getLogger(RetrainService.class);
    private final CategoryCorrectionRepository corrections;
    private final MlTrainingRunRepository runs;
    private final MlClient ml;
    private final AuditService audit;
    private final AppProperties props;
    private final ReentrantLock lock = new ReentrantLock();

    public RetrainService(CategoryCorrectionRepository corrections, MlTrainingRunRepository runs, MlClient ml,
                          AuditService audit, AppProperties props) {
        this.corrections = corrections;
        this.runs = runs;
        this.ml = ml;
        this.audit = audit;
        this.props = props;
    }

    public record RetrainResult(int modelVersion, int corrections, double holdoutAccuracy) {}

    public record Status(Integer modelVersion, String trainedAt, Double holdoutAccuracy, Integer nSamples,
                         Integer nCorrections, long pendingCorrections) {}

    @Scheduled(cron = "${finpilot.retrain.cron}")
    public void scheduledRetrain() {
        long pending = corrections.countByUsedInTrainingFalse();
        if (pending < props.retrain().minCorrections()) {
            return;
        }
        try {
            RetrainResult r = retrain(null, null);
            log.info("scheduled retrain complete version={} corrections={}", r.modelVersion(), r.corrections());
        } catch (MlUnavailableException e) {
            log.warn("scheduled retrain skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public RetrainResult retrain(Long actorId, String actorEmail) {
        lock.lock();
        try {
            List<CategoryCorrection> all = corrections.findByCategoryNot(Categories.UNCATEGORIZED);
            List<Correction> payload = all.stream()
                    .map(c -> new Correction(c.getDescription(), c.getAmount().doubleValue(), c.getCategory()))
                    .toList();
            ModelInfo info = ml.train(new TrainRequest(payload));
            all.forEach(CategoryCorrection::markUsed);
            corrections.saveAll(all);
            runs.save(new MlTrainingRun(payload.size(), info.version(), info.holdoutAccuracy()));
            audit.record(actorId, actorEmail, "ML_RETRAIN", "MODEL", info.version(),
                    "corrections=" + payload.size() + " acc=" + info.holdoutAccuracy());
            return new RetrainResult(info.version(), payload.size(), info.holdoutAccuracy());
        } finally {
            lock.unlock();
        }
    }

    public Status status() {
        long pending = corrections.countByUsedInTrainingFalse();
        try {
            ModelInfo m = ml.modelInfo();
            return new Status(m.version(), m.trainedAt(), m.holdoutAccuracy(), m.nSamples(), m.nCorrections(), pending);
        } catch (MlUnavailableException e) {
            return new Status(null, null, null, null, null, pending);
        }
    }
}
