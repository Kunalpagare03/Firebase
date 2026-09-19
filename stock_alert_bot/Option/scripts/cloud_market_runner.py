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
from utils.firebase_sync import sync_to_firestore
from brokers.angel_one import start_live_feed, get_latest_spot
from stock_alert.market_hours import market_status
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure

from stock.live_stock_scanner import run_live_scan

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger("CloudTerminal")

# Multi-Instrument Memory
_chart_history = defaultdict(lambda: deque(maxlen=1000))
_last_chain_refresh = defaultdict(int)
_last_stock_scan = 0
_cached_chain = {}
_cached_df_prev = {}
_cached_full_data = {}

def get_yahoo_spot(symbol):
    """Fallback high-reliability spot price fetcher with explicit timeout."""
    try:
        code = "%5ENSEI" if symbol == "NIFTY" else "%5ENSEBANK"
        headers = {'User-Agent': 'Mozilla/5.0'}
        # 5 second timeout to prevent hanging the whole engine
        r = requests.get(f"https://query1.finance.yahoo.com/v8/finance/chart/{code}?interval=1m&range=1d", headers=headers, timeout=5)
        j = r.json()
        prices = j['chart']['result'][0]['indicators']['quote'][0]['close']
        valid_prices = [p for p in prices if p is not None]
        return float(valid_prices[-1]) if valid_prices else None
    except Exception as e:
        log.warning(f"Yahoo fetch timeout/fail for {symbol}: {e}")
        return None

def load_initial_state(db, symbols):
    """Restore history and state from Firestore to prevent blank charts."""
    for sym in symbols:
        try:
            doc_snap = db.collection("option_sentiment").document(sym).get(timeout=10)
            if doc_snap.exists:
                d = doc_snap.to_dict()
                hist = d.get("spot_history", [])
                if hist:
                    _chart_history[sym].clear()
                    for h in hist: _chart_history[sym].append(h)
                _cached_full_data[sym] = d
                log.info(f"Successfully restored state for {sym}")
        except Exception as e:
            log.warning(f"Initial state load failed for {sym}: {e}")

def build_full_analysis(symbol, db):
    try:
        now = time.time()
        # 1. Get Spot Price
        live_spot = get_latest_spot(symbol) or get_yahoo_spot(symbol)

        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})
            # Fast heart-beat update for phone app
            db.collection("live_quotes").document(symbol).set({
                "spot": float(live_spot),
                "time": datetime.now().strftime("%H:%M:%S"),
                "status": "Online"
            })

        # 2. Resilient Option Chain Refresh (Every 150s)
        if now - _last_chain_refresh[symbol] > 150 or symbol not in _cached_chain:
            try:
                raw = get_option_chain(symbol, retries=1)
                df, spot_chain = parse_option_chain(raw)
                if not df.empty:
                    _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
                    _cached_chain[symbol] = df
                    _last_chain_refresh[symbol] = now
                    log.info(f"SYNC: {symbol} Chain Refresh SUCCESS")
            except Exception as e:
                log.warning(f"NSE BLOCK: Falling back to cached chain for {symbol}")

        # 3. Assemble Dashboard with Error Isolation
        prices = [s['value'] for s in _chart_history[symbol]]
        spot = live_spot if live_spot else (prices[-1] if prices else 23400)

        # Start with cached data to ensure UI never goes blank
        data = _cached_full_data.get(symbol, {}).copy()

        if symbol in _cached_chain:
            try:
                df = _cached_chain[symbol]
                df_prev = _cached_df_prev.get(symbol, df)
                dashboard_data = build_dashboard(df_prev, df, spot)
                data.update(dashboard_data)
                data["greeks"] = json_safe(analyze_chain_greeks(df, spot))
            except Exception as e:
                log.error(f"Dashboard build failed for {symbol}: {e}")

        # 4. Technical Overlays
        try:
            structure = analyze_price_structure(prices)
            data["price_structure"] = structure
            data["market_read"] = data.get("combined_sentiment_read", "Neutral")
            data["chart_structure_str"] = f"{structure.get('pattern', 'Range')} ({structure.get('trend', 'Neutral')})"
            data["support_str"] = ", ".join(map(str, data.get("support_zones", [])))
            data["resist_str"] = ", ".join(map(str, data.get("resistance_zones", [])))
            data["trend_verdict"] = data.get("decision_note", "Scanning market flow...")
        except: pass

        data["spot_price"] = spot
        data["spot_history"] = list(_chart_history[symbol])
        data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        data["market_status_str"] = f"OPEN | MASTER ENGINE | {data['fetched_at']}"
        data["countdown_sec"] = int(150 - (now - _last_chain_refresh[symbol]))

        # UI mapping for 1:1 Mirror
        data["oi_buildup"] = data.get("oi_buildup_overall", "Neutral")
        data["flow_str"] = data.get("vol_flow", "Neutral")

        # Update Price Action
        try:
            data["price_action"] = json_safe(analyze_price_action(symbol))
        except: pass

        _cached_full_data[symbol] = data
        db.collection("option_sentiment").document(symbol).set(json_safe(data))
        log.info(f"PUSH: {symbol} @ {spot} - History: {len(prices)}")

    except Exception as e:
        log.error(f"Analysis loop crash for {symbol}: {e}")
        db.collection("stock_scans").document("status").set({"status": f"Engine Error: {str(e)[:50]}", "last_scan_time": datetime.now().strftime("%H:%M:%S")})

def main_loop():
    global _last_stock_scan
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.join(ROOT, "..", "service-account.json"))
        firebase_admin.initialize_app(cred, name='cloud-master-sync')
    db = firestore.client(app=firebase_admin.get_app('cloud-master-sync'))

    log.info("Cloud Hub Engine RESTARTING...")
    sync_to_firestore("stock_scans", "status", {"status": "Cloud Hub Restarting...", "last_scan_time": datetime.now().strftime("%H:%M:%S")})

    load_initial_state(db, SYMBOLS)
    for s in SYMBOLS: start_live_feed(s)

    while True:
        try:
            status = market_status()
            # Push Heartbeat
            db.collection("stock_scans").document("status").set({
                "status": f"Live - {status['reason']}",
                "last_scan_time": datetime.now().strftime("%H:%M:%S")
            })

            # --- HOURLY STOCK SCAN AUTOMATION ---
            # Run every 60 minutes during market hours
            now_ts = time.time()
            if status['is_open'] and (now_ts - _last_stock_scan > 3600):
                log.info("Triggering Hourly Automated Stock Scan...")
                threading.Thread(target=run_live_scan, daemon=True).start()
                _last_stock_scan = now_ts

            if status["reason"] == "after market close":
                log.info("Closing session.")
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
