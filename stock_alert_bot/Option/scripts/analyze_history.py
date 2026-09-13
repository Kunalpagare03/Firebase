"""Summarize saved option-chain history for strategy research."""
import argparse
import csv
import os
import sys
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from stock_alert.storage import ANALYSIS_DIR


def load_history(symbol, history_dir=None):
    history_dir = Path(history_dir or ROOT / "data" / "history")
    files = sorted(history_dir.glob(f"*/{symbol.replace('^', '').replace('/', '_')}_option_chain.csv"))
    if not files:
        raise FileNotFoundError(f"No saved history found for {symbol} under {history_dir}")
    columns = ["captured_at", "source", "expiry", "spot_price", "strike", "call_oi", "put_oi", "call_oi_change", "put_oi_change", "call_volume", "put_volume", "call_ltp", "put_ltp", "call_iv", "put_iv"]
    frames = []
    for path in files:
        rows = []
        with path.open(newline="") as handle:
            for row in csv.reader(handle):
                if row and row[0] == "captured_at":
                    continue
                if len(row) == 14:
                    row.insert(3, "")
                if len(row) == len(columns):
                    rows.append(row)
        frames.append(pd.DataFrame(rows, columns=columns))
    return pd.concat(frames, ignore_index=True)


def summarize_history(history):
    if "spot_price" not in history:
        history = history.copy()
        history["spot_price"] = pd.NA
    numeric_columns = ["spot_price", "call_oi", "put_oi", "call_volume", "put_volume"]
    history = history.copy()
    for column in numeric_columns:
        history[column] = pd.to_numeric(history[column], errors="coerce")
    grouped = history.groupby("captured_at", as_index=False).agg(
        call_oi=("call_oi", "sum"), put_oi=("put_oi", "sum"),
        call_volume=("call_volume", "sum"), put_volume=("put_volume", "sum"),
        spot_price=("spot_price", "first"),
    )
    grouped["pcr_oi"] = (grouped["put_oi"] / grouped["call_oi"]).round(3)
    grouped["pcr_volume"] = (grouped["put_volume"] / grouped["call_volume"]).round(3)
    grouped["net_oi_put_minus_call"] = grouped["put_oi"] - grouped["call_oi"]
    grouped["net_volume_put_minus_call"] = grouped["put_volume"] - grouped["call_volume"]
    return grouped.sort_values("captured_at")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--symbol", default="NIFTY")
    args = parser.parse_args()
    summary = summarize_history(load_history(args.symbol))
    print(summary.to_string(index=False))
    ANALYSIS_DIR.mkdir(parents=True, exist_ok=True)
    output = ANALYSIS_DIR / f"{args.symbol.replace('^', '').replace('/', '_')}_history_summary.csv"
    summary.to_csv(output, index=False)
    print(f"Saved analysis summary: {output}")


if __name__ == "__main__":
    main()