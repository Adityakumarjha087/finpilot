from pathlib import Path

from finpilot_ml.categorizer import Categorizer, Correction, normalise


def test_normalise_strips_refs_and_channels() -> None:
    assert normalise("UPI/SWIGGY/123456789/Payment", -250) == "swiggy __debit__"
    assert normalise("NEFT-99887766-SALARY CREDIT", 1000).endswith("__credit__")


def test_train_predict_and_persist(tmp_path: Path) -> None:
    cat = Categorizer(tmp_path)
    meta = cat.train([], base_samples_per_merchant=12)
    assert meta.version == 1
    assert meta.holdout_accuracy > 0.9
    preds = cat.predict([("POS 4111XXXXXX1234 SWIGGY BANGALORE", -420.0), ("SALARY CREDIT ACME", 120000.0)])
    assert preds[0].category == "Food"
    assert preds[1].category == "Income"
    assert 0 < preds[0].confidence <= 1
    assert len(preds[0].alternatives) == 2
    reloaded = Categorizer(tmp_path)
    assert reloaded.load_or_train().version == 1
    assert reloaded.predict([]) == []


def test_corrections_teach_new_merchant(tmp_path: Path) -> None:
    cat = Categorizer(tmp_path)
    cat.train([], base_samples_per_merchant=10)
    weird = "QWXZ VENTURES PVT"
    corrections = [Correction(f"UPI/{weird}/{i}/Payment", -900.0, "Health") for i in range(3)]
    meta = cat.train(corrections, base_samples_per_merchant=10)
    assert meta.version == 2
    assert meta.n_corrections == 3
    assert cat.predict([(f"POS {weird} PUNE", -950.0)])[0].category == "Health"


def test_predict_without_model_raises(tmp_path: Path) -> None:
    import pytest

    with pytest.raises(RuntimeError):
        Categorizer(tmp_path).predict([("x", 1.0)])
