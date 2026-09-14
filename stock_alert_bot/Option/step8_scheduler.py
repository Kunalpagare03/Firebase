"""
Step 8 -- Live scheduling + logging.

Runs the full pipeline every N minutes:
  1. Fetch live option chain (Step 1)
  2. Parse it (Step 2)
  3. Keep previous snapshot in memory for buildup comparison (Step 6)
  4. Build the combined dashboard (Step 7)
  5. Print it AND append a row to a CSV log (Step 9 folded in here)

Run this on your own machine with normal internet access, ideally
during NSE market hours (9:15 AM - 3:30 PM IST) since data is only
live/meaningful during trading hours.

Usage:
    python step8_scheduler.py
    (Ctrl+C to stop)
"""

import time
import csv
import os
from datetime import datetime

import json
from step1_fetch import get_option_chain
from step1_fetch_dhan import get_option_chain_dhan
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard
from utils.firebase_sync import sync_to_firestore
from stock_alert.execution import place_order_for_signal
from stock_alert.market_hours import market_status

# Load config
PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
CFG_PATH = os.path.join(PROJECT_ROOT, "config", "settings.json")
if os.path.isfile(CFG_PATH):
    with open(CFG_PATH, "r") as f:
        cfg = json.load(f)
else:
    cfg = {}

REFRESH_MINUTES = cfg.get("refresh_minutes", 5)
SYMBOL = cfg.get("symbol", "NIFTY")
EXPIRY = cfg.get("expiry")
LOG_FILE = cfg.get("log_file", "sentiment_log.csv")
if not os.path.isabs(LOG_FILE):
    LOG_FILE = os.path.join(PROJECT_ROOT, LOG_FILE)
USE_LIVE_API = cfg.get("use_live_api", True)
NSE_RETRIES = cfg.get("nse_retry_count", 3)
NSE_BACKOFF = cfg.get("nse_retry_backoff_seconds", 2)
ENABLE_ORDER_EXECUTION = cfg.get("enable_order_execution", False)


def log_to_csv(dashboard, timestamp):
    file_exists = os.path.isfile(LOG_FILE)
    with open(LOG_FILE, "a", newline="") as f:
        writer = csv.writer(f)
        if not file_exists:
            writer.writerow([
                "timestamp", "spot_price", "pcr_oi", "pcr_volume",
                "max_pain", "oi_buildup_overall", "combined_sentiment_read",
                "bullish_votes", "bearish_votes"
            ])
        writer.writerow([
            timestamp,
            dashboard["spot_price"],
            dashboard["pcr_oi"],
            dashboard["pcr_volume"],
            dashboard["max_pain"],
            dashboard["oi_buildup_overall"],
            dashboard["combined_sentiment_read"],
            dashboard["votes"]["bullish"],
            dashboard["votes"]["bearish"],
        ])


def _fetch_live_option_chain(symbol, retries, backoff):
    configured_source = (cfg.get("data_source") or "dhan").lower()

    if configured_source == "nse":
        return get_option_chain(symbol, retries=retries, backoff=backoff), "nse"

    if configured_source == "yfinance_mock":
        from step1_fetch_dhan import generate_mock_option_chain
        safe = symbol.replace("^", "").replace("/", "_")
        out_path = os.path.join(PROJECT_ROOT, "data", f"option_chain_{safe}.json")
        raw = generate_mock_option_chain(symbol, out_path=out_path)
        return raw, "yfinance_mock"

    dh_url = cfg.get("dhan_option_chain_url")
    try:
        raw = get_option_chain_dhan(symbol, url=dh_url, retries=retries, backoff=backoff)
        return raw, "dhan"
    except Exception as dhan_error:
        try:
            raw = get_option_chain(symbol, retries=retries, backoff=backoff)
            return raw, "nse-fallback"
        except Exception as nse_error:
            raise RuntimeError(f"Dhan failed: {dhan_error}; NSE failed: {nse_error}") from nse_error


def run_loop():
    df_prev = None

    print(f"Starting live monitor for {SYMBOL}, refreshing every {REFRESH_MINUTES} min.")
    print("Press Ctrl+C to stop.\n")

    while True:
        try:
            session = market_status()
            reason = session.get("reason", "unknown")
            if reason == "after market close":
                print(f"Market closed ({session['local_time']}); stopping live monitor at session close.")
                return
            if not session["is_open"]:
                print(f"Market not yet open ({reason}, {session['local_time']}); waiting.")
                time.sleep(REFRESH_MINUTES * 60)
                continue
            if USE_LIVE_API:
                raw, source = _fetch_live_option_chain(SYMBOL, NSE_RETRIES, NSE_BACKOFF)
            else:
                # Use mock loader from parse module to simulate live data
                from step2_parse import load_mock_nse_response
                raw = load_mock_nse_response()
                source = "mock"
            df_curr, spot_curr = parse_option_chain(raw, expiry=EXPIRY)

            if df_prev is not None:
                dashboard = build_dashboard(df_prev, df_curr, spot_curr)
                ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                print(f"\n[{ts}] Dashboard update:")
                for k, v in dashboard.items():
                    if k != "caveat":
                        print(f"  {k}: {v}")
                log_to_csv(dashboard, ts)
                sync_to_firestore("option_sentiment", SYMBOL, dashboard)
                print(f"  (logged to {LOG_FILE})")
                # Execution: place orders when signal is clear
                if ENABLE_ORDER_EXECUTION:
                    try:
                        sig = dashboard.get("combined_sentiment_read")
                        if sig in ("Leaning Bullish", "Leaning Bearish"):
                            resp = place_order_for_signal(sig)
                            print(f"  Order execution response: {resp}")
                    except Exception as ex:
                        print(f"  Execution error: {ex}")
            else:
                print("First snapshot captured -- need one more cycle to compute buildup trends.")

            df_prev = df_curr

        except Exception as e:
            print(f"Error during fetch/analysis: {e}")

        time.sleep(REFRESH_MINUTES * 60)


if __name__ == "__main__":
    run_loop()
