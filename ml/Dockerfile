# syntax=docker/dockerfile:1
FROM python:3.12-slim-bookworm AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
COPY --from=ghcr.io/astral-sh/uv:0.12.9 /uv /usr/local/bin/uv
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
RUN uv sync --frozen --no-dev \
 && useradd --system --uid 10001 finpilot \
 && mkdir -p /app/models /app/base-model \
 && ML_MODEL_DIR=/app/base-model /app/.venv/bin/python -c "from pathlib import Path; from finpilot_ml.categorizer import Categorizer; Categorizer(Path('/app/base-model')).train([])" \
 && chown -R finpilot /app/models
COPY docker-entrypoint.sh /usr/local/bin/
USER finpilot
ENV PATH="/app/.venv/bin:$PATH" ML_MODEL_DIR=/app/models
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8000/health').status==200 else 1)"
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["uvicorn", "finpilot_ml.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
