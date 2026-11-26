package com.finpilot.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Per-user "always categorise merchant X as Y" rule learned from corrections. */
@Entity
@Table(name = "merchant_rules")
public class MerchantRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String merchant;
    @Column(nullable = false)
    private String category;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MerchantRule() {}

    public MerchantRule(Long userId, String merchant, String category) {
        this.userId = userId;
        this.merchant = merchant;
        this.category = category;
    }

    public String getMerchant() { return merchant; }
    public String getCategory() { return category; }

    public void setCategory(String category) {
        this.category = category;
        this.updatedAt = Instant.now();
    }
}
