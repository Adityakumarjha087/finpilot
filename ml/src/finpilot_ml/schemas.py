"""Request/response contracts for the ML HTTP API."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field, field_validator

from finpilot_ml.synthetic import CATEGORIES


class CategorizeItem(BaseModel):
    id: str | None = None
    description: str = Field(min_length=1, max_length=500)
    amount: float


class Alternative(BaseModel):
    category: str
    probability: float


class CategoryPrediction(BaseModel):
    id: str | None
    category: str
    confidence: float
    alternatives: list[Alternative]


class CategorizeRequest(BaseModel):
    items: list[CategorizeItem] = Field(max_length=5000)


class CategorizeResponse(BaseModel):
    model_version: int
    predictions: list[CategoryPrediction]


class CorrectionIn(BaseModel):
    description: str = Field(min_length=1, max_length=500)
    amount: float
    category: str

    @field_validator("category")
    @classmethod
    def known_category(cls, value: str) -> str:
        if value not in CATEGORIES:
            raise ValueError(f"unknown category {value}")
        return value


class TrainRequest(BaseModel):
    corrections: list[CorrectionIn] = Field(default_factory=list, max_length=100_000)


class ModelInfo(BaseModel):
    version: int
    trained_at: str
    n_samples: int
    n_corrections: int
    holdout_accuracy: float
    holdout_macro_f1: float
    classes: list[str]


class SeriesPoint(BaseModel):
    period: str = Field(pattern=r"^\d{4}-\d{2}$")
    amount: float


class Series(BaseModel):
    key: str = Field(min_length=1, max_length=64)
    history: list[SeriesPoint] = Field(min_length=1, max_length=240)


class ForecastRequest(BaseModel):
    series: list[Series] = Field(max_length=64)
    horizon: int = Field(default=3, ge=1, le=12)
    confidence: float = Field(default=0.8, gt=0.5, lt=0.99)


class ForecastPointOut(BaseModel):
    period: str
    mean: float
    lower: float
    upper: float


class SeriesForecast(BaseModel):
    key: str
    method: str
    points: list[ForecastPointOut]


class ForecastResponse(BaseModel):
    horizon: int
    confidence: float
    forecasts: list[SeriesForecast]


class AnomalyTxn(BaseModel):
    id: str
    posted_at: datetime
    amount: float
    category: str | None = None
    merchant: str | None = None


class AnomalyRequest(BaseModel):
    transactions: list[AnomalyTxn] = Field(max_length=50_000)
    contamination: float = Field(default=0.01, gt=0.0, le=0.2)


class AnomalyOut(BaseModel):
    id: str
    score: float
    reasons: list[str]


class AnomalyResponse(BaseModel):
    scanned: int
    anomalies: list[AnomalyOut]
