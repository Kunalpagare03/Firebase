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

# Global Settings
WATCHLIST = [sym for sector in SECTOR_MAP.values() for sym in sector]
# Limit to 500 top stocks if we are being rate-limited, but user wants all 2800.
# We will use fewer workers to stay under the radar.

def calculate_rsi(series, period=14):
    if len(series) < period + 1: return 50
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / loss
    return 100 - (100 / (1 + rs.iloc[-1])) if not rs.empty and rs.iloc[-1] != 0 else 50

def analyze_stock(symbol):
    try:
        # Optimization: Use 1mo period for faster scanning on large watchlists
        ticker = yf.Ticker(symbol)
        df = ticker.history(period="6mo", interval="1d")
        if df.empty or len(df) < 20: return None, None, None

        curr = df.iloc[-1]
        prev = df.iloc[-2]

        # Core Technicals
        df['ema20'] = df['Close'].ewm(span=20, adjust=False).mean()
        df['ema50'] = df['Close'].ewm(span=50, adjust=False).mean()

        # Momentum
        change_pct = ((curr['Close'] - prev['Close']) / prev['Close']) * 100
        avg_vol = df['Volume'].tail(20).mean()
        vol_ratio = curr['Volume'] / avg_vol if avg_vol > 0 else 0

        # Rating Logic
        score = 0
        if curr['Close'] > df['ema20'].iloc[-1]: score += 2
        if change_pct > 0: score += 2
        if vol_ratio > 1.1: score += 2
        if curr['Close'] > df['ema50'].iloc[-1]: score += 2

        rating = min(10, score)

        base_info = {
            "symbol": symbol,
            "sector": get_sector(symbol),
            "price": round(curr['Close'], 2),
            "entry_price": round(curr['Close'], 2),
            "target_price": round(curr['Close'] * 1.05, 2),
            "sl_price": round(curr['Close'] * 0.97, 2),
            "change": round(change_pct, 2),
            "rating": f"{rating}/10",
            "vol_ratio": round(vol_ratio, 1),
            "justification": f"Stock showing {round(change_pct, 2)}% move with {round(vol_ratio, 1)}x volume."
        }

        # Filtering (Very relaxed to ensure output)
        intra = {**base_info, "recommendation": "INTRA BUY"} if change_pct > 0.05 and vol_ratio > 0.5 else None
        swing = {**base_info, "recommendation": "SWING ENTRY"} if rating >= 4 and curr['Close'] > df['ema20'].iloc[-1] else None
        pos = {**base_info, "recommendation": "POS HOLD"} if rating >= 6 else None

        return intra, swing, pos
    except:
        return None, None, None

def run_live_scan():
    start_time = time.time()

    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "service-account.json")))
        firebase_admin.initialize_app(cred)
    db = firestore.client()

    sync_to_firestore("stock_scans", "status", {"status": "Scanning 2,800 Stocks...", "start_time": datetime.now().strftime("%H:%M:%S")})

    intra_list, swing_list, pos_list = [], [], []

    # Use 8 workers to avoid 'Too Many Requests' error from Yahoo
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
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

    # Sort
    intra_list = sorted(intra_list, key=lambda x: float(x['change']), reverse=True)[:50]
    swing_list = sorted(swing_list, key=lambda x: int(x['rating'].split('/')[0]), reverse=True)[:50]
    pos_list = sorted(pos_list, key=lambda x: int(x['rating'].split('/')[0]), reverse=True)[:50]

    report = {
        "timestamp": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
        "intraday": intra_list,
        "swing": swing_list,
        "positional": pos_list
    }

    # Rotation
    try:
        cur_latest = db.collection("stock_scans").document("comprehensive").get().to_dict()
        cur_h1 = db.collection("stock_scans").document("history_1").get().to_dict()
        if cur_h1: db.collection("stock_scans").document("history_2").set(cur_h1)
        if cur_latest: db.collection("stock_scans").document("history_1").set(cur_latest)
    except: pass

    sync_to_firestore("stock_scans", "comprehensive", report)
    sync_to_firestore("stock_scans", "status", {
        "status": "Idle / Complete",
        "last_scan_time": report["timestamp"],
        "results": f"{len(intra_list)} Intra, {len(swing_list)} Swing"
    })
    print(f"Scan complete: Found {len(intra_list)} stocks.")

if __name__ == "__main__":
    run_live_scan()
