import time
import os
import sys
import logging
import threading
import json
import numpy as np
import pandas as pd
import requests
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

# Multi-Instrument Memory
_chart_history = defaultdict(lambda: deque(maxlen=2000))
_last_chain_refresh = defaultdict(int)
_cached_chain = {}
_cached_df_prev = {}

def get_yahoo_spot(symbol):
    try:
        code = "%5ENSEI" if symbol == "NIFTY" else "%5ENSEBANK"
        r = requests.get(f"https://query1.finance.yahoo.com/v8/finance/chart/{code}?interval=1m&range=1d", timeout=5)
        j = r.json()
        return float(j['chart']['result'][0]['indicators']['quote'][0]['close'][-1])
    except: return None

def load_initial_history(db, symbols):
    for sym in symbols:
        try:
            doc_snap = db.collection("option_sentiment").document(sym).get()
            if doc_snap.exists:
                d = doc_snap.to_dict()
                hist = d.get("spot_history", [])
                for h in hist: _chart_history[sym].append(h)

                # Load last successful chain as fallback for Cloud (NSE Block)
                # Note: We can't easily store the whole DF, but we can try to reuse the last sync
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
        # High-res Spot (Yahoo fallback for Cloud)
        live_spot = get_latest_spot(symbol) or get_yahoo_spot(symbol)

        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})
            # Fast Quote Sync
            sync_to_firestore("live_quotes", symbol, {"spot": float(live_spot), "time": datetime.now().strftime("%H:%M:%S")})

        # Option Chain Logic (Resilient to NSE Blocking)
        if now - _last_chain_refresh[symbol] > 180 or symbol not in _cached_chain:
            try:
                raw = get_option_chain(symbol, retries=1) # Fast fail for NSE block
                df, spot_chain = parse_option_chain(raw)
                _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
                _cached_chain[symbol] = df
                _last_chain_refresh[symbol] = now
            except:
                log.warning(f"NSE Blocked Cloud for {symbol}. Using cached chain data.")
                # If we have no cache, try to fetch the DF from the last Firestore update
                if symbol not in _cached_chain:
                    try:
                        # Fallback strike data if NSE is completely dead
                        pass
                    except: pass

        if symbol in _cached_chain or live_spot:
            df = _cached_chain.get(symbol, pd.DataFrame())
            df_prev = _cached_df_prev.get(symbol, df)
            prices = [s['value'] for s in _chart_history[symbol]]
            spot = live_spot if live_spot else prices[-1] if prices else 23400

            tech = calculate_technicals(prices)
            structure = analyze_price_structure(prices)

            # Dashboard build
            if not df.empty:
                data = build_dashboard(df_prev, df, spot)
                data["greeks"] = json_safe(analyze_chain_greeks(df, spot))
            else:
                # Fallback structure if only spot is moving
                data = {"final_signal": "Neutral", "combined_sentiment_read": "Cloud Standby"}

            data["price_structure"] = structure
            data["price_action"] = json_safe(analyze_price_action(symbol))
            data["spot_history"] = list(_chart_history[symbol])
            data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            data["market_status_str"] = f"OPEN | CLOUD LIVE | {data['fetched_at']}"
            if "trade_alert" not in data: data["trade_alert"] = {}
            data["trade_alert"]["technicals"] = tech

            sync_to_firestore("option_sentiment", symbol, json_safe(data))
            log.info(f"CLOUD PUSH: {symbol} @ {live_spot}")

    except Exception as e:
        log.error(f"Sync error for {symbol}: {e}")

def main_loop():
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.join(ROOT, "..", "service-account.json"))
        firebase_admin.initialize_app(cred)
    db = firestore.client()

    sync_to_firestore("stock_scans", "status", {"status": "Cloud Hub Starting...", "last_scan_time": datetime.now().strftime("%H:%M:%S")})

    load_initial_history(db, SYMBOLS)
    for s in SYMBOLS: start_live_feed(s)

    while True:
        try:
            status = market_status()
            sync_to_firestore("stock_scans", "status", {"status": f"Live - {status['reason']}", "last_scan_time": datetime.now().strftime("%H:%M:%S")})

            if status["reason"] == "after market close": break
            for sym in SYMBOLS:
                build_full_analysis(sym, db)
                time.sleep(2)
        except Exception as e:
            log.error(f"Global Crash: {e}")
            time.sleep(10)
        time.sleep(5)

if __name__ == "__main__":
    main_loop()
