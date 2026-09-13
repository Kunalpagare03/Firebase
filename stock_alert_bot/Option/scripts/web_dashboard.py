
import os
import json
import sys
import importlib.util
import time
import threading
from collections import defaultdict, deque
from datetime import datetime
from flask import Flask, jsonify, render_template, request, Response
from functools import wraps

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

APP = Flask(__name__, template_folder=os.path.join(os.path.dirname(__file__), "templates"))

def check_auth(username, password):
    """Check if a username password combination is valid."""
    # Hardcoded for now, should ideally be in a config file or env var
    return username == 'admin' and password == 'kunalstock'

def authenticate():
    """Sends a 401 response that enables basic auth."""
    return Response(
    'Could not verify your access level for that URL.\n'
    'You have to login with proper credentials', 401,
    {'WWW-Authenticate': 'Basic realm="Login Required"'})

def requires_auth(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth = request.authorization
        if not auth or not check_auth(auth.username, auth.password):
            return authenticate()
        return f(*args, **kwargs)
    return decorated

@APP.after_request
def disable_dashboard_cache(response):
    response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate, max-age=0"
    response.headers["Pragma"] = "no-cache"
    response.headers["Expires"] = "0"
    return response


def load_option_module():
    path = os.path.join(os.path.dirname(__file__), "option_analysis.py")
    spec = importlib.util.spec_from_file_location("option_analysis", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


OPT = load_option_module()

with open(os.path.join(ROOT, "config", "settings.json"), "r") as f:
    CONFIG = json.load(f)

from step1_fetch import get_option_chain
from step1_fetch_dhan import get_option_chain_dhan
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard
from utils.firebase_sync import sync_to_firestore
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks
from stock_alert.storage import save_live_snapshot, save_option_chain_snapshot, save_daily_trend_change
from stock_alert.market_hours import market_status
from src.trend_detector import analyze_price_structure
from brokers.angel_one import get_latest_spot, get_live_status, start_live_feed

_trend_state = {"baseline_df": None, "baseline_at": None, "last_trend": None}
_analysis_cache = {}
_price_action_cache = {}
_analysis_lock = threading.Lock()
_refreshing_symbols = set()
_last_saved_at = {}
_last_live_saved_at = {}
_spot_history = defaultdict(lambda: deque(maxlen=20))
_chart_history = defaultdict(lambda: deque(maxlen=3600))
_alert_state = defaultdict(lambda: {"direction": None, "streak": 0, "confirmed": None})
_signal_cache = {}
SUPPORTED_SYMBOLS = {
    "NIFTY": "NIFTY 50",
    "BANKNIFTY": "BANKNIFTY",
    "FINNIFTY": "FINNIFTY",
    "SENSEX": "SENSEX",
}
ANALYSIS_CACHE_SECONDS = 5.0
PRICE_ACTION_CACHE_SECONDS = 15
INTRADAY_TREND_THRESHOLD = 0.0002
SIGNAL_PRICE_MOVE_THRESHOLD = 0.002


def analyze_flow(previous_df, current_df):
    """Compare total call/put volume and OI between two live snapshots."""
    current = {
        "call_volume": int(current_df["call_volume"].sum()),
        "put_volume": int(current_df["put_volume"].sum()),
        "call_oi": int(current_df["call_oi"].sum()),
        "put_oi": int(current_df["put_oi"].sum()),
    }
    if previous_df is None:
        return {
            **current,
            "call_volume_change": None,
            "put_volume_change": None,
            "call_oi_change": None,
            "put_oi_change": None,
            "trend": "Waiting for second snapshot",
            "alert": "No trend-change alert yet",
        }

    changes = {
        "call_volume_change": current["call_volume"] - int(previous_df["call_volume"].sum()),
        "put_volume_change": current["put_volume"] - int(previous_df["put_volume"].sum()),
        "call_oi_change": current["call_oi"] - int(previous_df["call_oi"].sum()),
        "put_oi_change": current["put_oi"] - int(previous_df["put_oi"].sum()),
    }
    if changes["put_volume_change"] > changes["call_volume_change"] and changes["put_oi_change"] >= changes["call_oi_change"]:
        trend = "Bullish flow"
    elif changes["call_volume_change"] > changes["put_volume_change"] and changes["call_oi_change"] >= changes["put_oi_change"]:
        trend = "Bearish flow"
    else:
        trend = "Neutral / mixed flow"
    return {**current, **changes, "trend": trend, "alert": "No trend change"}


def _market_trend(option_read, price_read):
    def clean(name):
        return (name or "").strip().lower()

    price_key = clean(price_read)
    option_key = clean(option_read)

    if "mixed" in price_key or "neutral" in price_key or "range" in price_key:
        return "Mixed / neutral trend"
    if "bearish" in price_key:
        return "Bearish trend"
    if "bullish" in price_key:
        return "Bullish trend"

    if "bearish" in option_key:
        return "Bearish trend"
    if "bullish" in option_key:
        return "Bullish trend"
    return "Mixed / neutral trend"


def _single_direction_summary(price_read, support_zones, resistance_zones, spot_price):
    def clean(name):
        return (name or "").strip().lower()

    price_key = clean(price_read)
    support = list(support_zones or [])
    resistance = list(resistance_zones or [])
    support_level = support[0] if support else None
    resistance_level = resistance[0] if resistance else None

    if "bearish" in price_key:
        if support_level is not None:
            return {
                "headline": "Bearish bias",
                "note": f"Break below {support_level} is the trigger; hold {support_level} to stay neutral; bullish only above {resistance_level or 'resistance'} with OI confirmation.",
                "signal": "Bearish",
            }
        return {"headline": "Bearish bias", "note": "Price is weak; wait for lower support to break before adding conviction.", "signal": "Bearish"}

    if "bullish" in price_key:
        if support_level is not None:
            return {
                "headline": "Bullish bias",
                "note": f"Hold above {support_level}; bullish confirmation needs reclaim of {resistance_level or 'resistance'} with OI support.",
                "signal": "Bullish",
            }
        return {"headline": "Bullish bias", "note": "Price is strong; wait for support to hold before carrying more conviction.", "signal": "Bullish"}

    return {"headline": "Neutral / watch", "note": "Price and OI are mixed; wait for a clean break or support hold.", "signal": "Neutral"}


def _record_live_spot(symbol, spot):
    if spot is not None:
        value = float(spot)
        _spot_history[symbol].append(value)
        _chart_history[symbol].append({"time": int(time.time() * 1000), "value": value})


def _intraday_price_read(symbol):
    history = _spot_history[symbol]
    if len(history) < 5 or not history[0]:
        return None
    change_ratio = (history[-1] - history[0]) / history[0]
    if change_ratio <= -INTRADAY_TREND_THRESHOLD:
        return "Bearish intraday price action"
    if change_ratio >= INTRADAY_TREND_THRESHOLD:
        return "Bullish intraday price action"
    return "Mixed / range-bound intraday price action"


def _intraday_change_pct(symbol):
    history = _spot_history[symbol]
    if len(history) < 2 or not history[0]:
        return None
    return round((history[-1] - history[0]) / history[0] * 100, 3)


def _technical_read(symbol):
    values = list(_spot_history[symbol])
    if len(values) < 5:
        return {"rsi": None, "macd": None, "trendline": "Insufficient live history", "candle": "Insufficient live history"}

    changes = [values[index] - values[index - 1] for index in range(1, len(values))]
    gains = [change for change in changes[-14:] if change > 0]
    losses = [-change for change in changes[-14:] if change < 0]
    average_gain = sum(gains) / max(len(changes[-14:]), 1)
    average_loss = sum(losses) / max(len(changes[-14:]), 1)
    rsi = 100.0 if average_loss == 0 and average_gain else 0.0 if average_loss == 0 else 100 - (100 / (1 + average_gain / average_loss))

    def ema(period):
        multiplier = 2 / (period + 1)
        value = values[0]
        for price in values[1:]:
            value = (price - value) * multiplier + value
        return value

    macd = ema(12) - ema(26)
    slope = (values[-1] - values[0]) / max(len(values) - 1, 1)
    trendline = "Bullish trendline" if slope > 0 else "Bearish trendline" if slope < 0 else "Flat trendline"
    candle = "Bullish momentum candle" if values[-1] > values[-2] else "Bearish momentum candle" if values[-1] < values[-2] else "Doji / indecision"
    return {"rsi": round(rsi, 1), "macd": round(macd, 2), "trendline": trendline, "candle": candle}


def _price_structure_read(symbol):
    values = [sample["value"] for sample in _chart_history[symbol]]
    return analyze_price_structure(values)


def _directional_price_read(symbol, fallback):
    structure = _price_structure_read(symbol)
    if structure["trend"] == "Bearish":
        return "Bearish price structure"
    if structure["trend"] == "Bullish":
        return "Bullish price structure"
    return fallback


def _trade_alert(price_read, support_zones, resistance_zones, oi_read, pcr_oi, call_oi_change, put_oi_change, call_volume_change, put_volume_change, symbol):
    def clean(value):
        return (value or "").strip().lower()

    support = list(support_zones or [])
    resistance = list(resistance_zones or [])
    support_level = support[0] if support else "support"
    resistance_level = resistance[0] if resistance else "resistance"
    technical = _technical_read(symbol)
    factors = []
    bullish = bearish = 0
    price_key = clean(price_read)
    if "bullish" in price_key:
        bullish += 1; factors.append("price bullish")
    elif "bearish" in price_key:
        bearish += 1; factors.append("price bearish")
    if technical["rsi"] is not None:
        if technical["rsi"] >= 55: bullish += 1; factors.append(f"RSI {technical['rsi']} bullish")
        elif technical["rsi"] <= 45: bearish += 1; factors.append(f"RSI {technical['rsi']} bearish")
    if technical["macd"] is not None:
        if technical["macd"] > 0: bullish += 1; factors.append("MACD positive")
        elif technical["macd"] < 0: bearish += 1; factors.append("MACD negative")
    if "bullish" in technical["trendline"].lower(): bullish += 1; factors.append("trendline bullish")
    elif "bearish" in technical["trendline"].lower(): bearish += 1; factors.append("trendline bearish")
    if "bullish" in technical["candle"].lower(): bullish += 1; factors.append("candle bullish")
    elif "bearish" in technical["candle"].lower(): bearish += 1; factors.append("candle bearish")
    oi_key = clean(oi_read)
    if all(value is not None for value in (call_oi_change, put_oi_change, call_volume_change, put_volume_change)):
        put_flow = put_oi_change > call_oi_change and put_volume_change > call_volume_change
        call_flow = call_oi_change > put_oi_change and call_volume_change > put_volume_change
        if put_flow: bullish += 1; factors.append(f"put OI +{put_oi_change} and volume +{put_volume_change} > call")
        elif call_flow: bearish += 1; factors.append(f"call OI +{call_oi_change} and volume +{call_volume_change} > put")
        else: factors.append("OI/volume sides mixed")
    elif "bullish" in oi_key or (pcr_oi is not None and pcr_oi > 1.05): bullish += 1; factors.append("OI/PCR bullish")
    elif "bearish" in oi_key or (pcr_oi is not None and pcr_oi < 0.95): bearish += 1; factors.append("OI/PCR bearish")
    direction = "bullish" if bullish > bearish else "bearish" if bearish > bullish else "mixed"
    score = max(bullish, bearish)
    state = _alert_state[symbol]
    if direction in ("bullish", "bearish") and score >= 4 and abs(bullish - bearish) >= 1:
        state["streak"] = state["streak"] + 1 if state["direction"] == direction else 1
        state["direction"] = direction
    else:
        state["direction"] = None
        state["streak"] = 0

    if state["streak"] >= 2:
        state["confirmed"] = direction
    elif state["confirmed"] and state["direction"] != state["confirmed"]:
        state["confirmed"] = None

    confirmed = state["confirmed"]
    if confirmed == "bearish":
        action = "BUY PUT WATCH"; trigger = f"Break and hold below {support_level}"
    elif confirmed == "bullish":
        action = "BUY CALL WATCH"; trigger = f"Hold above {support_level} and reclaim {resistance_level}"
    else:
        action = "WAIT / NO TRADE"; trigger = f"Wait for a clean break of {support_level} or {resistance_level}"
    confirmation = f"confirmed {state['streak']}/2 checks" if confirmed else "needs 4/6 confirmations for 2 meaningful checks"
    return {"action": action, "trigger": trigger, "note": f"Confirmations: {score}/6 | {confirmation} | " + (", ".join(factors) or "insufficient live evidence") + ".", "score": score, "bullish": bullish, "bearish": bearish, "technical": technical}


def _stable_signal(symbol, directional_read, dashboard, flow, spot):
    """Recalculate signals periodically; keep confirmation state stable between checks."""
    now = time.monotonic()
    cached = _signal_cache.get(symbol)
    interval_seconds = max(30, int(CONFIG.get("trend_analysis_interval_minutes", 5)) * 60)
    previous_spot = cached.get("spot") if cached else None
    moved_enough = (
        previous_spot is None
        or spot is None
        or abs(float(spot) - float(previous_spot)) / max(abs(float(previous_spot)), 1) >= SIGNAL_PRICE_MOVE_THRESHOLD
    )
    if cached and now - cached["at"] < interval_seconds and not moved_enough:
        return cached["decision"], cached["trade_alert"]

    trade_alert = _trade_alert(
        directional_read,
        dashboard.get("support_zones", []),
        dashboard.get("resistance_zones", []),
        dashboard.get("oi_directional_read"),
        dashboard.get("pcr_oi"),
        flow.get("call_oi_change"),
        flow.get("put_oi_change"),
        flow.get("call_volume_change"),
        flow.get("put_volume_change"),
        symbol,
    )
    decision = _single_direction_summary(
        directional_read,
        dashboard.get("support_zones", []),
        dashboard.get("resistance_zones", []),
        spot,
    )
    _signal_cache[symbol] = {
        "at": now,
        "spot": spot,
        "direction": directional_read,
        "decision": decision,
        "trade_alert": trade_alert,
    }
    return decision, trade_alert


def _json_safe(value):
    if isinstance(value, dict):
        return {key: _json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_json_safe(item) for item in value]
    if hasattr(value, "item"):
        return value.item()
    return value


def get_analysis(symbol="^NSEI"):
    start_live_feed(symbol)
    cached = _analysis_cache.get(symbol)
    now = time.monotonic()
    if cached and now - cached["at"] < ANALYSIS_CACHE_SECONDS:
        return _apply_live_spot(cached["data"], symbol)

    with _analysis_lock:
        cached = _analysis_cache.get(symbol)
        if symbol in _refreshing_symbols:
            return _apply_live_spot(cached["data"], symbol) if cached else None
        _refreshing_symbols.add(symbol)
        if cached:
            threading.Thread(target=_refresh_analysis, args=(symbol,), daemon=True).start()
            return _apply_live_spot(cached["data"], symbol)
    try:
        return _get_analysis_and_cache(symbol)
    finally:
        with _analysis_lock:
            _refreshing_symbols.discard(symbol)


def _apply_live_spot(data, symbol):
    live_spot = get_latest_spot(symbol)
    live_status = get_live_status(symbol)
    _record_live_spot(symbol, live_spot)
    if live_spot is None or not isinstance(data, dict):
        return data
    result = dict(data)
    dashboard = dict(result.get("dashboard") or {})
    dashboard["spot_price"] = live_spot
    intraday_read = _intraday_price_read(symbol)
    if intraday_read:
        flow = result.get("flow") or {}
        structure = _price_structure_read(symbol)
        directional_read = _directional_price_read(symbol, intraday_read)
        confirmed_read = directional_read or "Mixed / range-bound intraday price action"
        decision, trade_alert = _stable_signal(symbol, confirmed_read, dashboard, flow, live_spot)
        dashboard["market_read"] = decision["headline"]
        dashboard["combined_sentiment_read"] = decision["headline"]
        dashboard["final_signal"] = decision["signal"]
        dashboard["decision_headline"] = decision["headline"]
        dashboard["decision_note"] = decision["note"]
        dashboard["decision_signal"] = decision["signal"]
        dashboard["single_direction"] = decision["headline"]
        dashboard["intraday_price_read"] = intraday_read
        dashboard["price_structure"] = structure
        dashboard["intraday_change_pct"] = _intraday_change_pct(symbol)
        dashboard["trade_alert"] = trade_alert
        dashboard["signal_refresh_seconds"] = max(30, int(CONFIG.get("trend_analysis_interval_minutes", 5)) * 60)
    result["dashboard"] = dashboard
    result["live_feed"] = live_status
    result["spot_source"] = "angel-one-live" if live_spot is not None else result.get("source", "fallback")
    result["spot_history"] = list(_chart_history[symbol])
    return result


def _refresh_analysis(symbol):
    try:
        _get_analysis_and_cache(symbol)
    finally:
        with _analysis_lock:
            _refreshing_symbols.discard(symbol)


def _get_analysis_and_cache(symbol):
    result = _get_analysis(symbol)
    if isinstance(result, dict):
        result = dict(result)
        result["fetched_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
        if "dashboard" in result:
            sync_to_firestore("option_sentiment", symbol, result["dashboard"])
    _analysis_cache[symbol] = {"at": time.monotonic(), "data": result}
    return result


def _get_analysis(symbol="^NSEI"):
    session = market_status()
    market_open = session["is_open"]
    source = CONFIG.get("data_source", "dhan")
    retries = CONFIG.get("nse_retry_count", 3)
    backoff = CONFIG.get("nse_retry_backoff_seconds", 2)
    expiry = CONFIG.get("expiry")
    if source == "mock":
        from step6_buildup import load_mock_snapshot
        raw = load_mock_snapshot(offset=1)
    else:
        try:
            raw = get_option_chain_dhan(symbol, expiry=expiry, retries=retries, backoff=backoff)
            source = "dhan"
        except Exception as dhan_error:
            try:
                raw = get_option_chain(symbol, retries=retries, backoff=backoff, expiry=expiry)
                source = "nse-fallback"
            except Exception as nse_error:
                raise RuntimeError(f"Dhan failed: {dhan_error}; NSE failed: {nse_error}") from nse_error

    df, spot = parse_option_chain(raw, expiry=expiry)
    live_spot = get_latest_spot(symbol)
    if live_spot is not None:
        spot = live_spot
        _record_live_spot(symbol, live_spot)
    save_clock = time.monotonic()
    if market_open and save_clock - _last_live_saved_at.get(symbol, 0) >= 10:
        save_live_snapshot(raw, symbol, source)
        _last_live_saved_at[symbol] = save_clock
    if market_open and save_clock - _last_saved_at.get(symbol, 0) >= 300:
        save_option_chain_snapshot(raw, symbol, source)
        _last_saved_at[symbol] = save_clock
    if source != "mock":
        now = time.monotonic()
        cached_price = _price_action_cache.get(symbol)
        if cached_price and now - cached_price["at"] < PRICE_ACTION_CACHE_SECONDS:
            price_action = cached_price["data"]
        else:
            price_action = analyze_price_action(symbol)
            _price_action_cache[symbol] = {"at": now, "data": price_action}
    else:
        price_action = None
    price_read = price_action["reading"] if price_action else "Mixed / range-bound price action"
    interval_minutes = max(1, int(CONFIG.get("trend_analysis_interval_minutes", 5)))
    now = time.monotonic()
    baseline = _trend_state["baseline_df"]
    elapsed = now - (_trend_state["baseline_at"] or now)
    trend_change = None
    if not market_open:
        flow = analyze_flow(baseline, df)
        dashboard = build_dashboard(df, df, spot)
        trend = _trend_state["last_trend"] or "Market closed"
        flow["alert"] = f"Market closed ({session['reason']}); no trend analysis or snapshots saved."
    elif baseline is None:
        _trend_state["baseline_df"] = df.copy()
        _trend_state["baseline_at"] = now
        flow = analyze_flow(None, df)
        dashboard = build_dashboard(df, df, spot)
        trend = "Waiting for analysis window"
    elif elapsed >= interval_minutes * 60:
        flow = analyze_flow(baseline, df)
        dashboard = build_dashboard(baseline, df, spot)
        trend = _market_trend(dashboard["combined_sentiment_read"], price_read)
        previous_trend = _trend_state["last_trend"]
        if previous_trend and trend != previous_trend:
            flow["alert"] = f"Trend changed: {previous_trend} -> {trend}"
            trend_change = (previous_trend, trend)
        _trend_state["last_trend"] = trend
        _trend_state["baseline_df"] = df.copy()
        _trend_state["baseline_at"] = now
    else:
        flow = analyze_flow(baseline, df)
        dashboard = build_dashboard(df, df, spot)
        trend = _trend_state["last_trend"] or "Waiting for analysis window"
        flow["alert"] = f"Next trend analysis in {max(0, int(interval_minutes * 60 - elapsed))} seconds"
    dashboard["trend_analysis"] = trend
    intraday_read = _intraday_price_read(symbol)
    structure = _price_structure_read(symbol)
    directional_read = _directional_price_read(symbol, intraday_read or price_read)
    confirmed_read = directional_read
    decision, trade_alert = _stable_signal(symbol, confirmed_read, dashboard, flow, spot)
    dashboard["market_read"] = decision["headline"]
    dashboard["combined_sentiment_read"] = decision["headline"]
    dashboard["final_signal"] = decision["signal"]
    dashboard["decision_headline"] = decision["headline"]
    dashboard["decision_note"] = decision["note"]
    dashboard["decision_signal"] = decision["signal"]
    dashboard["intraday_price_read"] = intraday_read or price_read
    dashboard["price_structure"] = structure
    dashboard["intraday_change_pct"] = _intraday_change_pct(symbol)
    dashboard["trade_alert"] = trade_alert
    dashboard["signal_refresh_seconds"] = max(30, int(CONFIG.get("trend_analysis_interval_minutes", 5)) * 60)
    dashboard["single_direction"] = decision["headline"]
    dashboard["trend_analysis_interval_minutes"] = interval_minutes
    dashboard["market_status"] = session
    if trend_change:
        previous_trend, changed_trend = trend_change
        save_daily_trend_change(
            symbol=symbol,
            trend=changed_trend,
            source=source,
            captured_at=datetime.now(),
            reason=f"Trend changed from {previous_trend} to {changed_trend}",
            spot_price=spot,
            pcr_oi=dashboard.get("pcr_oi"),
            pcr_volume=dashboard.get("pcr_volume"),
            max_pain=dashboard.get("max_pain"),
            call_oi_change=flow.get("call_oi_change"),
            put_oi_change=flow.get("put_oi_change"),
            call_volume_change=flow.get("call_volume_change"),
            put_volume_change=flow.get("put_volume_change"),
            alert_action=trade_alert.get("action"),
            confirmation_score=trade_alert.get("score"),
        )
    greeks = analyze_chain_greeks(df, spot, expiry=expiry)
    return _json_safe({"symbol": symbol, "source": source, "dashboard": dashboard, "price_action": price_action, "flow": flow, "greeks": greeks, "live_feed": get_live_status(symbol), "spot_source": "angel-one-live" if get_latest_spot(symbol) is not None else source, "spot_history": list(_chart_history[symbol])})


@APP.route("/api/trend")
@requires_auth
def api_trend():
    requested = (request.args.get("symbol") or os.environ.get("SYMBOL") or CONFIG.get("symbol", "NIFTY")).upper()
    symbol = requested if requested in SUPPORTED_SYMBOLS else "NIFTY"
    try:
        result = get_analysis(symbol=symbol)
        result["supported_symbols"] = SUPPORTED_SYMBOLS
        result["selected_symbol"] = symbol
        return jsonify(result)
    except Exception as exc:
        return jsonify({"error": str(exc)}), 503


@APP.route("/")
@requires_auth
def index():
    return render_template("dashboard.html")


def main():
    APP.run(host="0.0.0.0", port=int(os.environ.get("DASHBOARD_PORT", "5000")), debug=False)


if __name__ == "__main__":
    main()
