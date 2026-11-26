from datetime import datetime

from finpilot_ml.anomaly import TxnInput, detect
from finpilot_ml.synthetic import user_history


def _txns(seed: int = 11) -> tuple[list[TxnInput], set[str]]:
    hist = user_history(months=8, seed=seed, anomaly_rate=0.02)
    txns = [
        TxnInput(str(i), datetime.fromisoformat(t.posted_at), t.amount, t.category, t.merchant)
        for i, t in enumerate(hist)
    ]
    return txns, {str(i) for i, t in enumerate(hist) if t.is_anomaly}


def test_detects_injected_anomalies_with_reasons() -> None:
    txns, truth = _txns()
    found = detect(txns)
    assert found, "expected some anomalies"
    ids = {a.id for a in found}
    assert truth
    assert len(ids & truth) / len(truth) >= 0.8
    assert all(a.reasons for a in found)
    assert all(0 <= a.score <= 1 for a in found)
    assert found == sorted(found, key=lambda a: a.score, reverse=True)


def test_obvious_outlier_explained() -> None:
    base = [
        TxnInput(str(i), datetime(2026, 1, 1 + i % 28, 13, 0), -300.0 - (i % 7) * 10, "Food", "SWIGGY")
        for i in range(60)
    ]
    base.append(TxnInput("big", datetime(2026, 2, 3, 3, 15), -25000.0, "Food", "NEW PLACE"))
    found = {a.id: a for a in detect(base, contamination=0.02)}
    assert "big" in found
    text = " ".join(found["big"].reasons)
    assert "typical Food" in text
    assert "03:15" in text
    assert "New Place" in text


def test_too_little_history_returns_nothing() -> None:
    txns = [TxnInput(str(i), datetime(2026, 1, 1), -10.0, "Food", "X") for i in range(5)]
    assert detect(txns) == []
