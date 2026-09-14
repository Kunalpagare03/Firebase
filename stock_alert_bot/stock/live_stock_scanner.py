import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime
import os
from utils.firebase_sync import sync_to_firestore
from utils.news_engine import fetch_market_news
from utils.sector_map import get_sector, SECTOR_MAP

# Comprehensive Watchlist from Sector Map
WATCHLIST = [sym for sector in SECTOR_MAP.values() for sym in sector]

def calculate_rating(df, change_pct, vol_ratio):
    score = 0
    close = df['Close'].iloc[-1]

    # 1. EMA Trend Strength (Max 4 points)
    if close > df['ema20'].iloc[-1]: score += 1
    if df['ema20'].iloc[-1] > df['ema50'].iloc[-1]: score += 1
    if df['ema50'].iloc[-1] > df['ema100'].iloc[-1]: score += 1
    if df['ema100'].iloc[-1] > df['ema200'].iloc[-1]: score += 1

    # 2. Institutional Volume (Max 2 points)
    if vol_ratio > 2.0: score += 2
    elif vol_ratio > 1.5: score += 1

    # 3. Momentum (Max 2 points)
    if abs(change_pct) > 2.0: score += 2
    elif abs(change_pct) > 1.0: score += 1

    # 4. Candlestick/Structure (Max 2 points)
    # Near support bounce (EMA 20 or 50)
    if abs(close - df['ema20'].iloc[-1]) / close < 0.006 or abs(close - df['ema50'].iloc[-1]) / close < 0.006:
        score += 1
    # Bullish close
    if close > df['Open'].iloc[-1]: score += 1

    return min(10, score)

def get_justification(strategy, rating, vol_ratio, change_pct):
    reasons = []
    if rating >= 9: reasons.append("Institutional Standard (10/10 Setup)")
    if vol_ratio > 2.5: reasons.append("Massive Volume Spike (Smart Money)")
    if change_pct > 2.0: reasons.append("Strong Trend Expansion")
    if "Retest" in strategy: reasons.append("Successful Support Retest / Bounce")
    return " | ".join(reasons) if reasons else "Trend following momentum"

def analyze_stock(symbol):
    try:
        ticker = yf.Ticker(symbol)
        # Need 1 year for EMA 200
        df = ticker.history(period="1y", interval="1d")
        if df.empty or len(df) < 200: return None, None, None

        # Indicators
        for p in [20, 50, 100, 200]:
            df[f'ema{p}'] = df['Close'].ewm(span=p, adjust=False).mean()

        current_price = df['Close'].iloc[-1]
        prev_close = df['Close'].iloc[-2]
        change_pct = ((current_price - prev_close) / prev_close) * 100

        avg_vol = df['Volume'].rolling(20).mean().iloc[-1]
        vol_ratio = df['Volume'].iloc[-1] / avg_vol if avg_vol > 0 else 0

        rating = calculate_rating(df, change_pct, vol_ratio)
        sector = get_sector(symbol)

        strategy = "Trend Alignment"
        if abs(current_price - df['ema20'].iloc[-1]) / current_price < 0.005: strategy = "EMA 20 Retest"
        elif change_pct > 1.5 and vol_ratio > 1.5: strategy = "High Vol Breakout"

        target_price = round(current_price * (1.02 if change_pct > 0 else 0.98), 2)
        stop_loss = round(current_price * (0.99 if change_pct > 0 else 1.01), 2)
        sell_price = target_price # For intraday, sell is the target

        base_info = {
            "symbol": symbol,
            "sector": sector,
            "price": round(current_price, 2),
            "entry_price": round(current_price, 2),
            "target_price": target_price,
            "sell_price": sell_price,
            "sl_price": stop_loss,
            "change": round(change_pct, 2),
            "rating": f"{rating}/10",
            "vol_ratio": round(vol_ratio, 1),
            "justification": get_justification(strategy, rating, vol_ratio, change_pct),
            "ema20": round(df['ema20'].iloc[-1], 2),
            "ema50": round(df['ema50'].iloc[-1], 2),
            "ema100": round(df['ema100'].iloc[-1], 2),
            "ema200": round(df['ema200'].iloc[-1], 2),
            "strategy": strategy
        }

        # 1. Intraday Logic (Buy/Sell Today)
        intra = {**base_info, "recommendation": "TRADE TODAY"} if abs(change_pct) >= 1.0 and vol_ratio > 1.2 else None

        # 2. Swing Logic (Short term)
        swing = {**base_info, "recommendation": "SWING ENTRY"} if rating >= 7 and current_price > df['ema20'].iloc[-1] else None

        # 3. Positional Logic (Long term trend)
        pos = {**base_info, "recommendation": "POS HOLD"} if rating >= 8 and df['ema20'].iloc[-1] > df['ema50'].iloc[-1] > df['ema200'].iloc[-1] else None

        return intra, swing, pos
    except:
        return None, None, None

def build_sectioned_report(report_data):
    sections = {}
    sector_sections = {}
    stocks_by_sector = {}

    for section_name in ["swing", "positional"]:
        section_items = []
        for item in report_data.get(section_name, []) or []:
            item_copy = dict(item)
            item_copy["section"] = section_name
            section_items.append(item_copy)
        if section_items:
            sections[section_name] = section_items

    all_stocks = []
    for section_name, items in sections.items():
        for item in items:
            sector = item.get("sector") or get_sector(item.get("symbol", "")) or "OTHERS"
            section_bucket = sector_sections.setdefault(sector, {
                "sector": sector,
                "count": 0,
                "stocks": []
            })
            section_bucket["count"] += 1
            section_bucket["stocks"].append(item)

            stocks_by_sector.setdefault(sector, []).append(item)
            all_stocks.append(item)

    return {
        "timestamp": report_data.get("timestamp"),
        "swing": sections.get("swing", []),
        "positional": sections.get("positional", []),
        "sections": sections,
        "sector_sections": sector_sections,
        "stocks_by_sector": stocks_by_sector,
        "market_news": report_data.get("market_news", []) or [],
        "stocks": all_stocks,
    }


def run_live_scan():
    swing_list = []
    positional_list = []

    print(f"Executing Global Terminal Scan: {len(WATCHLIST)} symbols...")
    for symbol in WATCHLIST:
        _, swing, pos = analyze_stock(symbol)
        if swing: swing_list.append(swing)
        if pos: positional_list.append(pos)

    news = fetch_market_news()

    report = {
        "timestamp": datetime.now().strftime("%H:%M:%S"),
        "swing": swing_list,
        "positional": positional_list,
        "market_news": news
    }

    sectioned_report = build_sectioned_report(report)
    sync_to_firestore("stock_scans", "comprehensive", sectioned_report)
    print("Cloud Terminal Sync Complete.")

if __name__ == "__main__":
    run_live_scan()
