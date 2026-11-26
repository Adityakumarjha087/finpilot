from fastapi.testclient import TestClient


def test_health_is_public(client: TestClient) -> None:
    r = client.get("/health", headers={"X-Internal-Token": ""})
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_token_required(client: TestClient) -> None:
    r = client.get("/v1/categories", headers={"X-Internal-Token": "wrong"})
    assert r.status_code == 401


def test_categories_and_model(client: TestClient) -> None:
    assert "Food" in client.get("/v1/categories").json()
    info = client.get("/v1/model").json()
    assert info["version"] >= 1
    assert info["holdout_accuracy"] > 0.9


def test_categorize(client: TestClient) -> None:
    r = client.post(
        "/v1/categorize",
        json={"items": [{"id": "a", "description": "NETFLIX.COM ACH D-", "amount": -649}]},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["predictions"][0]["id"] == "a"
    assert body["predictions"][0]["category"] == "Subscriptions"


def test_train_validates_category(client: TestClient) -> None:
    bad = client.post(
        "/v1/train", json={"corrections": [{"description": "x", "amount": -1, "category": "Nope"}]}
    )
    assert bad.status_code == 422
    ok = client.post(
        "/v1/train",
        json={"corrections": [{"description": "ZXQ LABS", "amount": -10, "category": "Education"}]},
    )
    assert ok.status_code == 200
    assert ok.json()["n_corrections"] == 1


def test_forecast(client: TestClient) -> None:
    hist = [{"period": f"2026-{m:02d}", "amount": 1000 + m * 10} for m in range(1, 10)]
    r = client.post("/v1/forecast", json={"series": [{"key": "Food", "history": hist}], "horizon": 3})
    assert r.status_code == 200
    fc = r.json()["forecasts"][0]
    assert fc["key"] == "Food"
    assert len(fc["points"]) == 3
    assert client.post("/v1/forecast", json={"series": [], "horizon": 30}).status_code == 422


def test_synthetic_and_anomalies(client: TestClient) -> None:
    rows = client.get("/v1/synthetic/transactions", params={"months": 4, "seed": 5}).json()
    assert len(rows) > 150
    txns = [
        {
            "id": str(i),
            "posted_at": r["posted_at"],
            "amount": r["amount"],
            "category": r["category"],
            "merchant": r["merchant"],
        }
        for i, r in enumerate(rows)
    ]
    r = client.post("/v1/anomalies", json={"transactions": txns})
    assert r.status_code == 200
    assert r.json()["scanned"] == len(txns)
