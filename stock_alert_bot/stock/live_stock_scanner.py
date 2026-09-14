import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime
import os
from utils.firebase_sync import sync_to_firestore
from utils.news_engine import fetch_market_news
from utils.sector_map import get_sector, SECTOR_MAP

# Flatten symbols from sector map for scanning
WATCHLIST = [sym for sector in SECTOR_MAP.values() for sym in sector]

def calculate_rating(df, change_pct, vol_ratio, is_swing=False):
    score = 0
    # Price Trend
    if df['Close'].iloc[-1] > df['ema20'].iloc[-1]: score += 2
    if df['ema20'].iloc[-1] > df['ema50'].iloc[-1]: score += 1
    if df['ema50'].iloc[-1] > df['ema100'].iloc[-1]: score += 1
    if df['ema100'].iloc[-1] > df['ema200'].iloc[-1]: score += 1

    # Volume
    if vol_ratio > 2.0: score += 2
    elif vol_ratio > 1.5: score += 1

    # Momentum
    if abs(change_pct) > 1.5: score += 2
    elif abs(change_pct) > 0.8: score += 1

    # Retest/Bounce
    if abs(df['Close'].iloc[-1] - df['ema20'].iloc[-1]) / df['Close'].iloc[-1] < 0.005: score += 1

    return min(10, score)

def analyze_stock(symbol):
    try:
        ticker = yf.Ticker(symbol)
        # Fetch longer history for EMA 200 (need at least 250+ points)
        df_daily = ticker.history(period="1y", interval="1d")
        df_intraday = ticker.history(period="2d", interval="5m")

        if df_daily.empty or len(df_daily) < 200: return None

        # Daily Indicators for Swing
        for p in [20, 50, 100, 200]:
            df_daily[f'ema{p}'] = df_daily['Close'].ewm(span=p).mean()

        # Intraday Indicators
        if not df_intraday.empty:
            for p in [20, 50]:
                df_intraday[f'ema{p}'] = df_intraday['Close'].ewm(span=p).mean()

        current_price = df_daily['Close'].iloc[-1]
        prev_close = df_daily['Close'].iloc[-2]
        change_pct = ((current_price - prev_close) / prev_close) * 100

        avg_vol = df_daily['Volume'].rolling(20).mean().iloc[-1]
        curr_vol = df_daily['Volume'].iloc[-1]
        vol_ratio = curr_vol / avg_vol if avg_vol > 0 else 0

        rating = calculate_rating(df_daily, change_pct, vol_ratio)
        sector = get_sector(symbol)

        base_info = {
            "symbol": symbol,
            "sector": sector,
            "price": round(current_price, 2),
            "change": round(change_pct, 2),
            "rating": f"{rating}/10",
            "vol_ratio": round(vol_ratio, 1),
            "ema20": round(df_daily['ema20'].iloc[-1], 2),
            "ema50": round(df_daily['ema50'].iloc[-1], 2),
            "ema200": round(df_daily['ema200'].iloc[-1], 2),
        }

        # Logic for Intraday (1-2% move + vol)
        intraday_data = None
        if abs(change_pct) >= 1.0 and vol_ratio > 1.2:
            intraday_data = {**base_info, "type": "INTRADAY", "recommendation": "BUY TODAY" if change_pct > 0 else "SELL TODAY"}

        # Logic for Swing (EMA alignment)
        swing_data = None
        if current_price > df_daily['ema20'].iloc[-1] > df_daily['ema50'].iloc[-1] and rating >= 7:
            swing_data = {**base_info, "type": "SWING", "recommendation": "BUY NEXT DAY"}

        return intraday_data, swing_data
    except Exception as e:
        return None, None

def run_live_scan():
    intraday_results = []
    swing_results = []

    for symbol in WATCHLIST:
        intra, swing = analyze_stock(symbol)
        if intra: intraday_results.append(intra)
        if swing: swing_results.append(swing)

    news = fetch_market_news()

    report = {
        "timestamp": datetime.now().strftime("%H:%M:%S"),
        "intraday": intraday_results,
        "swing": swing_results,
        "market_news": news
    }
    sync_to_firestore("stock_scans", "comprehensive", report)

if __name__ == "__main__":
    run_live_scan()
