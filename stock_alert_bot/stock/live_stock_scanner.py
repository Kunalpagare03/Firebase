import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime
import os
import concurrent.futures
from utils.firebase_sync import sync_to_firestore
from utils.news_engine import fetch_market_news
from utils.sector_map import get_sector, SECTOR_MAP
import time
import firebase_admin
from firebase_admin import credentials, firestore

# Comprehensive Watchlist (All 2,800+ stocks)
WATCHLIST = [sym for sector in SECTOR_MAP.values() for sym in sector]

def calculate_rsi(series, period=14):
    if len(series) < period + 1: return 50
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / loss if loss.iloc[-1] != 0 else 100
    return 100 - (100 / (1 + rs))

def analyze_stock(symbol):
    try:
        ticker = yf.Ticker(symbol)
        df = ticker.history(period="1y", interval="1d")
        if df.empty or len(df) < 200: return None, None, None

        curr = df.iloc[-1]
        entry = round(curr['Close'], 2)

        for p in [20, 50, 100, 200]:
            df[f'ema{p}'] = df['Close'].ewm(span=p, adjust=False).mean()

        df['rsi'] = calculate_rsi(df['Close'])
        prev = df.iloc[-2]
        high_52w = df['High'].max()

        change_pct = ((curr['Close'] - prev['Close']) / prev['Close']) * 100
        avg_vol = df['Volume'].rolling(20).mean().iloc[-1]
        vol_ratio = curr['Volume'] / avg_vol if avg_vol > 0 else 0

        # Institutional Rating
        score = 0
        if curr['Close'] > df['ema20'].iloc[-1]: score += 2
        if df['ema20'].iloc[-1] > df['ema50'].iloc[-1]: score += 1
        if vol_ratio > 2.0: score += 2
        if 40 < curr['rsi'] < 70: score += 2
        if curr['Close'] > high_52w * 0.98: score += 3

        rating = min(10, score)
        base_info = {
            "symbol": symbol, "sector": get_sector(symbol), "price": entry,
            "target_price": round(entry * 1.05, 2), "sl_price": round(entry * 0.97, 2),
            "change": round(change_pct, 2), "rating": f"{rating}/10", "vol_ratio": round(vol_ratio, 1)
        }

        intra = {**base_info, "recommendation": "INTRA BUY"} if change_pct > 0.5 and vol_ratio > 1.2 else None
        swing = {**base_info, "recommendation": "SWING ENTRY"} if rating >= 7 and curr['Close'] > df['ema20'].iloc[-1] else None
        pos = {**base_info, "recommendation": "POS HOLD"} if rating >= 8 and df['ema50'].iloc[-1] > df['ema200'].iloc[-1] else None

        return intra, swing, pos
    except: return None, None, None

def run_live_scan():
    start_time = time.time()

    # 1. Initialize Firestore for History Rotation
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "service-account.json")))
        firebase_admin.initialize_app(cred)
    db = firestore.client()

    sync_to_firestore("stock_scans", "status", {"status": "Cloud Scanning...", "start_time": datetime.now().strftime("%H:%M:%S")})

    print(f"Executing Global Scan: {len(WATCHLIST)} symbols...")
    intra_list, swing_list, pos_list = [], [], []

    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        futures = {executor.submit(analyze_stock, sym): sym for sym in WATCHLIST}
        for future in concurrent.futures.as_completed(futures):
            res = future.result()
            if res:
                i, s, p = res
                if i: intra_list.append(i)
                if s: swing_list.append(s)
                if p: pos_list.append(p)

    # Sort
    intra_list = sorted(intra_list, key=lambda x: int(x['rating'].split('/')[0]), reverse=True)
    swing_list = sorted(swing_list, key=lambda x: int(x['rating'].split('/')[0]), reverse=True)

    new_report = {
        "timestamp": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
        "intraday": intra_list, "swing": swing_list, "positional": pos_list
    }

    # 2. ROTATION LOGIC: Save last 3 scans
    try:
        current_latest = db.collection("stock_scans").document("comprehensive").get().to_dict()
        current_h1 = db.collection("stock_scans").document("history_1").get().to_dict()

        if current_h1:
            db.collection("stock_scans").document("history_2").set(current_h1)
        if current_latest:
            db.collection("stock_scans").document("history_1").set(current_latest)

        sync_to_firestore("stock_scans", "comprehensive", new_report)
        print("Rotation Complete: Latest, History_1, History_2 updated.")
    except Exception as e:
        print(f"Rotation failed: {e}")
        sync_to_firestore("stock_scans", "comprehensive", new_report)

    sync_to_firestore("stock_scans", "status", {
        "status": "Idle / Complete",
        "last_scan_time": new_report["timestamp"],
        "results": f"{len(intra_list)} Intra, {len(swing_list)} Swing"
    })
    print(f"Scan Finished: {new_report['timestamp']}")

if __name__ == "__main__":
    run_live_scan()
