import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import os
import sys
import concurrent.futures
from utils.firebase_sync import sync_to_firestore
from utils.sector_map import get_sector, SECTOR_MAP
import time
import firebase_admin
from firebase_admin import credentials, firestore

# Setup Path for Pattern Engine
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "Option"))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from src.trend_detector import analyze_price_structure

# Institutional Watchlist (2,800+ stocks)
WATCHLIST = [sym for sector in SECTOR_MAP.values() for sym in sector]

def calculate_rsi(series, period=14):
    if len(series) < period + 1: return 50
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / loss
    return float(100 - (100 / (1 + rs.iloc[-1]))) if not rs.empty and rs.iloc[-1] != 0 else 50

def analyze_stock(symbol):
    try:
        ticker = yf.Ticker(symbol)
        df = ticker.history(period="6mo", interval="1d")
        if df.empty or len(df) < 50: return None, None, None

        curr = df.iloc[-1]
        prev = df.iloc[-2]

        # Indicators
        df['ema20'] = df['Close'].ewm(span=20, adjust=False).mean()
        df['ema50'] = df['Close'].ewm(span=50, adjust=False).mean()
        df['ema200'] = df['Close'].ewm(span=200, adjust=False).mean()
        rsi_val = calculate_rsi(df['Close'])

        avg_vol_20 = df['Volume'].tail(20).mean()
        vol_ratio = curr['Volume'] / avg_vol_20 if avg_vol_20 > 0 else 0
        change_pct = ((curr['Close'] - prev['Close']) / prev['Close']) * 100

        # Pattern Detection
        structure = analyze_price_structure(df['Close'].tolist())

        # --- USER SPECIFIC SCAN CONDITIONS ---
        c1 = curr['Close'] > curr['Open']            # Bullish Candle
        c2 = curr['Volume'] > (avg_vol_20 * 1.5)     # Vol > 1.5x SMA20
        c3 = curr['Close'] > df['ema20'].iloc[-1]    # Above 20 EMA
        c4 = curr['Close'] > df['ema50'].iloc[-1]    # Above 50 EMA
        c5 = 55 < rsi_val < 65                       # RSI in accumulation zone
        c6 = curr['Close'] > 100                     # Price > 100
        c7 = curr['High'] > prev['High']             # Higher High

        is_strict_setup = all([c1, c2, c3, c4, c5, c6, c7])

        # Score
        score = 0
        if is_strict_setup: score += 5
        if curr['Close'] > df['ema200'].iloc[-1]: score += 2
        if vol_ratio > 2.0: score += 2
        if change_pct > 2.0: score += 1

        rating = min(10, score)
        conviction = "High" if is_strict_setup or rating >= 8 else "Medium"

        base_info = {
            "symbol": symbol,
            "sector": get_sector(symbol),
            "price": round(curr['Close'], 2),
            "target_price": round(curr['Close'] * 1.05, 2),
            "sl_price": round(curr['Close'] * 0.97, 2),
            "change": round(change_pct, 2),
            "rating": f"{rating}/10",
            "conviction": conviction,
            "pattern": structure.get('chart_pattern', 'Consolidating'),
            "is_breakout": structure.get('is_breakout', False),
            "vol_ratio": round(vol_ratio, 1),
            "justification": f"Entry: Buy Next Day if Price > {round(curr['High'], 2)} & RSI < 70. Setup valid: {'YES' if is_strict_setup else 'PARTIAL'}."
        }

        intra, swing, pos = None, None, None
        if is_strict_setup or (rating >= 7 and vol_ratio > 1.8):
            intra = {**base_info, "recommendation": "NEXT DAY BUY"}
        if is_strict_setup or (rating >= 6 and curr['Close'] > df['ema20'].iloc[-1]):
            swing = {**base_info, "recommendation": "SWING SETUP"}
        if df['ema50'].iloc[-1] > df['ema200'].iloc[-1] and rating >= 7:
            pos = {**base_info, "recommendation": "STRUCTURAL BUY"}

        return intra, swing, pos
    except:
        return None, None, None

def run_live_scan():
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "service-account.json")))
        firebase_admin.initialize_app(cred)
    db = firestore.client()

    ts_now = datetime.now()
    # Sortable ISO Format for Firestore
    ts_iso = ts_now.isoformat()
    ts_str = ts_now.strftime("%d-%m-%Y %H:%M:%S")
    doc_id = ts_now.strftime("%Y%m%d_%H%M")

    sync_to_firestore("stock_scans", "status", {"status": "Institutional Deep Scan Active...", "start_time": ts_str})

    intra_list, swing_list, pos_list = [], [], []

    with concurrent.futures.ThreadPoolExecutor(max_workers=15) as executor:
        futures = {executor.submit(analyze_stock, sym): sym for sym in WATCHLIST}
        for future in concurrent.futures.as_completed(futures):
            try:
                res = future.result()
                if res:
                    i, s, p = res
                    if i: intra_list.append(i)
                    if s: swing_list.append(s)
                    if p: pos_list.append(p)
            except: pass

    # Unique Sorting logic
    intra_list = sorted(intra_list, key=lambda x: (x['is_breakout'], x['vol_ratio']), reverse=True)[:40]
    intra_syms = {x['symbol'] for x in intra_list}

    swing_filtered = [s for s in swing_list if s['symbol'] not in intra_syms]
    swing_list = sorted(swing_filtered, key=lambda x: (x['is_breakout'], x['change']), reverse=True)[:40]

    swing_syms = {x['symbol'] for x in swing_list}
    pos_filtered = [p for p in pos_list if p['symbol'] not in intra_syms and p['symbol'] not in swing_syms]
    pos_list = sorted(pos_filtered, key=lambda x: (int(x['rating'].split('/')[0]), x['vol_ratio']), reverse=True)[:40]

    report = {
        "timestamp": ts_str,
        "ts_iso": ts_iso, # High precision sort key
        "doc_id": doc_id,
        "intraday": intra_list,
        "swing": swing_list,
        "positional": pos_list
    }

    sync_to_firestore("stock_scans", "comprehensive", report)
    db.collection("stock_history").document(doc_id).set(report)

    # Cleanup logic
    one_week_ago = ts_now - timedelta(days=7)
    old_docs = db.collection("stock_history").where("ts_iso", "<", one_week_ago.isoformat()).stream()
    for old in old_docs: old.reference.delete()

    sync_to_firestore("stock_scans", "status", {
        "status": "Ready / Automated",
        "last_scan_time": ts_str,
        "results": f"Scan Complete: {len(intra_list)} targets found."
    })
    print(f"Scan Complete: {ts_str}")

if __name__ == "__main__":
    run_live_scan()
