import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import os
import concurrent.futures
from utils.firebase_sync import sync_to_firestore
from utils.news_engine import fetch_market_news
from utils.sector_map import get_sector, SECTOR_MAP
import time
import firebase_admin
from firebase_admin import credentials, firestore

# Institutional Watchlist (2,800+ stocks)
WATCHLIST = [sym for sector in SECTOR_MAP.values() for sym in sector]

def calculate_rsi(series, period=14):
    if len(series) < period + 1: return 50
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / loss
    return 100 - (100 / (1 + rs.iloc[-1])) if not rs.empty and rs.iloc[-1] != 0 else 50

def analyze_stock(symbol):
    try:
        ticker = yf.Ticker(symbol)
        df = ticker.history(period="6mo", interval="1d")
        if df.empty or len(df) < 50: return None, None, None

        curr = df.iloc[-1]
        prev = df.iloc[-2]

        # Technical Indicators
        df['ema20'] = df['Close'].ewm(span=20, adjust=False).mean()
        df['ema50'] = df['Close'].ewm(span=50, adjust=False).mean()
        df['ema200'] = df['Close'].ewm(span=200, adjust=False).mean()
        rsi_val = calculate_rsi(df['Close'])

        change_pct = ((curr['Close'] - prev['Close']) / prev['Close']) * 100
        avg_vol = df['Volume'].tail(20).mean()
        vol_ratio = curr['Volume'] / avg_vol if avg_vol > 0 else 0

        # --- Institutional Conviction Logic ---
        score = 0
        if curr['Close'] > df['ema20'].iloc[-1]: score += 2
        if curr['Close'] > df['ema200'].iloc[-1]: score += 2
        if vol_ratio > 1.5: score += 2
        if rsi_val > 60: score += 2
        if change_pct > 1.0: score += 2

        rating = min(10, score)
        conviction = "High" if rating >= 8 else "Medium" if rating >= 5 else "Normal"

        # Pattern Detection
        pattern = "Consolidating"
        if curr['Close'] > df['High'].tail(10).max() * 0.99: pattern = "Breakout Zone"
        if rsi_val > 70 and vol_ratio > 2: pattern = "Bullish Blast"

        base_info = {
            "symbol": symbol,
            "sector": get_sector(symbol),
            "price": round(curr['Close'], 2),
            "target_price": round(curr['Close'] * 1.06, 2),
            "sl_price": round(curr['Close'] * 0.96, 2),
            "change": round(change_pct, 2),
            "rating": f"{rating}/10",
            "conviction": conviction,
            "pattern": pattern,
            "vol_ratio": round(vol_ratio, 1),
            "justification": f"{conviction} conviction: {pattern}. Vol spike {round(vol_ratio,1)}x with RSI {round(rsi_val,1)}."
        }

        # --- Refined Distinct Filtering Logic (v89.3) ---

        # 1. Intraday: Today's Vol Spike + Momentum
        intra = None
        if rating >= 7 and vol_ratio > 1.8 and change_pct > 1.2:
            intra = {**base_info, "recommendation": "INTRA MOMENTUM"}

        # 2. Swing (1 Day to 1 Week): Breaking 10-day highs + 20-EMA support
        swing = None
        ten_day_high = df['High'].tail(10).max()
        if curr['Close'] > df['ema20'].iloc[-1] and curr['Close'] >= ten_day_high * 0.98 and rating >= 6:
            swing = {**base_info, "recommendation": "SWING BUY"}

        # 3. Positional (1 Week to 1 Month+): Structural Strength (50 > 200 EMA)
        pos = None
        if df['ema50'].iloc[-1] > df['ema200'].iloc[-1] and curr['Close'] > df['ema50'].iloc[-1] and rating >= 7:
            pos = {**base_info, "recommendation": "POS ACCUMULATE"}

        return intra, swing, pos
    except:
        return None, None, None

def run_live_scan():
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "service-account.json")))
        firebase_admin.initialize_app(cred)
    db = firestore.client()

    ts_now = datetime.now()
    ts_str = ts_now.strftime("%d-%m-%Y %H:%M:%S")
    doc_id = ts_now.strftime("%Y%m%d_%H%M")

    sync_to_firestore("stock_scans", "status", {"status": "Full Week Sync Active...", "start_time": ts_str})

    print(f"Executing Global Scan: {len(WATCHLIST)} symbols...")
    intra_list, swing_list, pos_list = [], [], []

    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
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

    # --- UNIQUE SELECTION & DE-DUPLICATION (v89.5) ---

    # 1. Intraday: Top 40 by Volume Spike
    intra_list = sorted(intra_list, key=lambda x: (x['vol_ratio'], x['change']), reverse=True)[:40]
    intra_symbols = {s['symbol'] for s in intra_list}

    # 2. Swing: Top 40 by Breakout (Exclude Intraday picks)
    swing_filtered = [s for s in swing_list if s['symbol'] not in intra_symbols]
    swing_list = sorted(swing_filtered, key=lambda x: (x['change'], x['vol_ratio']), reverse=True)[:40]
    swing_symbols = {s['symbol'] for s in swing_list}

    # 3. Positional: Top 40 by Rating (Exclude Intraday & Swing picks)
    pos_filtered = [s for s in pos_list if s['symbol'] not in intra_symbols and s['symbol'] not in swing_symbols]
    pos_list = sorted(pos_filtered, key=lambda x: (int(x['rating'].split('/')[0]), x['vol_ratio']), reverse=True)[:40]

    report = {
        "timestamp": ts_str,
        "doc_id": doc_id,
        "intraday": intra_list,
        "swing": swing_list,
        "positional": pos_list
    }

    # --- WEEKLY HISTORY RETENTION ---
    # 1. Save Current to Main
    sync_to_firestore("stock_scans", "comprehensive", report)
    # 2. Save Current to History Archive
    db.collection("stock_history").document(doc_id).set(report)

    # 3. Clean up older than 7 days
    one_week_ago = ts_now - timedelta(days=7)
    old_docs = db.collection("stock_history").where("timestamp", "<", one_week_ago.strftime("%d-%m-%Y %H:%M:%S")).stream()
    for old in old_docs:
        old.reference.delete()
    # --------------------------------

    sync_to_firestore("stock_scans", "status", {
        "status": "Idle / Ready",
        "last_scan_time": ts_str,
        "results": f"{len(intra_list)} High-Conviction saved to 7-Day History"
    })
    print(f"Weekly History Scan Complete: {ts_str}")

if __name__ == "__main__":
    run_live_scan()
