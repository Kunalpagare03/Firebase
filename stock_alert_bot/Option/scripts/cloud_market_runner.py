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
_cached_full_data = {}

def get_yahoo_spot(symbol):
    try:
        code = "%5ENSEI" if symbol == "NIFTY" else "%5ENSEBANK"
        # Explicit timeout to prevent hanging
        r = requests.get(f"https://query1.finance.yahoo.com/v8/finance/chart/{code}?interval=1m&range=1d", timeout=8)
        j = r.json()
        return float(j['chart']['result'][0]['indicators']['quote'][0]['close'][-1])
    except: return None

def load_initial_state(db, symbols):
    for sym in symbols:
        try:
            doc_snap = db.collection("option_sentiment").document(sym).get(timeout=10)
            if doc_snap.exists:
                d = doc_snap.to_dict()
                hist = d.get("spot_history", [])
                for h in hist: _chart_history[sym].append(h)
                _cached_full_data[sym] = d
                log.info(f"Restored {len(hist)} history points for {sym}")
        except Exception as e:
            log.warning(f"Initial state load failed: {e}")

def build_full_analysis(symbol, db):
    try:
        now = time.time()
        # 1. High-Speed Spot (Angel -> Yahoo)
        live_spot = get_latest_spot(symbol) or get_yahoo_spot(symbol)

        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})
            # Fast heart-beat update for phone
            db.collection("live_quotes").document(symbol).set({"spot": float(live_spot), "time": datetime.now().strftime("%H:%M:%S")})

        # 2. Option Chain Sync (Resilient)
        if now - _last_chain_refresh[symbol] > 150 or symbol not in _cached_chain:
            try:
                raw = get_option_chain(symbol, retries=1)
                df, spot_chain = parse_option_chain(raw)
                if not df.empty:
                    _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
                    _cached_chain[symbol] = df
                    _last_chain_refresh[symbol] = now
                    log.info(f"SUCCESS: {symbol} Chain Refresh")
            except:
                log.warning(f"NSE BLOCK on {symbol}. Using previous chain data.")

        # 3. Assemble Dashboard
        prices = [s['value'] for s in _chart_history[symbol]]
        spot = live_spot if live_spot else (prices[-1] if prices else 23400)

        data = _cached_full_data.get(symbol, {"final_signal": "Neutral"}).copy()

        if symbol in _cached_chain:
            df = _cached_chain[symbol]
            df_prev = _cached_df_prev.get(symbol, df)
            dashboard_data = build_dashboard(df_prev, df, spot)
            data.update(dashboard_data)
            data["greeks"] = json_safe(analyze_chain_greeks(df, spot))

        # 4. Technical Overlays
        structure = analyze_price_structure(prices)
        data["spot_price"] = spot
        data["price_structure"] = structure
        data["spot_history"] = list(_chart_history[symbol])
        data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        data["market_status_str"] = f"OPEN | MASTER SYNC | {data['fetched_at']}"

        # Mapping screenshot labels
        data["market_read"] = data.get("combined_sentiment_read", "Neutral")
        data["chart_structure_str"] = f"{structure.get('pattern', 'Range')} ({structure.get('trend', 'Neutral')})"
        data["support_str"] = ", ".join(map(str, data.get("support_zones", [])))
        data["resist_str"] = ", ".join(map(str, data.get("resistance_zones", [])))
        data["oi_buildup"] = data.get("oi_buildup_overall", "Neutral")
        data["flow_str"] = data.get("vol_flow", "Neutral")
        data["trend_verdict"] = data.get("decision_note", "Scanning...")
        data["countdown_sec"] = int(150 - (now - _last_chain_refresh[symbol]))

        _cached_full_data[symbol] = data
        db.collection("option_sentiment").document(symbol).set(json_safe(data))
        log.info(f"CLOUD PUSH: {symbol} @ {spot}")

    except Exception as e:
        log.error(f"Sync error for {symbol}: {e}")

def main_loop():
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.join(ROOT, "..", "service-account.json"))
        firebase_admin.initialize_app(cred, name='master-hub')
    db = firestore.client(app=firebase_admin.get_app('master-hub'))

    log.info("Continuous Master Engine Re-Starting...")
    load_initial_state(db, SYMBOLS)
    for s in SYMBOLS: start_live_feed(s)

    while True:
        try:
            status = market_status()
            # Server Heartbeat
            db.collection("stock_scans").document("status").set({
                "status": f"Live - {status['reason']}",
                "last_scan_time": datetime.now().strftime("%H:%M:%S")
            })

            if status["reason"] == "after market close":
                log.info("Market Closed. Session Complete.")
                break

            for sym in SYMBOLS:
                build_full_analysis(sym, db)
                time.sleep(2)
        except Exception as e:
            log.error(f"Global Crash: {e}")
            time.sleep(10)
        time.sleep(5)

if __name__ == "__main__":
    main_loop()
