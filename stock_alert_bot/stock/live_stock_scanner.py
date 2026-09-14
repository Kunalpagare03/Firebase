import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime
import os
from utils.firebase_sync import sync_to_firestore
from utils.news_engine import fetch_market_news

WATCHLIST = [
    "RELIANCE.NS", "HDFCBANK.NS", "ICICIBANK.NS", "INFY.NS", "TCS.NS", "SBIN.NS", "BHARTIARTL.NS", "AXISBANK.NS",
    "LICI.NS", "ITC.NS", "LT.NS", "KOTAKBANK.NS", "HINDUNILVR.NS", "TATAMOTORS.NS", "BAJFINANCE.NS", "ADANIENT.NS",
    "SUNPHARMA.NS", "MARUTI.NS", "ASIANPAINT.NS", "TITAN.NS", "ULTRACEMCO.NS", "JSWSTEEL.NS", "TATASTEEL.NS",
    "POWERGRID.NS", "NTPC.NS", "ONGC.NS", "ADANIPORTS.NS", "WIPRO.NS", "HCLTECH.NS", "M&M.NS", "BAJAJ-AUTO.NS",
    "COALINDIA.NS", "GRASIM.NS", "JSWENERGY.NS", "TRENT.NS", "BEL.NS", "HAL.NS", "DIXON.NS", "POLYCAB.NS",
    "PERSISTENT.NS", "LTIM.NS", "TATACONSUM.NS", "APOLLOHOSP.NS", "NESTLEIND.NS", "DRREDDY.NS", "CIPLA.NS",
    "TECHM.NS", "HINDALCO.NS", "BRITANNIA.NS", "EICHERMOT.NS", "INDUSINDBK.NS", "BPCL.NS", "SBILIFE.NS",
    "HDFCLIFE.NS", "HEROMOTOCO.NS", "TATACOMM.NS", "VOLTAS.NS", "CUMMINSIND.NS", "AUROPHARMA.NS", "LUPIN.NS"
]

def get_justification(strategy, change_pct, vol_ratio):
    reasons = []
    if abs(change_pct) > 1.5: reasons.append(f"Strong {'Price Momentum' if change_pct > 0 else 'Sell-off'} ({abs(change_pct)}%)")
    if vol_ratio > 2.0: reasons.append(f"High Institutional Activity ({vol_ratio}x Vol)")
    if "Retest" in strategy: reasons.append("Price bouncing from key EMA Support")
    if "Breakout" in strategy: reasons.append("Surpassing immediate resistance levels")
    return " | ".join(reasons) if reasons else "Aligned with intraday trend"

def analyze_intraday(symbol):
    try:
        ticker = yf.Ticker(symbol)
        df = ticker.history(period="2d", interval="5m")
        if df.empty or len(df) < 20: return None

        current_price = df['Close'].iloc[-1]
        prev_close = ticker.fast_info['previousClose']
        change_pct = ((current_price - prev_close) / prev_close) * 100

        df['ema9'] = df['Close'].ewm(span=9).mean()
        df['ema21'] = df['Close'].ewm(span=21).mean()
        df['avg_vol'] = df['Volume'].rolling(20).mean()

        latest_ema9 = df['ema9'].iloc[-1]
        latest_ema21 = df['ema21'].iloc[-1]
        latest_vol = df['Volume'].iloc[-1]
        avg_vol = df['avg_vol'].iloc[-1]
        vol_ratio = latest_vol / avg_vol if avg_vol > 0 else 0

        is_intraday = False
        strategy = ""

        if 1.0 <= change_pct <= 2.5 and current_price > latest_ema9 > latest_ema21 and vol_ratio > 1.5:
            is_intraday = True
            strategy = "Momentum Breakout"
        elif -2.5 <= change_pct <= -1.0 and current_price < latest_ema9 < latest_ema21 and vol_ratio > 1.5:
            is_intraday = True
            strategy = "Momentum Breakdown"
        elif abs(current_price - latest_ema21) / current_price < 0.002:
            is_intraday = True
            strategy = "EMA Retest"

        if not is_intraday: return None

        return {
            "symbol": symbol,
            "price": round(current_price, 2),
            "change": round(change_pct, 2),
            "strategy": strategy,
            "volume_ratio": round(vol_ratio, 1),
            "justification": get_justification(strategy, change_pct, vol_ratio),
            "recommendation": "BUY" if change_pct > 0 else "SELL",
            "target": round(current_price * (1.015 if change_pct > 0 else 0.985), 2),
            "stop": round(current_price * (0.993 if change_pct > 0 else 1.007), 2)
        }
    except: return None

def run_live_scan():
    intraday_results = []
    for symbol in WATCHLIST:
        res = analyze_intraday(symbol)
        if res: intraday_results.append(res)

    intraday_results.sort(key=lambda x: (x['volume_ratio'], abs(x['change'])), reverse=True)
    news = fetch_market_news()

    report = {
        "timestamp": datetime.now().strftime("%H:%M:%S"),
        "results": intraday_results[:15],
        "market_news": news
    }
    sync_to_firestore("stock_scans", "intraday", report)

if __name__ == "__main__":
    run_live_scan()
