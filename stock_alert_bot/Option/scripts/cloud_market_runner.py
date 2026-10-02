import time
import os
import sys
import logging
import threading
import json
import argparse
import numpy as np
import pandas as pd
from datetime import datetime
from collections import deque, defaultdict
import firebase_admin
from firebase_admin import credentials, firestore

# Setup Path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard, json_safe
from step6_buildup import classify_buildup
from step9_deep_dive_analysis import perform_deep_dive
from utils.firebase_sync import sync_to_firestore
from brokers.angel_one import start_live_feed, get_latest_spot, get_live_status
from stock_alert.market_hours import market_status
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from src.trend_detector import analyze_price_structure

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger("CloudTerminal")

# Multi-Instrument Memory (Deduplicated)
_chart_history = defaultdict(lambda: deque(maxlen=10000))
_last_chain_refresh = defaultdict(int)
_cached_chain = {}
_cached_df_prev = {}
_cached_expiry = {}

# Option-chain positioning changes more slowly than a quote feed.  Keep the
# analysis cadence in one place so the scheduler and the mobile client agree.
CONFIG_PATH = os.path.join(ROOT, "config", "settings.json")
try:
    with open(CONFIG_PATH, "r", encoding="utf-8") as config_file:
        CONFIG = json.load(config_file)
except (OSError, json.JSONDecodeError):
    CONFIG = {}
REFRESH_SECONDS = max(60, int(CONFIG.get("refresh_minutes", 5)) * 60)

def load_initial_history(db, symbols):
    for sym in symbols:
        try:
            doc_snap = db.collection("option_sentiment").document(sym).get()
            if doc_snap.exists:
                hist = doc_snap.to_dict().get("spot_history", [])
                for h in hist: _chart_history[sym].append(h)
                log.info(f"Loaded {len(hist)} history points for {sym}")
        except: pass

def calculate_technicals(prices):
    if len(prices) < 14: return {"rsi": "-", "macd": "-", "trendline": "N/A", "candle": "N/A"}
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
        if "Bull" in pattern or "W-Pattern" in pattern: return f"BREAKOUT: {pattern}. Buy at {data['spot_price']}"
        elif "Bear" in pattern or "M-Pattern" in pattern: return f"BREAKDOWN: {pattern}. Sell at {data['spot_price']}"
    if sig == 'Bullish' and tech['trendline'] == 'Bullish': return f"Bullish: Support hold with OI build."
    elif sig == 'Bearish' and tech['trendline'] == 'Bearish': return f"Bearish: Resistance building. Target lower."
    return f"Price and OI are mixed; wait for a clean break or support hold."


def _deep_analysis_summary(data, structure, greeks, price_action):
    """Combine live price, OI and volume into an explainable setup watch."""
    deep = data.get("deep_dive") or {}
    signal = data.get("final_signal", "Neutral")
    confirmations = []
    score = 0

    option_read = (data.get("oi_directional_read") or "").lower()
    pressure = (deep.get("market_pressure") or "").lower()
    price_read = (price_action.get("reading") or "").lower()
    chart_trend = (structure.get("trend") or "Neutral")

    if signal != "Neutral" and signal.lower() in option_read:
        confirmations.append("OI positioning agrees with the directional read")
        score += 1
    if signal == "Bullish" and "buying" in pressure:
        confirmations.append("Put-side volume supports the bullish read")
        score += 1
    elif signal == "Bearish" and "selling" in pressure:
        confirmations.append("Call-side volume supports the bearish read")
        score += 1
    if signal.lower() in price_read:
        confirmations.append("Daily price action agrees")
        score += 1
    if signal == chart_trend:
        confirmations.append("Live chart structure agrees")
        score += 1
    if structure.get("is_breakout"):
        confirmations.append(f"Pattern event: {structure.get('chart_pattern', 'breakout')}")
        score += 1

    votes = []
    if "bullish" in option_read:
        votes.append("Bullish")
    elif "bearish" in option_read:
        votes.append("Bearish")
    if "bullish" in (deep.get("vol_pcr_sentiment") or "").lower() or "buying" in pressure:
        votes.append("Bullish")
    elif "bearish" in (deep.get("vol_pcr_sentiment") or "").lower() or "selling" in pressure:
        votes.append("Bearish")
    if chart_trend in ("Bullish", "Bearish"):
        votes.append(chart_trend)
    if "bullish" in price_read:
        votes.append("Bullish")
    elif "bearish" in price_read:
        votes.append("Bearish")
    bullish_votes = votes.count("Bullish")
    bearish_votes = votes.count("Bearish")
    candidate = "CALL WATCH" if bullish_votes >= 2 and bullish_votes > bearish_votes else (
        "PUT WATCH" if bearish_votes >= 2 and bearish_votes > bullish_votes else "WAIT"
    )
    support = data.get("support_zones") or []
    resistance = data.get("resistance_zones") or []
    spot = float(data.get("spot_price") or 0)
    support_below = [float(level) for level in support if float(level) < spot]
    resistance_above = [float(level) for level in resistance if float(level) > spot]
    invalidation = max(support_below) if candidate == "CALL WATCH" and support_below else (
        min(resistance_above) if candidate == "PUT WATCH" and resistance_above else 0
    )
    target = min(resistance_above) if candidate == "CALL WATCH" and resistance_above else (
        max(support_below) if candidate == "PUT WATCH" and support_below else 0
    )

    confidence = "High" if score >= 4 else "Moderate" if score >= 2 else "Low"
    summary = "; ".join(confirmations) if confirmations else "Mixed inputs — wait for price and OI confirmation."
    if signal == "Bullish" and score >= 2:
        next_candle = "Bullish continuation watch. Confirm only if the next candle holds above support."
    elif signal == "Bearish" and score >= 2:
        next_candle = "Bearish continuation watch. Confirm only if the next candle stays below resistance."
    else:
        next_candle = "No directional candle call. Wait for the next candle to close with price and OI confirmation."
    return {
        "confidence": confidence,
        "confirmation_count": score,
        "direction": candidate,
        "bullish_votes": bullish_votes,
        "bearish_votes": bearish_votes,
        "entry_condition": (
            f"Wait for spot to hold above {spot:.2f} and confirm with the next candle"
            if candidate == "CALL WATCH" else
            f"Wait for spot to hold below {spot:.2f} and confirm with the next candle"
            if candidate == "PUT WATCH" else
            "No aligned setup; wait for price, OI and volume to agree"
        ),
        "invalidation_spot": invalidation,
        "target_spot": target,
        "data_note": "Bias is conditional evidence, not a guaranteed direction. Fast-tape activity is a proxy, not verified HFT attribution.",
        "summary": summary,
        "chart_pattern": structure.get("chart_pattern", "Building data"),
        "chart_trend": chart_trend,
        "chart_confidence": structure.get("confidence", 0),
        "market_pressure": deep.get("market_pressure", "Unavailable"),
        "volume_pcr": deep.get("volume_pcr", 0),
        "volume_read": deep.get("vol_pcr_sentiment", "Neutral"),
        "greeks_summary": greeks.get("summary", greeks.get("reason", "Greeks unavailable")),
        "next_candle_outlook": next_candle,
    }

def build_full_analysis(symbol):
    try:
        now = time.time()
        live_spot = get_latest_spot(symbol)
        if live_spot:
            _chart_history[symbol].append({"time": int(now * 1000), "value": float(live_spot)})

        if now - _last_chain_refresh[symbol] >= REFRESH_SECONDS or symbol not in _cached_chain:
            raw = get_option_chain(symbol)
            df, spot_chain = parse_option_chain(raw, expiry=raw.get("_selected_expiry"))
            _cached_df_prev[symbol] = _cached_chain.get(symbol, df)
            _cached_chain[symbol] = df
            _cached_expiry[symbol] = raw.get("_selected_expiry", "")
            _last_chain_refresh[symbol] = now

        if symbol in _cached_chain:
            df = _cached_chain[symbol]
            df_prev = _cached_df_prev.get(symbol, df)
            prices = [s['value'] for s in _chart_history[symbol]]
            spot = live_spot if live_spot else prices[-1] if prices else 23400

            tech = calculate_technicals(prices)
            structure = analyze_price_structure(prices)
            data = build_dashboard(df_prev, df, spot)
            decision = _get_trade_decision(data, tech, structure)

            # --- FULL MIRROR FIELDS ---
            data["trade_alert"] = {
                "action": "BUY CALL" if "Bullish" in decision or "BREAKOUT" in decision else "BUY PUT" if "Bearish" in decision or "BREAKDOWN" in decision else "WAIT",
                "trigger": decision,
                "note": f"System Live ({len(prices)} ticks)",
                "technicals": tech
            }
            data["decision_note"] = decision
            data["oi_trend"] = "Strong" if abs(data.get("pcr_oi", 1) - 1) > 0.1 else "Moderate"

            deep = data.get("deep_dive", {})
            call_v = deep.get("top_call_vol_strikes", [{}])[0].get("call_volume", 0)
            put_v = deep.get("top_put_vol_strikes", [{}])[0].get("put_volume", 0)
            oi_building = data.get("oi_building_analysis", {})
            call_oi_change = int(oi_building.get("call_oi_change", 0) or 0)
            put_oi_change = int(oi_building.get("put_oi_change", 0) or 0)
            data["vol_flow"] = f"{'Bullish' if call_v > put_v else 'Bearish'} flow | Call vol {call_v} | Put vol {put_v}"
            data["vol_deltas"] = f"call {call_v}, put {put_v}"
            data["oi_deltas"] = f"Call OI {call_oi_change:+,} | Put OI {put_oi_change:+,}"
            data["countdown_sec"] = max(0, int(REFRESH_SECONDS - (now - _last_chain_refresh[symbol])))

            data["price_structure"] = structure
            full_chain = df.copy()
            full_chain["total_oi"] = full_chain["call_oi"] + full_chain["put_oi"]
            previous_by_strike = df_prev.drop_duplicates("strike").set_index("strike")
            for side in ("call", "put"):
                oi_column, ltp_column = f"{side}_oi", f"{side}_ltp"
                full_chain[f"window_{side}_oi_change"] = full_chain["strike"].map(previous_by_strike[oi_column]).rsub(full_chain[oi_column]).fillna(0)
                previous_ltp = full_chain["strike"].map(previous_by_strike[ltp_column])
                full_chain[f"{side}_buildup"] = [
                    classify_buildup(float(price - old_price), float(oi_delta))
                    if pd.notna(old_price) else "No prior snapshot"
                    for price, old_price, oi_delta in zip(
                        full_chain[ltp_column], previous_ltp, full_chain[f"window_{side}_oi_change"]
                    )
                ]
            full_chain["oi_side"] = full_chain.apply(
                lambda row: "Put-side support" if row["window_put_oi_change"] > row["window_call_oi_change"]
                else "Call-side resistance" if row["window_call_oi_change"] > row["window_put_oi_change"]
                else "Balanced", axis=1
            )
            data["option_chain"] = full_chain.to_dict("records")
            data["chain_expiry"] = str(_cached_expiry.get(symbol, ""))
            data["chain_updated_at"] = datetime.fromtimestamp(
                _last_chain_refresh[symbol]
            ).strftime("%Y-%m-%d %H:%M:%S")
            data["greeks"] = json_safe(analyze_chain_greeks(df, spot))
            data["price_action"] = json_safe(analyze_price_action(symbol))
            data["deep_analysis"] = _deep_analysis_summary(
                data, structure, data["greeks"], data["price_action"]
            )
            data["trade_alert"]["action"] = data["deep_analysis"]["direction"]
            data["trade_alert"]["trigger"] = data["deep_analysis"]["entry_condition"]
            data["trade_alert"]["note"] = "Conditional setup from price, OI and volume alignment"
            if data["deep_analysis"]["direction"] != "WAIT" and not full_chain.empty:
                atm = full_chain.iloc[(full_chain["strike"] - spot).abs().argsort()[:1]].iloc[0]
                is_call = data["deep_analysis"]["direction"] == "CALL WATCH"
                data["deep_analysis"]["option_contract"] = f"{int(atm['strike'])} {'CE' if is_call else 'PE'}"
                data["deep_analysis"]["option_premium"] = float(atm["call_ltp"] if is_call else atm["put_ltp"])
            else:
                data["deep_analysis"]["option_contract"] = "WAIT"
                data["deep_analysis"]["option_premium"] = 0
            data["spot_history"] = list(_chart_history[symbol])
            data["fetched_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            feed = get_live_status(symbol)
            source = "Angel One live quote" if feed.get("connected") and (feed.get("tick_age") or 0) <= 30 else "NSE option chain"
            data["market_status_str"] = (
                f"OPEN | {CONFIG.get('refresh_minutes', 5)} MIN ANALYSIS | {source} | "
                f"{data['fetched_at']}"
            )

            sync_to_firestore("option_sentiment", symbol, json_safe(data))
            log.info(f"V62.2 PUSH: {symbol} Pattern: {structure.get('chart_pattern')}")
            return True
        log.error("No option chain data was available for %s", symbol)
        return False

    except Exception as e:
        log.error(f"Sync error for {symbol}: {e}")
        return False

def main_loop(run_once=False):
    SYMBOLS = ["NIFTY", "BANKNIFTY"]
    if not firebase_admin._apps:
        cred = credentials.Certificate(os.path.join(ROOT, "..", "service-account.json"))
        firebase_admin.initialize_app(cred)
    db = firestore.client()
    load_initial_history(db, SYMBOLS)
    for s in SYMBOLS: start_live_feed(s)

    if run_once:
        status = market_status()
        if not status.get("is_open"):
            log.warning("Manual option refresh skipped: market is %s.", status.get("reason"))
            db.collection("option_scans").document("status").set({
                "status": f"Option refresh unavailable: {status.get('reason')}",
                "is_scanning": False,
                "progress": 0,
                "results": "Option data was not refreshed because the market is closed.",
            }, merge=True)
            raise RuntimeError(f"Market is not open ({status.get('reason')}); no option data was refreshed")
        db.collection("option_scans").document("status").set({
            "status": "Option analysis started...",
            "is_scanning": True,
            "progress": 7,
            "start_time": datetime.now().strftime("%H:%M:%S"),
        }, merge=True)
        for index, symbol in enumerate(SYMBOLS):
            db.collection("option_scans").document("status").set({
                "status": f"Updating option analysis: {symbol}...",
                "is_scanning": True,
                "progress": 7 if index == 0 else 50,
            }, merge=True)
            if not build_full_analysis(symbol):
                raise RuntimeError(f"Option analysis failed for {symbol}; check the manual_option.log output")
        db.collection("option_scans").document("status").set({
            "status": "Option analysis complete",
            "is_scanning": False,
            "progress": 100,
            "last_scan_time": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
            "results": "Option analysis refreshed for NIFTY and BANKNIFTY.",
        }, merge=True)
        return

    while True:
        try:
            status = market_status()
            if not status.get("is_open"):
                if status.get("reason") == "after market close":
                    log.info("Market closed; option analysis service stopped.")
                    break
                log.info("Market is not open (%s); waiting.", status.get("reason"))
                time.sleep(min(60, REFRESH_SECONDS))
                continue
            cycle_started = time.monotonic()
            for sym in SYMBOLS:
                build_full_analysis(sym)
                time.sleep(1)
        except Exception as e:
            log.error(f"Crash: {e}")
            time.sleep(5)
        elapsed = time.monotonic() - cycle_started
        time.sleep(max(1, REFRESH_SECONDS - elapsed))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Sync option analysis to Firebase.")
    parser.add_argument("--once", action="store_true", help="Run one market-hours refresh and exit.")
    main_loop(run_once=parser.parse_args().once)
