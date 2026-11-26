"""Offline evaluation of all three models on synthetic data. Writes docs/METRICS.json.

Usage: uv run python -m finpilot_ml.scripts.evaluate --out ../docs/METRICS.json
"""

from __future__ import annotations

import argparse
import json
import random
import tempfile
import time
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import numpy as np
from sklearn.metrics import accuracy_score, f1_score

from finpilot_ml.anomaly import TxnInput, detect
from finpilot_ml.categorizer import Categorizer, Correction, build_pipeline, normalise
from finpilot_ml.forecasting import forecast_series
from finpilot_ml.synthetic import MERCHANTS, labelled_corpus, user_history


def _fit_eval(
    train: list[tuple[str, float, str, str]], test: list[tuple[str, float, str, str]]
) -> dict[str, float]:
    pipe = build_pipeline()
    pipe.fit([normalise(d, a) for d, a, _, _ in train], [c for _, _, c, _ in train])
    pred = pipe.predict([normalise(d, a) for d, a, _, _ in test])
    truth = [c for _, _, c, _ in test]
    return {
        "accuracy": round(float(accuracy_score(truth, pred)), 4),
        "macro_f1": round(float(f1_score(truth, pred, average="macro")), 4),
        "n_test": len(test),
    }


def categorisation() -> dict[str, object]:
    # 1) seen merchants, unseen narrations (new random templates/refs)
    train = labelled_corpus(30, seed=1)
    test = labelled_corpus(10, seed=99)
    seen = _fit_eval(train, test)
    # 2) unseen merchants: merchant-level split (harder, honest generalisation number)
    rng = random.Random(5)
    merchants = MERCHANTS[:]
    rng.shuffle(merchants)
    cut = int(len(merchants) * 0.8)
    train_m, test_m = merchants[:cut], merchants[cut:]
    unseen_train = labelled_corpus(30, seed=2, merchants=train_m)
    unseen_test = labelled_corpus(10, seed=3, merchants=test_m)
    unseen = _fit_eval(unseen_train, unseen_test)
    # 3) active learning: one correction per unseen merchant, then re-evaluate on other narrations
    corrections = labelled_corpus(1, seed=4, merchants=test_m)
    pipe = build_pipeline()
    texts = [normalise(d, a) for d, a, _, _ in unseen_train] + [normalise(d, a) for d, a, _, _ in corrections]
    labels = [c for *_, c, _ in unseen_train] + [c for *_, c, _ in corrections]
    weights = [1.0] * len(unseen_train) + [8.0] * len(corrections)
    pipe.fit(texts, labels, clf__sample_weight=np.asarray(weights))
    after = pipe.predict([normalise(d, a) for d, a, _, _ in unseen_test])
    truth = [c for _, _, c, _ in unseen_test]
    al_acc = round(float(accuracy_score(truth, after)), 4)
    # latency of the production wrapper
    with tempfile.TemporaryDirectory() as tmp:
        cat = Categorizer(Path(tmp))
        cat.train([Correction(d, a, c) for d, a, c, _ in corrections])
        batch = [(d, a) for d, a, _, _ in test[:1000]]
        start = time.perf_counter()
        cat.predict(batch)
        per_1k_ms = round((time.perf_counter() - start) * 1000, 1)
    return {
        "model": "TF-IDF (char 2-5 + word 1-2) + LogisticRegression",
        "seen_merchants": seen,
        "unseen_merchants": unseen,
        "active_learning": {
            "corrections_added": len(corrections),
            "unseen_accuracy_before": unseen["accuracy"],
            "unseen_accuracy_after": al_acc,
        },
        "predict_latency_ms_per_1000": per_1k_ms,
    }


def forecasting(n_users: int = 20) -> dict[str, object]:
    errors: dict[str, list[float]] = defaultdict(list)
    covered = 0
    total = 0
    for seed in range(n_users):
        hist = user_history(months=18, seed=1000 + seed, anomaly_rate=0.0)
        monthly: dict[str, dict[str, float]] = defaultdict(lambda: defaultdict(float))
        for t in hist:
            if t.amount < 0:
                monthly[t.category][t.posted_at[:7]] += -t.amount
        for cat, series in monthly.items():
            periods = sorted(series)
            if len(periods) < 17:
                continue
            periods = periods[-17:-1]  # drop the (partial) current month
            values = [series[p] for p in periods]
            train_p, test_v = periods[:-3], values[-3:]
            fc = forecast_series(train_p, values[:-3], horizon=3, confidence=0.8)
            naive = float(np.mean(values[:-3][-3:]))
            for pt, actual in zip(fc.points, test_v, strict=True):
                errors["model"].append(abs(pt.mean - actual) / max(actual, 1.0))
                errors["naive_last3_mean"].append(abs(naive - actual) / max(actual, 1.0))
                covered += int(pt.lower <= actual <= pt.upper)
                total += 1
            del cat
    return {
        "model": "statsmodels ETS (additive error, damped additive trend)",
        "backtest": "18-month synthetic histories, 3-month holdout, per category",
        "mape_model": round(float(np.mean(errors["model"])), 4),
        "mape_naive_baseline": round(float(np.mean(errors["naive_last3_mean"])), 4),
        "interval_80_coverage": round(covered / max(total, 1), 4),
        "n_forecasts": total,
    }


def anomalies(n_users: int = 20) -> dict[str, object]:
    tp = fp = fn = 0
    for seed in range(n_users):
        hist = user_history(months=12, seed=3000 + seed, anomaly_rate=0.012)  # tuned on seeds 2000-2019
        txns = [
            TxnInput(str(i), datetime.fromisoformat(t.posted_at), t.amount, t.category, t.merchant)
            for i, t in enumerate(hist)
        ]
        flagged = {a.id for a in detect(txns, contamination=0.01)}
        truth = {str(i) for i, t in enumerate(hist) if t.is_anomaly}
        tp += len(flagged & truth)
        fp += len(flagged - truth)
        fn += len(truth - flagged)
    precision = tp / max(tp + fp, 1)
    recall = tp / max(tp + fn, 1)
    return {
        "model": "IsolationForest (log amount, category z, merchant freq, hour deviation) + rules",
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(2 * precision * recall / max(precision + recall, 1e-9), 4),
        "injected_anomalies": tp + fn,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()
    result = {
        "categorisation": categorisation(),
        "forecasting": forecasting(),
        "anomaly_detection": anomalies(),
    }
    text = json.dumps(result, indent=2)
    print(text)
    if args.out:
        args.out.write_text(text + "\n")


if __name__ == "__main__":
    main()
