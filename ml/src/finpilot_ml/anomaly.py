"""Isolation-Forest anomaly detection with human-readable explanations."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest

MIN_HISTORY = 20


@dataclass(frozen=True)
class TxnInput:
    id: str
    posted_at: datetime
    amount: float
    category: str
    merchant: str


@dataclass
class AnomalyResult:
    id: str
    score: float
    reasons: list[str]


def _fmt(amount: float) -> str:
    return f"₹{amount:,.0f}"


def _circular_hour_distance(hours: np.ndarray, centre: float) -> np.ndarray:
    d = np.abs(hours - centre)
    return np.asarray(np.minimum(d, 24 - d), dtype=float)


def detect(txns: list[TxnInput], contamination: float = 0.01, random_state: int = 0) -> list[AnomalyResult]:
    """Score expense transactions and return the flagged ones with explanations.

    Features: log amount, robust z-score of the amount within its category,
    merchant frequency and distance of the time-of-day from the user's typical hour.
    A transaction is reported only when the forest isolates it *and* at least one
    interpretable rule explains why, so every flag shown to a user is explainable.
    """
    expenses = [t for t in txns if t.amount < 0]
    if len(expenses) < MIN_HISTORY:
        return []
    df = pd.DataFrame(
        {
            "id": [t.id for t in expenses],
            "amt": [abs(t.amount) for t in expenses],
            "category": [t.category or "Uncategorized" for t in expenses],
            "merchant": [(t.merchant or "").strip().upper() for t in expenses],
            "hour": [t.posted_at.hour + t.posted_at.minute / 60 for t in expenses],
            "has_time": [
                not (t.posted_at.hour == 0 and t.posted_at.minute == 0 and t.posted_at.second == 0)
                for t in expenses
            ],
        }
    )
    df["log_amt"] = np.log1p(df["amt"])
    grp = df.groupby("category")["log_amt"]
    med = grp.transform("median")
    mad = grp.transform(lambda s: float(np.median(np.abs(s - s.median())))) * 1.4826
    global_mad = float(np.median(np.abs(df["log_amt"] - df["log_amt"].median()))) * 1.4826 or 1.0
    df["cat_z"] = (df["log_amt"] - med) / mad.where(mad > 1e-6, global_mad)
    df["cat_median"] = grp.transform(lambda s: float(np.expm1(s.median())))
    counts = df["merchant"].map(df["merchant"].value_counts())
    df["merchant_count"] = counts
    df["merchant_freq"] = counts / len(df)
    timed = df[df["has_time"]]
    typical_hour = float(timed["hour"].median()) if len(timed) else 12.0
    q_lo, q_hi = timed["hour"].quantile([0.05, 0.95]).tolist() if len(timed) else [9.0, 21.0]
    df["hour_dev"] = np.where(
        df["has_time"], _circular_hour_distance(df["hour"].to_numpy(), typical_hour), 0.0
    )

    features = df[["log_amt", "cat_z", "merchant_freq", "hour_dev"]].to_numpy()
    forest = IsolationForest(n_estimators=300, contamination=contamination, random_state=random_state)
    labels = forest.fit_predict(features)
    raw = -forest.score_samples(features)
    span = float(raw.max() - raw.min()) or 1.0
    scores = (raw - raw.min()) / span
    p99 = float(df["amt"].quantile(0.99))

    results: list[AnomalyResult] = []
    for idx, row in df.iterrows():
        i = int(str(idx))
        if labels[i] != -1:
            continue
        reasons: list[str] = []
        if row["cat_z"] >= 3.0:
            ratio = row["amt"] / max(row["cat_median"], 1.0)
            reasons.append(
                f"Amount {_fmt(row['amt'])} is {ratio:.1f}x your typical {row['category']} spend "
                f"(median {_fmt(row['cat_median'])})"
            )
        if row["merchant_count"] == 1 and len(df) >= 2 * MIN_HISTORY:
            reasons.append(f"First transaction ever with merchant '{row['merchant'].title()}'")
        if row["has_time"] and row["hour_dev"] >= 5:
            h = int(row["hour"])
            m = round((row["hour"] - h) * 60) % 60
            reasons.append(
                f"Made at {h:02d}:{m:02d}, outside your usual hours ({int(q_lo):02d}:00-{int(q_hi):02d}:00)"
            )
        if row["amt"] >= p99 and not reasons:
            reasons.append(f"Among your largest 1% of expenses ({_fmt(row['amt'])})")
        if reasons:
            results.append(AnomalyResult(str(row["id"]), round(float(scores[i]), 4), reasons))
    results.sort(key=lambda r: r.score, reverse=True)
    return results
