"""Capture one live option-chain snapshot for future analysis."""
import argparse
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step1_fetch_dhan import get_option_chain_dhan
from stock_alert.storage import save_live_snapshot, save_option_chain_snapshot


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--symbol", default="NIFTY")
    parser.add_argument("--source", choices=("nse", "dhan"), default="nse")
    parser.add_argument("--expiry")
    args = parser.parse_args()
    if args.source == "dhan":
        raw = get_option_chain_dhan(args.symbol, expiry=args.expiry)
    else:
        raw = get_option_chain(args.symbol, expiry=args.expiry)
    raw_path, csv_path = save_option_chain_snapshot(raw, args.symbol, args.source)
    live_path = save_live_snapshot(raw, args.symbol, args.source)
    print(f"Saved raw snapshot: {raw_path}")
    print(f"Updated analysis CSV: {csv_path}")
    print(f"Updated live snapshot: {live_path}")


if __name__ == "__main__":
    main()