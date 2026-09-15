import time
import os
import sys
import logging
import threading
import json
from datetime import datetime
from collections import deque, defaultdict

# Add project roots to path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard, json_safe
from step9_deep_dive_analysis import perform_deep_dive
from utils.firebase_sync import sync_to_firestore
from brokers.angel_one import start_live_feed, get_latest_spot
from stock_alert.market_hours import market_status
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger("CloudTerminal")

# Persistent memory
_chart_history = defaultdict(lambda: deque(maxlen=1000))
_last_full_refresh = defaultdict(int)
_cached_chain = {}
_cached_spot = {}


def publish_live_quotes():
    while True:
        for symbol in ("NIFTY", "BANKNIFTY"):
            spot = get_latest_spot(symbol)
            if spot is not None:
                sync_to_firestore("live_quotes", symbol, {
                    "symbol": symbol,
                    "spot_price": float(spot),
                    "updated_at": datetime.now().strftime("%H:%M:%S.%f")[:-3],
                })
        time.sleep(1)

def sync_symbol_to_cloud(symbol):
    try:
        now = time.time()
        live_spot = get_latest_spot(symbol)

        # 1. Update Chart History
        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})
            log.info(f"Tick: {symbol} @ {live_spot}")

        # 2. Refresh Option Chain (Every 2 minutes)
        if now - _last_full_refresh[symbol] > 120 or symbol not in _cached_chain:
            try:
                raw = get_option_chain(symbol)
                df, spot_chain = parse_option_chain(raw)
                _cached_chain[symbol] = df
                _cached_spot[symbol] = spot_chain
                _last_full_refresh[symbol] = now
                log.info(f"Chain Refreshed: {symbol}")
            except Exception as e:
                log.error(f"NSE Fetch Error: {e}")

        # 3. Build & Push Master Payload
        if symbol in _cached_chain:
            df = _cached_chain[symbol]
            spot = live_spot if live_spot is not None else _cached_spot.get(symbol, 0)

            # Use step7's dashboard builder
            data = build_dashboard(df, df, spot)

            # Add Real-Time Overlays
            data["greeks"] = json_safe(analyze_chain_greeks(df, spot))
            data["price_action"] = json_safe(analyze_price_action(symbol))
            data["price_structure"] = analyze_price_structure([s["value"] for s in _chart_history[symbol]])
            data["spot_history"] = list(_chart_history[symbol])
            data["fetched_at"] = datetime.now().strftime("%H:%M:%S")
            data["deep_dive"] = json_safe(perform_deep_dive(df, spot))

            # Atomic Push to Cloud
            sync_to_firestore("option_sentiment", symbol, json_safe(data))
            log.info(f"PRO HUB SYNC: {symbol} at {data['fetched_at']}")

    except Exception as e:
        log.error(f"Sync failed for {symbol}: {e}")

def main_loop():
    log.info("==== STARTING ULTIMATE CLOUD RUNNER ====")
    SYMBOLS = ["NIFTY", "BANKNIFTY"]

    # Initialize Angel Feed
    for s in SYMBOLS: start_live_feed(s)
    threading.Thread(target=publish_live_quotes, name="live-quote-publisher", daemon=True).start()

    while True:
        try:
            # Check market hours
            status = market_status()
            if status.get("reason") == "after market close":
                log.info("Market Hours Ended. Exiting.")
                break

            if not status["is_open"]:
                log.info(f"Waiting for market open... {status['local_time']}")
                time.sleep(60)
                continue

            # Run Sync for each index
            for symbol in SYMBOLS:
                sync_symbol_to_cloud(symbol)
                time.sleep(2)

        except Exception as e:
            log.error(f"Global Loop Error: {e}")
            time.sleep(10)

        time.sleep(5)

if __name__ == "__main__":
    main_loop()
