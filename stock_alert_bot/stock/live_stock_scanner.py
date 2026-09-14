import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime
import os
from utils.firebase_sync import sync_to_firestore
from utils.news_engine import fetch_market_news
from utils.sector_map import get_sector, SECTOR_MAP

# Comprehensive Watchlist (NSE 200 + Top BSE)
WATCHLIST = [sym for sector in SECTOR_MAP.values() for sym in sector]

def calculate_rating(df, change_pct, vol_ratio):
    score = 0
    close = df['Close'].iloc[-1]

    # 1. EMA Alignment (Trend Strength)
    # 10/10 Rating logic based on EMA 20, 50, 100, 200
    if close > df['ema20'].iloc[-1]: score += 1
    if df['ema20'].iloc[-1] > df['ema50'].iloc[-1]: score += 1
    if df['ema50'].iloc[-1] > df['ema100'].iloc[-1]: score += 1
    if df['ema100'].iloc[-1] > df['ema200'].iloc[-1]: score += 1

    # 2. Price Action & Volume
    if vol_ratio > 2.0: score += 2
    elif vol_ratio > 1.5: score += 1

    if abs(change_pct) > 2.0: score += 2
    elif abs(change_pct) > 1.0: score += 1

    # 3. Candlestick & Support
    # Near support bounce
    if abs(close - df['ema20'].iloc[-1]) / close < 0.005: score += 1
    # Bullish candle (Close > Open)
    if df['Close'].iloc[-1] > df['Open'].iloc[-1]: score += 1

    return min(10, score)

def get_justification(s, rating, vol_ratio, change_pct):
    reasons = []
    if rating >= 9: reasons.append("Perfect Bullish Structure (10/10 Setup)")
    if vol_ratio > 2.0: reasons.append("Institutional Buying Spike")
    if change_pct > 1.5: reasons.append("Strong Price Momentum")
    if "Retest" in s: reasons.append("Successful Support Retest")
    return " | ".join(reasons) if reasons else "Trend continuation"

def analyze_stock(symbol):
    try:
        ticker = yf.Ticker(symbol)
        # Fetch 1 year for EMA 200
        df_daily = ticker.history(period="1y", interval="1d")
        if df_daily.empty or len(df_daily) < 200: return None, None

        # Calculate all 4 EMAs
        for p in [20, 50, 100, 200]:
            df_daily[f'ema{p}'] = df_daily['Close'].ewm(span=p, adjust=False).mean()

        current_price = df_daily['Close'].iloc[-1]
        prev_close = df_daily['Close'].iloc[-2]
        change_pct = ((current_price - prev_close) / prev_close) * 100

        avg_vol = df_daily['Volume'].rolling(20).mean().iloc[-1]
        vol_ratio = df_daily['Volume'].iloc[-1] / avg_vol if avg_vol > 0 else 0

        rating = calculate_rating(df_daily, change_pct, vol_ratio)
        sector = get_sector(symbol)

        strategy = "Trend Following"
        if abs(current_price - df_daily['ema20'].iloc[-1]) / current_price < 0.005: strategy = "EMA Retest"
        elif change_pct > 1.5 and vol_ratio > 1.5: strategy = "Momentum Breakout"

        base_info = {
            "symbol": symbol,
            "sector": sector,
            "price": round(current_price, 2),
            "change": round(change_pct, 2),
            "rating": f"{rating}/10",
            "vol_ratio": round(vol_ratio, 1),
            "justification": get_justification(strategy, rating, vol_ratio, change_pct),
            "ema20": round(df_daily['ema20'].iloc[-1], 2),
            "ema50": round(df_daily['ema50'].iloc[-1], 2),
            "ema200": round(df_daily['ema200'].iloc[-1], 2),
            "strategy": strategy
        }

        # Intraday: 1% move + Volume
        intra = {**base_info, "recommendation": "BUY TODAY"} if change_pct >= 1.0 and vol_ratio > 1.2 else None
        # Swing: Rating 7+ and above 20 EMA
        swing = {**base_info, "recommendation": "BUY NEXT DAY"} if rating >= 7 and current_price > df_daily['ema20'].iloc[-1] else None

        return intra, swing
    except:
        return None, None

def run_live_scan():
    intraday_list = []
    swing_list = []

    print(f"Scanning {len(WATCHLIST)} stocks across NSE/BSE...")
    for symbol in WATCHLIST:
        intra, swing = analyze_stock(symbol)
        if intra: intraday_list.append(intra)
        if swing: swing_list.append(swing)

    news = fetch_market_news()

    report = {
        "timestamp": datetime.now().strftime("%H:%M:%S"),
        "intraday": intraday_list,
        "swing": swing_list,
        "market_news": news
    }
    sync_to_firestore("stock_scans", "comprehensive", report)
    print("Comprehensive scan completed and synced.")

if __name__ == "__main__":
    run_live_scan()
