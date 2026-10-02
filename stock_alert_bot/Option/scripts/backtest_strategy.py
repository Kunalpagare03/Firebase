"""Backtest the option-flow plus price-action strategy."""
import argparse
from pathlib import Path
import sys

import yfinance as yf

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.analyze_history import load_history, summarize_history
from stock_alert.strategy import backtest, build_signals


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--symbol", default="NIFTY")
    parser.add_argument("--period", default="5y")
    args = parser.parse_args()
    history = summarize_history(load_history(args.symbol))
    yahoo_symbol = "^NSEI" if args.symbol.upper() in ("NIFTY", "NSEI") else args.symbol
    prices = yf.Ticker(yahoo_symbol).history(period=args.period, interval="1d", auto_adjust=False)
    if prices.empty:
        raise RuntimeError(f"No price history returned for {yahoo_symbol}")
    signals = build_signals(prices[["Open", "High", "Low", "Close"]], history)
    result = backtest(signals)
    print(result)
    if result["status"] == "insufficient_history":
        print("Need at least two dated option snapshots, preferably 3-12 months, before trusting results.")


if __name__ == "__main__":
    main()