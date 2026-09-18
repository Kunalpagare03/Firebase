import time
import os
import sys
import logging
import random
import requests
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
from brokers.angel_one import start_live_feed, get_latest_spot
from stock_alert.market_hours import market_status
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger("CloudTerminal")

# Multi-Instrument Memory (Max 1000 points to keep Firestore documents small)
_chart_history = defaultdict(lambda: deque(maxlen=1000))
_last_chain_refresh = defaultdict(int)
_cached_chain = {}
_cached_df_prev = {}

def get_yahoo_spot(symbol):
    try:
        code = "%5ENSEI" if symbol == "NIFTY" else "%5ENSEBANK"
        headers = {'User-Agent': 'Mozilla/5.0'}
        r = requests.get(f"https://query1.finance.yahoo.com/v8/finance/chart/{code}?interval=1m&range=1d", headers=headers, timeout=10)
        j = r.json()
        prices = j['chart']['result'][0]['indicators']['quote'][0]['close']
        valid = [p for p in prices if p is not None]
        return float(valid[-1]) if valid else None
    except: return None

def load_initial_history(db, symbols):
    for sym in symbols:
        try:
            doc_snap = db.collection("option_sentiment").document(sym).get()
            if doc_snap.exists:
                hist = doc_snap.to_dict().get("spot_history", [])
                if hist:
                    for h in hist: _chart_history[sym].append(h)
                    log.info(f"Loaded {len(hist)} history points for {sym}")
        except: pass

def calculate_technicals(prices):
    if len(prices) < 14: return {"rsi": "-", "macd": "-", "trendline": "N/A", "candle": "N/A"}
    prices_arr = np.array(prices)
    deltas = np.diff(prices_arr)
    gain = np.where(deltas > 0, deltas, 0)
    loss = np.where(deltas < 0, -deltas, 0)
    avg_gain = np.mean(gain[-14:])
    avg_loss = np.mean(loss[-14:])
    rsi = 100 - (100 / (1 + (avg_gain/avg_loss))) if avg_loss != 0 else 100
    ema12 = pd.Series(prices_arr).ewm(span=12).mean().iloc[-1]
    ema26 = pd.Series(prices_arr).ewm(span=26).mean().iloc[-1]
    macd = ema12 - ema26
    slope = (prices_arr[-1] - prices_arr[0]) / len(prices_arr)
    return {"rsi": round(rsi, 1), "macd": round(macd, 2), "trendline": "Bullish" if slope > 0 else "Bearish", "candle": "Bullish" if prices_arr[-1] > prices_arr[-2] else "Bearish"}

def build_full_analysis(symbol, db):
    try:
        now = time.time()
        # 1. Spot (High Precision)
        live_spot = get_latest_spot(symbol) or get_yahoo_spot(symbol)
        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})
            db.collection("live_quotes").document(symbol).set({"spot": float(live_spot), "time": datetime.now().strftime("%H:%M:%S")})

        # 2. Resilient Chain Refresh (Every 120s)
        if now - _last_chain_refresh[symbol] > 120 or symbol not in _cached_chain:
            try:
                raw = get_option_chain(symbol, retries=1)
                df, spot_chain = parse_option_chain(raw)
                if not df.empty:
                    _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
                    _cached_chain[symbol] = df
                    _last_chain_refresh[symbol] = now
                    log.info(f"Refreshed {symbol}")
            except:
                log.warning(f"NSE Blocked {symbol}")

        # 3. State Assembly
        prices = [s['value'] for s in _chart_history[symbol]]
        spot = live_spot if live_spot else (prices[-1] if prices else 23400)

        # Load last state from Firestore to preserve static cards
        doc_ref = db.collection("option_sentiment").document(symbol)
        prev_data = doc_ref.get().to_dict() or {}
        data = prev_data.copy()

        if symbol in _cached_chain:
            df = _cached_chain[symbol]
            df_prev = _cached_df_prev.get(symbol, df)
            dashboard_data = build_dashboard(df_prev, df, spot)
            data.update(dashboard_data)
            data["greeks"] = json_safe(analyze_chain_greeks(df, spot))

        tech = calculate_technicals(prices)
        structure = analyze_price_structure(prices)

        data["spot_price"] = spot
        data["price_structure"] = structure
        data["spot_history"] = list(_chart_history[symbol])
        data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        data["market_status_str"] = f"OPEN | MASTER ENGINE | {data['fetched_at']}"

        # UI Alignment Mappings
        data["market_read"] = data.get("combined_sentiment_read", "Neutral")
        data["chart_structure_str"] = f"{structure.get('pattern', 'Range')} ({structure.get('trend', 'Neutral')})"
        data["support_str"] = ", ".join(map(str, data.get("support_zones", [])))
        data["resist_str"] = ", ".join(map(str, data.get("resistance_zones", [])))
        data["oi_buildup"] = data.get("oi_buildup_overall", "Neutral")
        data["flow_str"] = data.get("vol_flow", "Neutral")
        data["trend_verdict"] = data.get("decision_note", "Scanning...")
        data["countdown_sec"] = int(120 - (now - _last_chain_refresh[symbol]))

        if "trade_alert" not in data: data["trade_alert"] = {}
        data["trade_alert"]["technicals"] = tech

        # Final Cloud Push
        doc_ref.set(json_safe(data))
        log.info(f"PUSH: {symbol} @ {spot} - History: {len(prices)}")

    except Exception as e:
        log.error(f"Error for {symbol}: {e}")

def main_loop():
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.join(ROOT, "..", "service-account.json"))
        firebase_admin.initialize_app(cred)
    db = firestore.client()

    sync_to_firestore("stock_scans", "status", {"status": "Cloud Hub Wake-up...", "last_scan_time": datetime.now().strftime("%H:%M:%S")})

    load_initial_history(db, SYMBOLS)
    for s in SYMBOLS: start_live_feed(s)

    while True:
        try:
            status = market_status()
            # Push Heartbeat
            db.collection("stock_scans").document("status").set({"status": f"Live - {status['reason']}", "last_scan_time": datetime.now().strftime("%H:%M:%S")})

            if status["reason"] == "after market close": break

            for sym in SYMBOLS:
                build_full_analysis(sym, db)
                time.sleep(1)

            time.sleep(random.uniform(5, 10)) # Rate limiting

        except Exception as e:
            log.error(f"Global Crash: {e}")
            time.sleep(10)

if __name__ == "__main__":
    main_loop()
