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

# Comprehensive Watchlist from Sector Map (Improved matching found 3,034 stocks)
WATCHLIST = [sym for sector in SECTOR_MAP.values() for sym in sector]

def calculate_rsi(series, period=14):
    if len(series) < period + 1: return 50
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / loss
    return 100 - (100 / (1 + rs))

def analyze_stock(symbol):
    try:
        ticker = yf.Ticker(symbol)
        df = ticker.history(period="1y", interval="1d")
        if df.empty or len(df) < 200: return None, None, None

        curr = df.iloc[-1]
        entry = round(curr['Close'], 2)

        # Technical Indicators
        for p in [20, 50, 100, 200]:
            df[f'ema{p}'] = df['Close'].ewm(span=p, adjust=False).mean()

        df['rsi'] = calculate_rsi(df['Close'])

        prev = df.iloc[-2]
        high_52w = df['High'].max()

        change_pct = ((curr['Close'] - prev['Close']) / prev['Close']) * 100
        avg_vol = df['Volume'].rolling(20).mean().iloc[-1]
        vol_ratio = curr['Volume'] / avg_vol if avg_vol > 0 else 0

        # Rating Logic (StockEdge Style)
        score = 0
        if curr['Close'] > df['ema20'].iloc[-1]: score += 2
        if df['ema20'].iloc[-1] > df['ema50'].iloc[-1]: score += 1
        if vol_ratio > 2.0: score += 2
        if 40 < curr['rsi'] < 70: score += 2
        if curr['Close'] > high_52w * 0.98: score += 3 # Near 52w High

        rating = min(10, score)

        # Target/SL
        target = round(entry * 1.03, 2)
        sl = round(entry * 0.98, 2)

        base_info = {
            "symbol": symbol,
            "sector": get_sector(symbol),
            "price": entry,
            "entry_price": entry,
            "target_price": target,
            "sl_price": sl,
            "change": round(change_pct, 2),
            "rating": f"{rating}/10",
            "rsi": round(curr['rsi'], 1),
            "vol_ratio": round(vol_ratio, 1),
            "justification": f"{'Bullish' if change_pct > 0 else 'Bearish'} Momentum with {round(vol_ratio, 1)}x Vol spike. RSI at {round(curr['rsi'], 1)}.",
            "ema20": round(df['ema20'].iloc[-1], 2),
            "ema200": round(df['ema200'].iloc[-1], 2),
            "strategy": "Breakout" if curr['Close'] > high_52w * 0.95 else "Trend Following"
        }

        # Filtering Logic (Restored to Stable State)
        intra = {**base_info, "recommendation": "INTRA-DAY BUY"} if change_pct > 1.0 and vol_ratio > 1.2 else None
        swing = {**base_info, "recommendation": "SWING ACCUMULATE"} if rating >= 7 and curr['Close'] > df['ema20'].iloc[-1] else None
        pos = {**base_info, "recommendation": "LONG TERM HOLD"} if rating >= 8 and df['ema50'].iloc[-1] > df['ema200'].iloc[-1] else None

        return intra, swing, pos
    except: return None, None, None

def run_live_scan():
    intra_list, swing_list, pos_list = [], [], []
    start_time = time.time()

    # --- HEARTBEAT FOR MANUAL TRIGGER ---
    sync_to_firestore("stock_scans", "status", {
        "status": "Scanning...",
        "start_time": datetime.now().strftime("%H:%M:%S"),
        "progress": "0%"
    })
    # ------------------------------------

    print(f"Executing Global Terminal Scan: {len(WATCHLIST)} symbols...")

    # Using 10 workers for stability
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        future_to_stock = {executor.submit(analyze_stock, sym): sym for sym in WATCHLIST}
        count = 0
        for future in concurrent.futures.as_completed(future_to_stock):
            count += 1
            if count % 100 == 0:
                print(f"Scanned {count}/{len(WATCHLIST)} stocks...")

            try:
                res = future.result()
                if res:
                    i, s, p = res
                    if i: intra_list.append(i)
                    if s: swing_list.append(s)
                    if p: pos_list.append(p)
            except: pass

    # Sort results by rating (Highest first)
    intra_list = sorted(intra_list, key=lambda x: int(x['rating'].split('/')[0]), reverse=True)
    swing_list = sorted(swing_list, key=lambda x: int(x['rating'].split('/')[0]), reverse=True)

    report = {
        "timestamp": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
        "intraday": intra_list,
        "swing": swing_list,
        "positional": pos_list,
        "market_news": fetch_market_news()
    }

    sync_to_firestore("stock_scans", "comprehensive", report)

    # --- UPDATE STATUS ---
    sync_to_firestore("stock_scans", "status", {
        "status": "Idle / Complete",
        "last_scan_time": report["timestamp"],
        "results": f"{len(intra_list)} Intra, {len(swing_list)} Swing"
    })
    print(f"Terminal Sync Complete at {report['timestamp']}.")

if __name__ == "__main__":
    run_live_scan()
