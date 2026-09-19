import time
import os
import sys
import logging
import random
import requests
import json
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from collections import deque, defaultdict
import firebase_admin
from firebase_admin import credentials, firestore
import threading

# Setup Path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard, json_safe
from utils.firebase_sync import sync_to_firestore
from brokers.angel_one import start_live_feed, get_latest_spot
from stock_alert.market_hours import market_status
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure
from stock.live_stock_scanner import run_live_scan

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger("CloudTerminal")

# Multi-Instrument Memory (Increased to 3000 points for full week history)
_chart_history = defaultdict(lambda: deque(maxlen=3000))
_last_chain_refresh = defaultdict(int)
_last_stock_scan = 0
_cached_chain = {}
_cached_df_prev = {}
_cached_full_data = {}

def get_yahoo_history(symbol):
    """Fetch 7 days of 1-minute history to ensure charts are always full."""
    try:
        code = "%5ENSEI" if symbol == "NIFTY" else "%5ENSEBANK"
        headers = {'User-Agent': 'Mozilla/5.0'}
        # Fetch 7 days of 1m data to cover the "Previous Day" and more
        r = requests.get(f"https://query1.finance.yahoo.com/v8/finance/chart/{code}?interval=1m&range=7d", headers=headers, timeout=15)
        j = r.json()
        result = j['chart']['result'][0]
        timestamps = result['timestamp']
        closes = result['indicators']['quote'][0]['close']

        hist = []
        for ts, val in zip(timestamps, closes):
            if val is not None:
                hist.append({"time": ts * 1000, "value": float(val)})
        log.info(f"Yahoo: Fetched {len(hist)} historical points for {symbol}")
        return hist
    except Exception as e:
        log.warning(f"Yahoo history fetch failed for {symbol}: {e}")
        return []

def load_initial_state(db, symbols):
    """Prefill memory with 7 days of data so candles are never missing."""
    for sym in symbols:
        try:
            # Always pull fresh Yahoo history on startup to ensure "Previous Day" visibility
            y_hist = get_yahoo_history(sym)
            if y_hist:
                _chart_history[sym].clear()
                for h in y_hist: _chart_history[sym].append(h)
                log.info(f"Memory prefilled with {len(y_hist)} points for {sym}")
        except Exception as e:
            log.warning(f"Initial state load error for {sym}: {e}")

def calculate_technicals(prices):
    if len(prices) < 30: return {"rsi": "-", "macd": "-", "trendline": "N/A", "candle": "N/A"}
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
        live_spot = get_latest_spot(symbol)

        # Yahoo Fallback for live tick
        if not live_spot:
            try:
                code = "%5ENSEI" if symbol == "NIFTY" else "%5ENSEBANK"
                r = requests.get(f"https://query1.finance.yahoo.com/v8/finance/chart/{code}?interval=1m&range=1d", timeout=5)
                live_spot = float(r.json()['chart']['result'][0]['indicators']['quote'][0]['close'][-1])
            except: pass

        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})
            db.collection("live_quotes").document(symbol).set({"spot": float(live_spot), "time": datetime.now().strftime("%H:%M:%S")})

        # Option Chain Refresh (Every 3 mins)
        if now - _last_chain_refresh[symbol] > 180 or symbol not in _cached_chain:
            try:
                raw = get_option_chain(symbol, retries=1)
                df, spot_chain = parse_option_chain(raw)
                if not df.empty:
                    _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
                    _cached_chain[symbol] = df
                    _last_chain_refresh[symbol] = now
            except: pass

        prices = [s['value'] for s in _chart_history[symbol]]
        spot = live_spot if live_spot else (prices[-1] if prices else 23400)

        data = _cached_full_data.get(symbol, {}).copy()

        if symbol in _cached_chain:
            df = _cached_chain[symbol]
            df_prev = _cached_df_prev.get(symbol, df)
            dashboard_data = build_dashboard(df_prev, df, spot)
            data.update(dashboard_data)
            data["greeks"] = json_safe(analyze_chain_greeks(df, spot))

        structure = analyze_price_structure(prices)
        data["spot_price"] = spot
        data["price_structure"] = structure
        data["spot_history"] = list(_chart_history[symbol]) # Full Week History
        data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        data["countdown_sec"] = int(180 - (now - _last_chain_refresh[symbol]))

        # Technical row for tan box
        if "trade_alert" not in data or not isinstance(data["trade_alert"], dict): data["trade_alert"] = {}
        data["trade_alert"]["technicals"] = calculate_technicals(prices)

        # Mapping for Mirror UI
        data["market_read"] = data.get("combined_sentiment_read", "Neutral")
        data["chart_structure_str"] = f"{structure.get('pattern', 'Range')} ({structure.get('trend', 'Neutral')})"
        data["support_str"] = ", ".join(map(str, data.get("support_zones", [])))
        data["resist_str"] = ", ".join(map(str, data.get("resistance_zones", [])))
        data["oi_buildup"] = data.get("oi_buildup_overall", "Neutral")
        data["flow_str"] = data.get("vol_flow", "Neutral")
        data["trend_verdict"] = data.get("decision_note", "Scanning...")

        _cached_full_data[symbol] = data
        db.collection("option_sentiment").document(symbol).set(json_safe(data))
        log.info(f"PUSH: {symbol} @ {spot} | History Points: {len(prices)}")

    except Exception as e:
        log.error(f"Analysis error for {symbol}: {e}")

def main_loop():
    global _last_stock_scan
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.join(ROOT, "..", "service-account.json"))
        firebase_admin.initialize_app(cred, name='master-cloud-engine')
    db = firestore.client(app=firebase_admin.get_app('master-cloud-engine'))

    log.info("Prefilling History...")
    load_initial_state(db, SYMBOLS)
    for s in SYMBOLS: start_live_feed(s)

    while True:
        try:
            status = market_status()
            db.collection("stock_scans").document("status").set({"status": f"Live - {status['reason']}", "last_scan_time": datetime.now().strftime("%H:%M:%S")})

            # Hourly stock automation
            now_ts = time.time()
            if status['is_open'] and (now_ts - _last_stock_scan > 3600):
                threading.Thread(target=run_live_scan, daemon=True).start()
                _last_stock_scan = now_ts

            if status["reason"] == "after market close":
                # Run one last update even after close
                for sym in SYMBOLS: build_full_analysis(sym, db)
                break

            for sym in SYMBOLS:
                build_full_analysis(sym, db)
                time.sleep(1)
        except Exception as e:
            log.error(f"Global Crash: {e}")
            time.sleep(10)
        time.sleep(5)

if __name__ == "__main__":
    main_loop()
