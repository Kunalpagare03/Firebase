import time
import os
import sys
import logging
import threading
import json
import numpy as np
import pandas as pd
from datetime import datetime
from collections import deque, defaultdict
import firebase_admin
from firebase_admin import credentials, firestore

# Setup Path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard, json_safe
from step9_deep_dive_analysis import perform_deep_dive
from utils.firebase_sync import sync_to_firestore
from brokers.angel_one import start_live_feed, get_latest_spot, get_live_status
from stock_alert.market_hours import market_status
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger("CloudTerminal")

# Memory
_chart_history = defaultdict(lambda: deque(maxlen=2000))
_last_chain_refresh = defaultdict(int)
_cached_chain = {}
_cached_df_prev = {}

def load_initial_history(db, symbols):
    """Fetch existing history from cloud on startup."""
    for sym in symbols:
        try:
            doc = db.collection("option_sentiment").document(sym).get()
            if doc.exists:
                hist = doc.to_dict().get("spot_history", [])
                for h in hist: _chart_history[sym].append(h)
                log.info(f"Loaded {len(hist)} historical points for {sym}")
        except Exception as e:
            log.warning(f"Failed to load history for {sym}: {e}")

def calculate_technicals(prices):
    if len(prices) < 14:
        return {"rsi": "-", "macd": "-", "trendline": "N/A", "candle": "N/A"}

    # RSI calculation
    deltas = np.diff(prices)
    gain = np.where(deltas > 0, deltas, 0)
    loss = np.where(deltas < 0, -deltas, 0)
    avg_gain = np.mean(gain[-14:])
    avg_loss = np.mean(loss[-14:])
    if avg_loss == 0: rsi = 100
    else:
        rs = avg_gain / avg_loss
        rsi = 100 - (100 / (1 + rs))

    # MACD (Simplified)
    ema12 = pd.Series(prices).ewm(span=12).mean().iloc[-1]
    ema26 = pd.Series(prices).ewm(span=26).mean().iloc[-1]
    macd = ema12 - ema26

    # Trend & Candle
    slope = (prices[-1] - prices[0]) / len(prices)
    trend = "Bullish" if slope > 0 else "Bearish"
    candle = "Bullish" if prices[-1] > prices[-2] else "Bearish"

    return {"rsi": round(rsi, 1), "macd": round(macd, 2), "trendline": trend, "candle": candle}

def _get_trade_decision(data, tech):
    sig = data.get('final_signal', 'Neutral')
    if sig == 'Bullish' and tech['trendline'] == 'Bullish':
        return f"Bullish confirmation: Price holding above support with strong OI build."
    elif sig == 'Bearish' and tech['trendline'] == 'Bearish':
        return f"Bearish confirmation: Resistance BUILDING. Target lower levels."
    return f"Wait for clean break. Price and OI are mixed."

def build_full_analysis(symbol):
    try:
        now = time.time()
        live_spot = get_latest_spot(symbol)

        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})

        # Refresh Chain
        if now - _last_chain_refresh[symbol] > 120 or symbol not in _cached_chain:
            try:
                raw = get_option_chain(symbol)
                df, spot_chain = parse_option_chain(raw)
                _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
                _cached_chain[symbol] = df
                _last_chain_refresh[symbol] = now
            except: pass

        if symbol in _cached_chain:
            df = _cached_chain[symbol]
            df_prev = _cached_df_prev.get(symbol, df)
            prices = [s['value'] for s in _chart_history[symbol]]
            spot = live_spot if live_spot else prices[-1] if prices else 23400

            tech = calculate_technicals(prices)
            data = build_dashboard(df_prev, df, spot)

            # Inject Trade Alert & Decision note
            data["trade_alert"] = {
                "action": data.get("final_signal", "WAIT"),
                "trigger": "Wait for break" if data.get("final_signal") == "Neutral" else f"Trigger active @ {spot}",
                "note": f"Heartbeat active ({len(prices)} ticks)",
                "technicals": tech
            }
            data["decision_note"] = _get_trade_decision(data, tech)

            # Pattern Recognition (High Priority)
            data["price_structure"] = analyze_price_structure(prices)
            data["greeks"] = json_safe(analyze_chain_greeks(df, spot))
            data["price_action"] = json_safe(analyze_price_action(symbol))
            data["deep_dive"] = json_safe(perform_deep_dive(df, spot))
            data["spot_history"] = list(_chart_history[symbol])
            data["fetched_at"] = datetime.now().strftime("%H:%M:%S")
            data["market_status_str"] = f"OPEN | MASTER HUB | {data['fetched_at']}"

            sync_to_firestore("option_sentiment", symbol, json_safe(data))
            log.info(f"HUB SYNC: {symbol} [{data['price_structure'].get('chart_pattern')}]")

    except Exception as e:
        log.error(f"Sync error for {symbol}: {e}")

def main_loop():
    log.info("==== STARTING v57 FULL RUNNER ====")
    SYMBOLS = ["NIFTY", "BANKNIFTY"]

    # Init Firebase and load history
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.join(ROOT, "..", "service-account.json"))
        firebase_admin.initialize_app(cred)
    db = firestore.client()
    load_initial_history(db, SYMBOLS)

    for s in SYMBOLS: start_live_feed(s)

    while True:
        try:
            status = market_status()
            if status.get("reason") == "after market close": break
            for sym in SYMBOLS:
                build_full_analysis(sym)
                time.sleep(1)
        except Exception as e:
            log.error(f"Runner Crash: {e}")
            time.sleep(10)
        time.sleep(4)

if __name__ == "__main__":
    main_loop()
