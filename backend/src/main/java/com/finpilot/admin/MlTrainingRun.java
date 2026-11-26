package com.finpilot.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ml_training_runs")
public class MlTrainingRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();
    @Column(nullable = false)
    private int corrections;
    @Column(name = "model_version", nullable = false)
    private int modelVersion;
    @Column(name = "holdout_accuracy", nullable = false)
    private double holdoutAccuracy;

    protected MlTrainingRun() {}

    public MlTrainingRun(int corrections, int modelVersion, double holdoutAccuracy) {
        this.corrections = corrections;
        this.modelVersion = modelVersion;
        this.holdoutAccuracy = holdoutAccuracy;
    }

    public int getCorrections() { return corrections; }
    public int getModelVersion() { return modelVersion; }
    public double getHoldoutAccuracy() { return holdoutAccuracy; }
}
