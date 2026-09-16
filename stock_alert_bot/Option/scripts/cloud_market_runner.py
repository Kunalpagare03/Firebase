import time
import os
import sys
import logging
import threading
import json
import numpy as np
import pandas as pd
from datetime import datetime
from collections import deque, defaultdict

# Add project roots to path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard, json_safe
from step9_deep_dive_analysis import perform_deep_dive
from utils.firebase_sync import sync_to_firestore
from brokers.angel_one import start_live_feed, get_latest_spot, get_live_status
from stock_alert.market_hours import market_status
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger("CloudTerminal")

# Persistent memory for history & charts
_chart_history = defaultdict(lambda: deque(maxlen=2000))
_last_chain_refresh = defaultdict(int)
_cached_chain = {}
_cached_df_prev = {}

def calculate_technicals(prices):
    if len(prices) < 14:
        return {"rsi": "-", "macd": "-", "trendline": "N/A", "candle": "N/A"}

    # RSI calculation
    deltas = np.diff(prices)
    gain = np.where(deltas > 0, deltas, 0)
    loss = np.where(deltas < 0, -deltas, 0)
    avg_gain = np.mean(gain[-14:])
    avg_loss = np.mean(loss[-14:])
    if avg_loss == 0: rsi = 100
    else:
        rs = avg_gain / avg_loss
        rsi = 100 - (100 / (1 + rs))

    # MACD (Simplified)
    ema12 = pd.Series(prices).ewm(span=12).mean().iloc[-1]
    ema26 = pd.Series(prices).ewm(span=26).mean().iloc[-1]
    macd = ema12 - ema26

    # Trend & Candle
    slope = (prices[-1] - prices[0]) / len(prices)
    trend = "Bullish" if slope > 0 else "Bearish"
    candle = "Bullish" if prices[-1] > prices[-2] else "Bearish"

    return {"rsi": round(rsi, 1), "macd": round(macd, 2), "trendline": trend, "candle": candle}

def _get_trade_decision(data, tech):
    # Matches the screenshot "Trend using price + OI buildup"
    sig = data.get('final_signal', 'Neutral')
    oi_build = data.get('oi_buildup_overall', 'Neutral')

    if sig == 'Bullish' and tech['trendline'] == 'Bullish':
        return f"Bullish confirmation: Price holding above support with strong OI build."
    elif sig == 'Bearish' and tech['trendline'] == 'Bearish':
        return f"Bearish confirmation: Resistance BUILDING. Target lower levels."
    return f"Wait for clean break. Price and OI are mixed."

def sync_live_quote(symbol, spot):
    if spot:
        sync_to_firestore("live_quotes", symbol, {
            "symbol": symbol,
            "spot_price": float(spot),
            "updated_at": datetime.now().strftime("%H:%M:%S.%f")[:-3]
        })

def build_full_analysis(symbol):
    try:
        now = time.time()
        live_spot = get_latest_spot(symbol)

        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})
            sync_live_quote(symbol, live_spot)

        # 2. NSE Chain Refresh
        if now - _last_chain_refresh[symbol] > 120 or symbol not in _cached_chain:
            try:
                raw = get_option_chain(symbol)
                df, spot_chain = parse_option_chain(raw)
                _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
                _cached_chain[symbol] = df
                _last_chain_refresh[symbol] = now
                log.info(f"NSE Sync: {symbol}")
            except Exception as e:
                log.error(f"NSE Error: {e}")

        if symbol in _cached_chain:
            df = _cached_chain[symbol]
            df_prev = _cached_df_prev.get(symbol, df)
            prices = [s['value'] for s in _chart_history[symbol]]
            spot = live_spot if live_spot else prices[-1] if prices else 23400

            # 1. Base Dashboard
            data = build_dashboard(df_prev, df, spot)

            # 2. Technicals
            tech = calculate_technicals(prices)

            # 3. Enhanced Trade Alert (Matches Screenshot RSI/MACD row)
            data["trade_alert"] = {
                "action": data.get("final_signal", "WAIT"),
                "trigger": "Wait for break of levels" if data.get("final_signal") == "Neutral" else f"Trigger: {data.get('support_zones',[0])[0]}",
                "note": f"Confirmations active. Data analyzed from {len(prices)} ticks.",
                "technicals": tech
            }

            # 4. Fill Missing Screenshot Fields
            data["decision_note"] = _get_trade_decision(data, tech)
            data["oi_trend"] = "Strong" if abs(data.get("pcr_oi", 1) - 1) > 0.2 else "Moderate"
            data["market_read"] = f"{data.get('final_signal')} Flow Detected"

            # 5. Overlays
            data["greeks"] = json_safe(analyze_chain_greeks(df, spot))
            data["price_action"] = json_safe(analyze_price_action(symbol))
            data["price_structure"] = analyze_price_structure(prices)
            data["deep_dive"] = json_safe(perform_deep_dive(df, spot))
            data["spot_history"] = list(_chart_history[symbol])
            data["fetched_at"] = datetime.now().strftime("%H:%M:%S")
            data["market_status_str"] = f"OPEN | LIVE FEED | {data['fetched_at']}"

            sync_to_firestore("option_sentiment", symbol, json_safe(data))
            log.info(f"PRO HUB v47 Pushed: {symbol}")

    except Exception as e:
        log.error(f"Sync failed for {symbol}: {e}")

def main_loop():
    log.info("==== STARTING v47 ULTIMATE RUNNER ====")
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    for s in SYMBOLS: start_live_feed(s)

    while True:
        try:
            status = market_status()
            if status.get("reason") == "after market close": break
            for sym in SYMBOLS:
                build_full_analysis(sym)
                time.sleep(1)
        except Exception as e:
            log.error(f"Global Crash: {e}")
            time.sleep(10)
        time.sleep(3)

if __name__ == "__main__":
    main_loop()
