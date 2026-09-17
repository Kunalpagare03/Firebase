import os
import sys
import pandas as pd
import numpy as np
from datetime import datetime
import firebase_admin
from firebase_admin import credentials, firestore

# Setup Path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard, json_safe
from step9_deep_dive_analysis import perform_deep_dive
from utils.firebase_sync import sync_to_firestore
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure

def calculate_technicals(prices):
    if len(prices) < 14:
        return {"rsi": "-", "macd": "-", "trendline": "N/A", "candle": "N/A"}
    deltas = np.diff(prices)
    gain = np.where(deltas > 0, deltas, 0)
    loss = np.where(deltas < 0, -deltas, 0)
    avg_gain = np.mean(gain[-14:])
    avg_loss = np.mean(loss[-14:])
    rsi = 100 - (100 / (1 + (avg_gain/avg_loss))) if avg_loss != 0 else 100
    ema12 = pd.Series(prices).ewm(span=12).mean().iloc[-1]
    ema26 = pd.Series(prices).ewm(span=26).mean().iloc[-1]
    macd = ema12 - ema26
    slope = (prices[-1] - prices[0]) / len(prices)
    return {"rsi": round(rsi, 1), "macd": round(macd, 2), "trendline": "Bullish" if slope > 0 else "Bearish", "candle": "Bullish" if prices[-1] > prices[-2] else "Bearish"}

def _get_trade_decision(data, tech, structure):
    sig = data.get('final_signal', 'Neutral')
    is_breakout = structure.get('is_breakout', False)
    pattern = structure.get('chart_pattern', '')
    if is_breakout:
        if "Bull" in pattern or "W-Pattern" in pattern:
            return f"BREAKOUT ALERT: {pattern}. Strong Momentum. Buy at {data['spot_price']}"
        elif "Bear" in pattern or "M-Pattern" in pattern:
            return f"BREAKDOWN ALERT: {pattern}. Exit or Buy Put at {data['spot_price']}"
    if sig == 'Bullish' and tech['trendline'] == 'Bullish':
        return f"Bullish confirmation: Price holding above support with strong OI build."
    elif sig == 'Bearish' and tech['trendline'] == 'Bearish':
        return f"Bearish confirmation: Resistance BUILDING. Target lower levels."
    return f"Wait for clean break. Price and OI are mixed."

def sync_all():
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    print(f"Starting Smart Cloud Sync (Institutional)...")
    if not firebase_admin._apps:
        cred_path = os.path.join(ROOT, "..", "service-account.json")
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
    db = firestore.client()

    for symbol in SYMBOLS:
        try:
            raw = get_option_chain(symbol)
            df, spot = parse_option_chain(raw)
            doc_ref = db.collection("option_sentiment").document(symbol)
            doc_snap = doc_ref.get()
            history = []
            if doc_snap.exists: history = doc_snap.to_dict().get("spot_history", [])
            history.append({"time": int(datetime.now().timestamp() * 1000), "value": float(spot)})
            if len(history) > 1000: history = history[-1000:]
            prices = [h["value"] for h in history]

            tech = calculate_technicals(prices)
            structure = analyze_price_structure(prices)
            data = build_dashboard(df, df, spot)
            decision = _get_trade_decision(data, tech, structure)

            data["trade_alert"] = {
                "action": "BUY CALL" if "BREAKOUT" in decision or (data.get("final_signal")=="Bullish" and tech["trendline"]=="Bullish") else "BUY PUT" if "BREAKDOWN" in decision or (data.get("final_signal")=="Bearish" and tech["trendline"]=="Bearish") else "WAIT",
                "trigger": decision if structure.get('is_breakout') else "Wait for break",
                "note": "Cloud Terminal Sync",
                "technicals": tech
            }
            data["decision_note"] = decision
            data["oi_trend"] = "Strong" if abs(data.get("pcr_oi", 1) - 1) > 0.1 else "Stable"
            data["flow"] = data.get("oi_directional_read", "Neutral")
            data["greeks"] = analyze_chain_greeks(df, spot)
            data["price_action"] = analyze_price_action(symbol)
            data["price_structure"] = structure
            data["deep_dive"] = perform_deep_dive(df, spot)
            data["spot_history"] = history
            data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            data["market_status_str"] = f"OPEN | CLOUD SYNC | {data['fetched_at']}"

            sync_to_firestore("option_sentiment", symbol, json_safe(data))
            print(f"Smart Sync SUCCESS: {symbol} [{structure.get('chart_pattern')}]")
        except Exception as e:
            print(f"Error syncing {symbol}: {e}")

if __name__ == "__main__":
    sync_all()
