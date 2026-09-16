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
        # Use 1y period for EMA 200 stability
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

        # --- HIGH QUALITY FILTER ---
        # 1. Only show high-score stocks (8/10 or better)
        # 2. This applies to ALL stocks, including those below 30 Rupees.
        if rating < 8: return None, None, None
        # ---------------------------

        # Target/SL
        target = round(entry * 1.05, 2)
        sl = round(entry * 0.97, 2)

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
            "justification": f"{'Bullish' if change_pct > 0 else 'Bearish'} momentum with {round(vol_ratio, 1)}x vol. RSI {round(curr['rsi'], 1)}.",
            "ema20": round(df['ema20'].iloc[-1], 2),
            "ema200": round(df['ema200'].iloc[-1], 2),
            "strategy": "Breakout" if curr['Close'] > high_52w * 0.95 else "Trend Follow"
        }

        # Filtering Logic
        intra = {**base_info, "recommendation": "INTRA BUY"} if change_pct > 1.5 and vol_ratio > 1.5 else None
        swing = {**base_info, "recommendation": "SWING ENTRY"} if curr['Close'] > df['ema20'].iloc[-1] else None
        pos = {**base_info, "recommendation": "POS HOLD"} if df['ema50'].iloc[-1] > df['ema200'].iloc[-1] else None

        return intra, swing, pos
    except:
        return None, None, None

def run_live_scan():
    intra_list, swing_list, pos_list = [], [], []
    start_time = time.time()

    print(f"Executing Global Terminal Scan: {len(WATCHLIST)} symbols...")

    # Increased workers to 20 for 3000+ stocks
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        future_to_stock = {executor.submit(analyze_stock, sym): sym for sym in WATCHLIST}
        count = 0
        for future in concurrent.futures.as_completed(future_to_stock):
            count += 1
            if count % 100 == 0:
                elapsed = time.time() - start_time
                print(f"Scanned {count}/{len(WATCHLIST)} stocks... ({int(elapsed)}s)")

            res = future.result()
            if res:
                i, s, p = res
                if i: intra_list.append(i)
                if s: swing_list.append(s)
                if p: pos_list.append(p)

    duration = time.time() - start_time
    print(f"Scan complete in {int(duration/60)}m. Found {len(intra_list)} Intra, {len(swing_list)} Swing.")

    report = {
        "timestamp": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
        "intraday": intra_list,
        "swing": swing_list,
        "positional": pos_list,
        "market_news": fetch_market_news()
    }

    sync_to_firestore("stock_scans", "comprehensive", report)
    print("Cloud Terminal Sync Complete.")

if __name__ == "__main__":
    run_live_scan()
