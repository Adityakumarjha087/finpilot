from __future__ import annotations

from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from finpilot_ml.config import Settings
from finpilot_ml.main import create_app

TOKEN = "test-token"


@pytest.fixture(scope="session")
def model_dir(tmp_path_factory: pytest.TempPathFactory) -> Path:
    return tmp_path_factory.mktemp("models")


@pytest.fixture(scope="session")
def client(model_dir: Path) -> Iterator[TestClient]:
    app = create_app(Settings(model_dir=model_dir, service_token=TOKEN))
    with TestClient(app, headers={"X-Internal-Token": TOKEN}) as c:
        yield c
