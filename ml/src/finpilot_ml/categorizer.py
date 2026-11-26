"""TF-IDF + Logistic Regression transaction categoriser with an active-learning retrain path."""

from __future__ import annotations

import json
import re
import threading
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import FeatureUnion, Pipeline

from finpilot_ml.synthetic import labelled_corpus

_REF = re.compile(r"\d{3,}")
_NON_ALNUM = re.compile(r"[^a-z0-9 ]+")
_SPACES = re.compile(r"\s+")
_PREFIXES = re.compile(r"\b(upi|pos|neft|imps|ach|vin|bil|onl|card purchase|xxxxxx|okhdfc|payment|in)\b")

CORRECTION_WEIGHT = 8.0


def normalise(description: str, amount: float) -> str:
    """Strip reference numbers and channel noise; append a sign token so credits are separable."""
    text = description.lower()
    text = _REF.sub(" ", text)
    text = _NON_ALNUM.sub(" ", text)
    text = _PREFIXES.sub(" ", text)
    text = _SPACES.sub(" ", text).strip()
    sign = "__credit__" if amount > 0 else "__debit__"
    return f"{text} {sign}"


def build_pipeline() -> Pipeline:
    features = FeatureUnion(
        [
            ("char", TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), min_df=2, sublinear_tf=True)),
            ("word", TfidfVectorizer(analyzer="word", ngram_range=(1, 2), sublinear_tf=True)),
        ]
    )
    clf = LogisticRegression(C=8.0, max_iter=3000)
    return Pipeline([("features", features), ("clf", clf)])


@dataclass
class Prediction:
    category: str
    confidence: float
    alternatives: list[tuple[str, float]]


@dataclass
class ModelMeta:
    version: int
    trained_at: str
    n_samples: int
    n_corrections: int
    holdout_accuracy: float
    holdout_macro_f1: float
    classes: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class Correction:
    description: str
    amount: float
    category: str


class Categorizer:
    """Thread-safe wrapper owning the current model and its on-disk persistence."""

    def __init__(self, model_dir: Path) -> None:
        self._dir = model_dir
        self._lock = threading.RLock()
        self._pipeline: Pipeline | None = None
        self.meta: ModelMeta | None = None

    @property
    def _model_path(self) -> Path:
        return self._dir / "categorizer.joblib"

    @property
    def _meta_path(self) -> Path:
        return self._dir / "categorizer.json"

    def load_or_train(self) -> ModelMeta:
        with self._lock:
            if self._model_path.exists() and self._meta_path.exists():
                self._pipeline = joblib.load(self._model_path)
                self.meta = ModelMeta(**json.loads(self._meta_path.read_text()))
                return self.meta
            return self.train([])

    def train(self, corrections: list[Correction], base_samples_per_merchant: int = 30) -> ModelMeta:
        """(Re)train on the synthetic base corpus plus up-weighted user corrections."""
        base = labelled_corpus(n_per_merchant=base_samples_per_merchant)
        texts = [normalise(d, a) for d, a, _, _ in base]
        labels = [c for _, _, c, _ in base]
        weights = [1.0] * len(base)
        for corr in corrections:
            texts.append(normalise(corr.description, corr.amount))
            labels.append(corr.category)
            weights.append(CORRECTION_WEIGHT)

        x_tr, x_te, y_tr, y_te, w_tr, _ = train_test_split(
            texts,
            labels,
            weights,
            test_size=0.15,
            random_state=13,
            stratify=labels if not corrections else None,
        )
        holdout = build_pipeline()
        holdout.fit(x_tr, y_tr, clf__sample_weight=np.asarray(w_tr))
        y_pred = holdout.predict(x_te)
        acc = float(accuracy_score(y_te, y_pred))
        f1 = float(f1_score(y_te, y_pred, average="macro"))

        final = build_pipeline()
        final.fit(texts, labels, clf__sample_weight=np.asarray(weights))
        with self._lock:
            version = (self.meta.version + 1) if self.meta else 1
            meta = ModelMeta(
                version=version,
                trained_at=datetime.now(UTC).isoformat(),
                n_samples=len(texts),
                n_corrections=len(corrections),
                holdout_accuracy=round(acc, 4),
                holdout_macro_f1=round(f1, 4),
                classes=[str(c) for c in final.classes_],
            )
            self._dir.mkdir(parents=True, exist_ok=True)
            joblib.dump(final, self._model_path)
            self._meta_path.write_text(json.dumps(meta.__dict__, indent=2))
            self._pipeline = final
            self.meta = meta
            return meta

    def predict(self, items: list[tuple[str, float]], top_k: int = 3) -> list[Prediction]:
        with self._lock:
            pipeline = self._pipeline
        if pipeline is None:
            raise RuntimeError("model not loaded")
        if not items:
            return []
        probs: Any = pipeline.predict_proba([normalise(d, a) for d, a in items])
        classes = [str(c) for c in pipeline.classes_]
        out: list[Prediction] = []
        for row in probs:
            order = np.argsort(row)[::-1][:top_k]
            ranked = [(classes[i], round(float(row[i]), 4)) for i in order]
            out.append(Prediction(category=ranked[0][0], confidence=ranked[0][1], alternatives=ranked[1:]))
        return out
