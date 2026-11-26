package com.finpilot.transaction;

import com.finpilot.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class BankTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "import_batch_id")
    private Long importBatchId;
    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private String merchant;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String category;
    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false)
    private CategorySource categorySource;
    @Column(name = "category_confidence")
    private Double categoryConfidence;
    /** Free-text user notes may contain personal data: AES-GCM encrypted at rest. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "notes_enc")
    private String notes;
    @Column(name = "dedup_hash", nullable = false)
    private String dedupHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_status", nullable = false)
    private AnomalyStatus anomalyStatus = AnomalyStatus.NONE;
    @Column(name = "anomaly_score")
    private Double anomalyScore;
    @Column(name = "anomaly_reasons")
    private String anomalyReasons;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BankTransaction() {}

    public BankTransaction(Long userId, Long importBatchId, Instant postedAt, String description, String merchant,
                           BigDecimal amount, String dedupHash) {
        this.userId = userId;
        this.importBatchId = importBatchId;
        this.postedAt = postedAt;
        this.description = description;
        this.merchant = merchant;
        this.amount = amount;
        this.dedupHash = dedupHash;
        this.category = Categories.UNCATEGORIZED;
        this.categorySource = CategorySource.NONE;
    }

    public void categorise(String category, CategorySource source, Double confidence) {
        this.category = category;
        this.categorySource = source;
        this.categoryConfidence = confidence;
    }

    public void flag(double score, String reasonsJson) {
        this.anomalyStatus = AnomalyStatus.FLAGGED;
        this.anomalyScore = score;
        this.anomalyReasons = reasonsJson;
    }

    public void clearAnomaly() {
        this.anomalyStatus = AnomalyStatus.NONE;
        this.anomalyScore = null;
        this.anomalyReasons = null;
    }

    public void dismissAnomaly() {
        this.anomalyStatus = AnomalyStatus.DISMISSED;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getImportBatchId() { return importBatchId; }
    public Instant getPostedAt() { return postedAt; }
    public String getDescription() { return description; }
    public String getMerchant() { return merchant; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
    public CategorySource getCategorySource() { return categorySource; }
    public Double getCategoryConfidence() { return categoryConfidence; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getDedupHash() { return dedupHash; }
    public AnomalyStatus getAnomalyStatus() { return anomalyStatus; }
    public Double getAnomalyScore() { return anomalyScore; }
    public String getAnomalyReasons() { return anomalyReasons; }
}
