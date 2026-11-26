package com.finpilot.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** A user's category fix; the labelled example that feeds the active-learning retrain loop. */
@Entity
@Table(name = "category_corrections")
public class CategoryCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "transaction_id")
    private Long transactionId;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String merchant;
    @Column(name = "previous_category", nullable = false)
    private String previousCategory;
    @Column(nullable = false)
    private String category;
    @Column(name = "used_in_training", nullable = false)
    private boolean usedInTraining;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CategoryCorrection() {}

    public CategoryCorrection(BankTransaction t, String previousCategory, String category) {
        this.userId = t.getUserId();
        this.transactionId = t.getId();
        this.description = t.getDescription();
        this.amount = t.getAmount();
        this.merchant = t.getMerchant();
        this.previousCategory = previousCategory;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getPreviousCategory() { return previousCategory; }
    public boolean isUsedInTraining() { return usedInTraining; }
    public void markUsed() { this.usedInTraining = true; }
}
