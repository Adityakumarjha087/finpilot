"""Monthly spend forecasting with prediction intervals (statsmodels ETS, robust fallbacks)."""

from __future__ import annotations

import warnings
from dataclasses import dataclass

import numpy as np
import pandas as pd
from scipy import stats
from statsmodels.tsa.exponential_smoothing.ets import ETSModel


@dataclass
class ForecastPoint:
    period: str
    mean: float
    lower: float
    upper: float


@dataclass
class Forecast:
    method: str
    points: list[ForecastPoint]


def _future_periods(last: str, horizon: int) -> list[str]:
    start = pd.Period(last, freq="M")
    return [str(start + i) for i in range(1, horizon + 1)]


def _naive(values: np.ndarray, periods: list[str], z: float) -> Forecast:
    mean = float(values.mean()) if values.size else 0.0
    std = float(values.std(ddof=1)) if values.size > 1 else abs(mean) * 0.25
    pts = [
        ForecastPoint(p, round(mean, 2), round(max(0.0, mean - z * std), 2), round(mean + z * std, 2))
        for p in periods
    ]
    return Forecast("mean", pts)


def forecast_series(
    periods: list[str], values: list[float], horizon: int = 3, confidence: float = 0.8
) -> Forecast:
    """Forecast a non-negative monthly series ``horizon`` steps ahead.

    * >= 6 points: additive-error ETS with damped additive trend; intervals from
      the model's analytical prediction distribution.
    * 2-5 points: mean +/- z * std (too little data for a trend model).
    * Values are clipped at zero because spend can't go negative.
    """
    if len(periods) != len(values):
        raise ValueError("periods and values must have equal length")
    if not periods:
        raise ValueError("series must contain at least one point")
    raw = pd.Series([abs(v) for v in values], index=pd.PeriodIndex(periods, freq="M")).groupby(level=0).sum()
    full_index = pd.period_range(raw.index.min(), raw.index.max(), freq="M")
    series = raw.reindex(full_index, fill_value=0.0).astype(float)  # a missing month means zero spend
    y = series.to_numpy()
    future = _future_periods(str(full_index[-1]), horizon)
    z = float(stats.norm.ppf(0.5 + confidence / 2))
    if len(y) < 6 or np.allclose(y, y[0]):
        return _naive(y, future, z)
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            endog = pd.Series(y, index=full_index.to_timestamp(how="start"), dtype=float).asfreq("MS")
            model = ETSModel(endog, error="add", trend="add", damped_trend=True)
            fit = model.fit(disp=False, maxiter=200)
            pred = fit.get_prediction(start=len(y), end=len(y) + horizon - 1)
            frame = pred.summary_frame(alpha=1 - confidence)
        means = frame["mean"].to_numpy()
        lows = frame["pi_lower"].to_numpy()
        highs = frame["pi_upper"].to_numpy()
        if not np.all(np.isfinite(means)):
            return _naive(y, future, z)
        pts = [
            ForecastPoint(
                p, round(max(0.0, float(m)), 2), round(max(0.0, float(lo)), 2), round(max(0.0, float(hi)), 2)
            )
            for p, m, lo, hi in zip(future, means, lows, highs, strict=True)
        ]
        return Forecast("ets_damped_trend", pts)
    except (ValueError, np.linalg.LinAlgError):
        return _naive(y, future, z)
