"""Write a synthetic bank statement CSV in a typical Indian bank export layout.

Usage: uv run python -m finpilot_ml.scripts.generate_statement --out statement.csv --months 3
The layout deliberately differs from FinPilot's internal schema (separate
withdrawal/deposit columns, dd/mm/yyyy dates) to exercise the column-mapping UI.
"""

from __future__ import annotations

import argparse
import csv
from datetime import datetime
from pathlib import Path

from finpilot_ml.synthetic import user_history


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--months", type=int, default=3)
    parser.add_argument("--seed", type=int, default=2024)
    args = parser.parse_args()
    rows = user_history(months=args.months, seed=args.seed)
    balance = 250000.0
    with args.out.open("w", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["Txn Date", "Time", "Narration", "Withdrawal Amt", "Deposit Amt", "Closing Balance"])
        for r in rows:
            ts = datetime.fromisoformat(r.posted_at)
            balance += r.amount
            writer.writerow(
                [
                    ts.strftime("%d/%m/%Y"),
                    ts.strftime("%H:%M"),
                    r.description,
                    f"{-r.amount:.2f}" if r.amount < 0 else "",
                    f"{r.amount:.2f}" if r.amount > 0 else "",
                    f"{balance:.2f}",
                ]
            )
    print(f"wrote {len(rows)} rows to {args.out}")


if __name__ == "__main__":
    main()
