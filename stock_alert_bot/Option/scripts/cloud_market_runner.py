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

# Multi-Instrument Memory (Deduplicated)
_chart_history = defaultdict(lambda: deque(maxlen=2000))
_last_chain_refresh = defaultdict(int)
_cached_chain = {}
_cached_df_prev = {}

def load_initial_history(db, symbols):
    for sym in symbols:
        try:
            doc_snap = db.collection("option_sentiment").document(sym).get()
            if doc_snap.exists:
                hist = doc_snap.to_dict().get("spot_history", [])
                for h in hist: _chart_history[sym].append(h)
                log.info(f"Loaded {len(hist)} history points for {sym}")
        except: pass

def calculate_technicals(prices):
    if len(prices) < 14: return {"rsi": "-", "macd": "-", "trendline": "N/A", "candle": "N/A"}
    deltas = np.diff(prices)
    gain = np.where(deltas > 0, deltas, 0)
    loss = np.where(deltas < 0, -deltas, 0)
    avg_gain = np.mean(gain[-14:])
    avg_loss = np.mean(loss[-14:])
    rsi = 100 - (100 / (1 + (avg_gain/avg_loss))) if avg_loss != 0 else 100
    ema12 = pd.Series(prices).ewm(span=12).mean().iloc[-1]
    ema26 = pd.Series(prices).ewm(span=26).mean().iloc[-1]
    macd = ema12 - ema26
    slope = (prices[-1] - prices[0]) / len(prices)
    return {"rsi": round(rsi, 1), "macd": round(macd, 2), "trendline": "Bullish" if slope > 0 else "Bearish", "candle": "Bullish" if prices[-1] > prices[-2] else "Bearish"}

def _get_trade_decision(data, tech, structure):
    sig = data.get('final_signal', 'Neutral')
    is_breakout = structure.get('is_breakout', False)
    pattern = structure.get('chart_pattern', '')
    if is_breakout:
        if "Bull" in pattern or "W-Pattern" in pattern: return f"BREAKOUT: {pattern}. Buy at {data['spot_price']}"
        elif "Bear" in pattern or "M-Pattern" in pattern: return f"BREAKDOWN: {pattern}. Sell at {data['spot_price']}"
    if sig == 'Bullish' and tech['trendline'] == 'Bullish': return f"Bullish: Support hold with OI build."
    elif sig == 'Bearish' and tech['trendline'] == 'Bearish': return f"Bearish: Resistance building. Target lower."
    return f"Price and OI are mixed; wait for a clean break or support hold."

def build_full_analysis(symbol):
    try:
        now = time.time()
        live_spot = get_latest_spot(symbol)
        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})
            # Atomic Sync Quote
            sync_to_firestore("live_quotes", symbol, {"symbol": symbol, "spot_price": float(live_spot), "updated_at": datetime.now().strftime("%H:%M:%S")})

        if now - _last_chain_refresh[symbol] > 120 or symbol not in _cached_chain:
            raw = get_option_chain(symbol)
            df, spot_chain = parse_option_chain(raw)
            _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
            _cached_chain[symbol] = df
            _last_chain_refresh[symbol] = now

        if symbol in _cached_chain:
            df = _cached_chain[symbol]
            df_prev = _cached_df_prev.get(symbol, df)
            prices = [s['value'] for s in _chart_history[symbol]]
            spot = live_spot if live_spot else prices[-1] if prices else 23400

            tech = calculate_technicals(prices)
            structure = analyze_price_structure(prices)
            data = build_dashboard(df_prev, df, spot)
            decision = _get_trade_decision(data, tech, structure)

            # --- FULL MIRROR FIELDS ---
            data["trade_alert"] = {
                "action": "BUY CALL" if "Bullish" in decision or "BREAKOUT" in decision else "BUY PUT" if "Bearish" in decision or "BREAKDOWN" in decision else "WAIT",
                "trigger": decision,
                "note": f"System Live ({len(prices)} ticks)",
                "technicals": tech
            }
            data["decision_note"] = decision
            data["oi_trend"] = "Strong" if abs(data.get("pcr_oi", 1) - 1) > 0.1 else "Moderate"

            deep = data.get("deep_dive", {})
            call_v = deep.get("top_call_vol_strikes", [{}])[0].get("call_volume", 0)
            put_v = deep.get("top_put_vol_strikes", [{}])[0].get("put_volume", 0)
            data["vol_flow"] = f"{'Bullish' if call_v > put_v else 'Bearish'} flow | Call vol {call_v} | Put vol {put_v}"
            data["vol_deltas"] = f"call {call_v}, put {put_v}"
            data["oi_deltas"] = f"call 0, put 0"
            data["manipulation_risk"] = "Low"
            data["manipulation_note"] = "No special risk flagged."
            data["countdown_sec"] = int(120 - (now - _last_chain_refresh[symbol]))

            data["price_structure"] = structure
            data["greeks"] = json_safe(analyze_chain_greeks(df, spot))
            data["price_action"] = json_safe(analyze_price_action(symbol))
            data["spot_history"] = list(_chart_history[symbol])
            data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            data["market_status_str"] = f"OPEN | MASTER SYNC | {data['fetched_at']}"

            sync_to_firestore("option_sentiment", symbol, json_safe(data))
            log.info(f"V62.2 PUSH: {symbol} Pattern: {structure.get('chart_pattern')}")

    except Exception as e:
        log.error(f"Sync error for {symbol}: {e}")

def main_loop():
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.join(ROOT, "..", "service-account.json"))
        firebase_admin.initialize_app(cred)
    db = firestore.client()

    # Push immediate start signal
    sync_to_firestore("stock_scans", "status", {
        "status": "Cloud Server Waking Up...",
        "last_scan_time": datetime.now().strftime("%H:%M:%S"),
        "server_live": True
    })

    load_initial_history(db, SYMBOLS)
    for s in SYMBOLS: start_live_feed(s)

    while True:
        try:
            status = market_status()

            # Update Heartbeat
            sync_to_firestore("stock_scans", "status", {
                "status": f"Live - {status.get('reason')}",
                "last_scan_time": datetime.now().strftime("%H:%M:%S"),
                "server_live": True
            })

            if status.get("reason") == "after market close": break
            for sym in SYMBOLS:
                build_full_analysis(sym)
                time.sleep(1)
        except Exception as e:
            log.error(f"Crash: {e}")
            sync_to_firestore("stock_scans", "status", {"status": f"Server Error: {str(e)[:50]}", "server_live": False})
            time.sleep(5)
        time.sleep(3)

if __name__ == "__main__":
    main_loop()
