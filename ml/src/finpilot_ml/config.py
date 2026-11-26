"""Runtime configuration (environment driven)."""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ML_", env_file=".env", extra="ignore")

    model_dir: Path = Path("models")
    service_token: str = "dev-internal-token"
    log_level: str = "INFO"
    max_batch: int = 5000


@lru_cache
def get_settings() -> Settings:
    return Settings()
