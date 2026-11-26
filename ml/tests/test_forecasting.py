import pytest

from finpilot_ml.forecasting import forecast_series


def _periods(n: int) -> list[str]:
    return [f"{2025 + (i // 12)}-{(i % 12) + 1:02d}" for i in range(n)]


def test_ets_trend_forecast_has_ordered_intervals() -> None:
    values = [1000 + 50 * i + (30 if i % 2 else -30) for i in range(14)]
    fc = forecast_series(_periods(14), values, horizon=3, confidence=0.8)
    assert fc.method == "ets_damped_trend"
    assert [p.period for p in fc.points] == ["2026-03", "2026-04", "2026-05"]
    for p in fc.points:
        assert 0 <= p.lower <= p.mean <= p.upper
    assert fc.points[0].mean > 1500


def test_short_series_uses_mean_fallback() -> None:
    fc = forecast_series(["2026-01", "2026-02", "2026-03"], [100, 200, 300], horizon=2)
    assert fc.method == "mean"
    assert fc.points[0].mean == 200
    assert fc.points[0].lower < 200 < fc.points[0].upper


def test_gaps_are_zero_filled_and_negative_amounts_absolute() -> None:
    fc = forecast_series(["2026-01", "2026-03"], [-100, -300], horizon=1)
    assert fc.points[0].period == "2026-04"
    assert fc.points[0].mean == pytest.approx(133.33, rel=1e-3)


def test_constant_series_and_validation() -> None:
    fc = forecast_series(_periods(8), [500.0] * 8, horizon=1)
    assert fc.points[0].mean == 500
    with pytest.raises(ValueError):
        forecast_series(["2026-01"], [], horizon=1)
    with pytest.raises(ValueError):
        forecast_series([], [], horizon=1)
