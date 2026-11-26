package com.finpilot.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "goals")
public class Goal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String name;
    @Column(name = "target_amount", nullable = false)
    private BigDecimal targetAmount;
    @Column(name = "saved_amount", nullable = false)
    private BigDecimal savedAmount;
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Goal() {}

    public Goal(Long userId, String name, BigDecimal targetAmount, BigDecimal savedAmount, LocalDate targetDate) {
        this.userId = userId;
        update(name, targetAmount, savedAmount, targetDate);
    }

    public final void update(String name, BigDecimal targetAmount, BigDecimal savedAmount, LocalDate targetDate) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
        this.targetDate = targetDate;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public BigDecimal getSavedAmount() { return savedAmount; }
    public LocalDate getTargetDate() { return targetDate; }
}
