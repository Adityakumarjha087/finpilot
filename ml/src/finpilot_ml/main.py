"""FastAPI application exposing categorisation, training, forecasting and anomaly detection."""

from __future__ import annotations

import logging
import secrets
import time
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, Response
from fastapi.concurrency import run_in_threadpool

from finpilot_ml import __version__
from finpilot_ml.anomaly import TxnInput, detect
from finpilot_ml.categorizer import Categorizer, Correction
from finpilot_ml.config import Settings, get_settings
from finpilot_ml.forecasting import forecast_series
from finpilot_ml.schemas import (
    Alternative,
    AnomalyOut,
    AnomalyRequest,
    AnomalyResponse,
    CategorizeRequest,
    CategorizeResponse,
    CategoryPrediction,
    ForecastPointOut,
    ForecastRequest,
    ForecastResponse,
    ModelInfo,
    SeriesForecast,
    TrainRequest,
)
from finpilot_ml.synthetic import CATEGORIES, user_history

log = logging.getLogger("finpilot_ml")


def create_app(settings: Settings | None = None) -> FastAPI:
    cfg = settings or get_settings()
    logging.basicConfig(
        level=cfg.log_level,
        format='{"ts":"%(asctime)s","level":"%(levelname)s","logger":"%(name)s","msg":"%(message)s"}',
    )
    categorizer = Categorizer(cfg.model_dir)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        meta = await run_in_threadpool(categorizer.load_or_train)
        log.info("categoriser ready version=%s acc=%s", meta.version, meta.holdout_accuracy)
        yield

    app = FastAPI(
        title="FinPilot ML Service",
        version=__version__,
        description="Categorisation (TF-IDF + LR), ETS forecasting and Isolation-Forest anomaly detection.",
        lifespan=lifespan,
    )
    app.state.categorizer = categorizer

    @app.middleware("http")
    async def access_log(request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
        start = time.perf_counter()
        response = await call_next(request)
        log.info(
            "%s %s %s %.1fms",
            request.method,
            request.url.path,
            response.status_code,
            (time.perf_counter() - start) * 1000,
        )
        return response

    def require_token(x_internal_token: Annotated[str | None, Header()] = None) -> None:
        if x_internal_token is None or not secrets.compare_digest(x_internal_token, cfg.service_token):
            raise HTTPException(status_code=401, detail="invalid internal token")

    auth = [Depends(require_token)]

    @app.get("/health")
    def health() -> dict[str, object]:
        meta = categorizer.meta
        return {"status": "ok" if meta else "starting", "model_version": meta.version if meta else None}

    @app.get("/v1/categories", dependencies=auth)
    def categories() -> list[str]:
        return list(CATEGORIES)

    @app.get("/v1/model", dependencies=auth)
    def model_info() -> ModelInfo:
        if categorizer.meta is None:
            raise HTTPException(503, "model not ready")
        return ModelInfo(**categorizer.meta.__dict__)

    @app.post("/v1/categorize", dependencies=auth)
    def categorize(req: CategorizeRequest) -> CategorizeResponse:
        if categorizer.meta is None:
            raise HTTPException(503, "model not ready")
        preds = categorizer.predict([(i.description, i.amount) for i in req.items])
        return CategorizeResponse(
            model_version=categorizer.meta.version,
            predictions=[
                CategoryPrediction(
                    id=item.id,
                    category=p.category,
                    confidence=p.confidence,
                    alternatives=[Alternative(category=c, probability=pr) for c, pr in p.alternatives],
                )
                for item, p in zip(req.items, preds, strict=True)
            ],
        )

    @app.post("/v1/train", dependencies=auth)
    def train(req: TrainRequest) -> ModelInfo:
        meta = categorizer.train([Correction(c.description, c.amount, c.category) for c in req.corrections])
        log.info(
            "retrained version=%s corrections=%s acc=%s",
            meta.version,
            meta.n_corrections,
            meta.holdout_accuracy,
        )
        return ModelInfo(**meta.__dict__)

    @app.post("/v1/forecast", dependencies=auth)
    def forecast(req: ForecastRequest) -> ForecastResponse:
        out: list[SeriesForecast] = []
        for s in req.series:
            fc = forecast_series(
                [p.period for p in s.history], [p.amount for p in s.history], req.horizon, req.confidence
            )
            out.append(
                SeriesForecast(
                    key=s.key,
                    method=fc.method,
                    points=[ForecastPointOut(**p.__dict__) for p in fc.points],
                )
            )
        return ForecastResponse(horizon=req.horizon, confidence=req.confidence, forecasts=out)

    @app.post("/v1/anomalies", dependencies=auth)
    def anomalies(req: AnomalyRequest) -> AnomalyResponse:
        txns = [
            TxnInput(t.id, t.posted_at, t.amount, t.category or "Uncategorized", t.merchant or "")
            for t in req.transactions
        ]
        found = detect(txns, contamination=req.contamination)
        return AnomalyResponse(
            scanned=len(txns),
            anomalies=[AnomalyOut(id=a.id, score=a.score, reasons=a.reasons) for a in found],
        )

    @app.get("/v1/synthetic/transactions", dependencies=auth)
    def synthetic(
        months: Annotated[int, Query(ge=1, le=36)] = 12,
        seed: Annotated[int, Query()] = 42,
    ) -> list[dict[str, object]]:
        return [t.as_dict() for t in user_history(months=months, seed=seed)]

    return app


app = create_app()
