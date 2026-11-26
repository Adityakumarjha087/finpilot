package com.finpilot.assistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "assistant_queries")
public class AssistantQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String question;
    @Column(name = "sql_text", nullable = false)
    private String sqlText;
    @Column(name = "row_count", nullable = false)
    private int rowCount;
    @Column(nullable = false)
    private String provider;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AssistantQuery() {}

    public AssistantQuery(Long userId, String question, String sqlText, int rowCount, String provider) {
        this.userId = userId;
        this.question = question;
        this.sqlText = sqlText;
        this.rowCount = rowCount;
        this.provider = provider;
    }

    public String getQuestion() { return question; }
    public String getSqlText() { return sqlText; }
    public int getRowCount() { return rowCount; }
    public Instant getCreatedAt() { return createdAt; }
}
