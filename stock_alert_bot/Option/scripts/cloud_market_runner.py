import time
import os
import sys
import logging
import threading
from datetime import datetime
from collections import deque, defaultdict

# Add project roots to path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard
from step9_deep_dive_analysis import perform_deep_dive
from utils.firebase_sync import sync_to_firestore
from brokers.angel_one import start_live_feed, get_latest_spot, get_live_status
from stock_alert.market_hours import market_status
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger("CloudTerminal")

# In-memory history for real-time features
_spot_history = defaultdict(lambda: deque(maxlen=200))
_chart_history = defaultdict(lambda: deque(maxlen=3600))
_refresh_lock = threading.Lock()

def _get_trade_alert(dashboard, symbol):
    # This logic matches the local web_dashboard.py's _trade_alert function
    # It combines PCR, OI Buildup, and Price Structure into a final recommendation
    is_bull = dashboard.get('final_signal') == 'Bullish'
    support = (dashboard.get('support_zones') or [0])[0]
    resistance = (dashboard.get('resistance_zones') or [0])[0]

    if is_bull:
        action = "BUY CALL WATCH"
        trigger = f"Hold above {support} and reclaim {resistance}"
        note = "Confirmed Bullish bias: OI support and PCR favoring upside."
    else:
        action = "BUY PUT WATCH"
        trigger = f"Break and hold below {support}"
        note = "Confirmed Bearish bias: Resistance building at higher strikes."

    return {
        "action": action,
        "trigger": trigger,
        "note": note,
        "score": dashboard.get('votes', {}).get('bullish', 0) - dashboard.get('votes', {}).get('bearish', 0)
    }

def run_terminal_sync(symbol="NIFTY"):
    log.info(f"==== Activating Cloud Terminal: {symbol} ====")

    # 1. Initialize Angel One Feed
    start_live_feed(symbol)

    last_full_refresh = 0
    df_prev = None

    while True:
        try:
            session = market_status()
            reason = session.get("reason", "unknown")
            if reason == "after market close":
                log.info(f"Market Cycle Completed: {session['reason']} ({session['local_time']})")
                log.info("Stopping live option runner at market close.")
                return
            if not session["is_open"]:
                log.info(f"Market not yet open ({reason}, {session['local_time']}); waiting for next session.")
                time.sleep(300)
                continue

            now = time.time()
            live_spot = get_latest_spot(symbol)

            # Record Tick
            if live_spot:
                _spot_history[symbol].append(live_spot)
                _chart_history[symbol].append({"time": int(now * 1000), "value": live_spot})

            # 2. Sync Cycle (High Frequency)
            # Full Chain Refresh every 2 minutes
            if now - last_full_refresh > 120 or df_prev is None:
                try:
                    raw = get_option_chain(symbol)
                    df_curr, spot_chain = parse_option_chain(raw)
                    df_prev = df_curr
                    last_full_refresh = now
                    log.info("NSE Option Chain Refreshed")
                except Exception as e:
                    log.error(f"Chain Fetch Failed: {e}")

            if df_prev is not None:
                spot = live_spot if live_spot else df_prev.get('spot_price', 0)
                if spot == 0:
                    spot = spot_chain if 'spot_chain' in locals() else 23400

                # Build Core Dashboard
                try:
                    data = build_dashboard(df_prev, df_prev, spot)
                    data["trade_alert"] = _get_trade_alert(data, symbol)
                    data["price_action"] = analyze_price_action(symbol)
                    data["greeks"] = analyze_chain_greeks(df_prev, spot)
                    data["price_structure"] = analyze_price_structure([s["value"] for s in _chart_history[symbol]])
                    data["deep_dive"] = perform_deep_dive(df_prev, spot)
                    data["spot_history"] = list(_chart_history[symbol])[-100:]
                    data["fetched_at"] = datetime.now().strftime("%H:%M:%S")

                    # Push to Cloud
                    sync_to_firestore("option_sentiment", symbol, data)
                    log.info(f"Sync Success: {symbol} @ {spot} at {data['fetched_at']}")
                except Exception as e:
                    log.error(f"Dashboard/Sync Failed: {e}")

        except Exception as e:
            log.error(f"Main Loop Error: {e}")
            time.sleep(10)

        time.sleep(10) # Update every 10 seconds

if __name__ == "__main__":
    target = os.environ.get("SYMBOL", "NIFTY")
    run_terminal_sync(target)
