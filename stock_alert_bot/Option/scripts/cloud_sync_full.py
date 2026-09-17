import os
import sys
import pandas as pd
from datetime import datetime

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

def sync_all():
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    print(f"Starting Full Cloud Sync for {SYMBOLS}...")

    for symbol in SYMBOLS:
        try:
            # 1. Fetch & Parse
            raw = get_option_chain(symbol)
            df, spot = parse_option_chain(raw)

            # 2. Base Analysis
            data = build_dashboard(df, df, spot)

            # 3. Enhanced Institutional Layers
            data["greeks"] = analyze_chain_greeks(df, spot)
            data["price_action"] = analyze_price_action(symbol)
            # Use spot as a 1-point list for structure since history is not available in one-off cloud runs
            data["price_structure"] = analyze_price_structure([spot])
            data["deep_dive"] = perform_deep_dive(df, spot)
            data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            data["market_status_str"] = f"OPEN | CLOUD SYNC | {data['fetched_at']}"

            # 4. Push to Firebase
            sync_to_firestore("option_sentiment", symbol, json_safe(data))
            print(f"Successfully synced {symbol} with full telemetry.")

        except Exception as e:
            print(f"Error syncing {symbol}: {e}")

if __name__ == "__main__":
    sync_all()
