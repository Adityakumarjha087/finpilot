from datetime import date

from finpilot_ml.synthetic import CATEGORIES, MERCHANTS, labelled_corpus, user_history


def test_every_merchant_has_known_category() -> None:
    assert {m.category for m in MERCHANTS} == set(CATEGORIES)


def test_labelled_corpus_is_deterministic_and_signed() -> None:
    a = labelled_corpus(2, seed=1)
    b = labelled_corpus(2, seed=1)
    assert a == b
    assert len(a) == 2 * len(MERCHANTS)
    for _desc, amount, category, _m in a:
        assert (amount > 0) == (category == "Income")


def test_user_history_shape() -> None:
    hist = user_history(months=6, seed=3, end=date(2026, 6, 30))
    assert len(hist) > 250
    months = {t.posted_at[:7] for t in hist}
    assert months == {f"2026-0{i}" for i in range(1, 7)}
    assert any(t.category == "Income" and t.amount > 50000 for t in hist)
    assert all(t.posted_at <= "2026-06-30T23:59:59" for t in hist)
    assert hist == sorted(hist, key=lambda t: t.posted_at)
