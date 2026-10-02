import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime, time as dt_time
import os
import re
import concurrent.futures
import json
import threading
from utils.firebase_sync import sync_to_firestore
from utils.news_engine import fetch_market_news
from utils.sector_map import get_sector, SECTOR_MAP
import time
import firebase_admin
from firebase_admin import credentials, firestore

# Comprehensive Watchlist: deduplicated so progress and results represent the
# actual 2,800+ stock universe, not repeated sector entries.
def normalize_yahoo_symbol(symbol):
    """Remove NSE/BSE series suffixes that Yahoo treats as part of the ticker."""
    return re.sub(r"-(?:ST|BE|BZ|SM)\.(NS|BO)$", r".\1", str(symbol).upper())


SYMBOL_SECTOR = {
    normalize_yahoo_symbol(symbol): sector
    for sector, symbols in SECTOR_MAP.items()
    for symbol in symbols
}
WATCHLIST = list(dict.fromkeys(normalize_yahoo_symbol(sym) for sector in SECTOR_MAP.values() for sym in sector))
SWING_LOOKBACK_DAYS = 7
POSITIONAL_LOOKBACK_DAYS = 15
# A full 2,800+ share scan is network and CPU intensive. Keep the free/local
# default intentionally modest; it can be raised with STOCK_SCAN_WORKERS.
SCAN_WORKERS = int(os.environ.get("STOCK_SCAN_WORKERS", "16"))
YAHOO_TIMEOUT_SECONDS = float(os.environ.get("STOCK_SCAN_TIMEOUT_SECONDS", "10"))
_status_publish_lock = threading.Lock()
_status_publish_event = threading.Event()
_latest_status_update = None
_status_publisher_started = False


def build_sectioned_report(scan_data):
    """Create a mobile-friendly report without mixing intraday calls into holdings.

    The Android client consumes the original ``intraday``, ``swing`` and
    ``positional`` lists.  This additional view lets a client group longer
    horizon candidates by sector without accidentally presenting an intraday
    setup as a swing or positional idea.
    """
    sections = {
        name: list(scan_data.get(name, []) or [])
        for name in ("swing", "positional")
    }
    by_sector = {}
    for items in sections.values():
        for item in items:
            sector = str(item.get("sector") or "UNCLASSIFIED").upper()
            by_sector.setdefault(sector, []).append(item)
    return {
        "sections": sections,
        "stocks_by_sector": by_sector,
        "market_news": list(scan_data.get("market_news", []) or []),
    }


def to_native_values(value):
    """Convert NumPy scalar values into Firestore/JSON native values."""
    if isinstance(value, dict):
        return {key: to_native_values(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [to_native_values(item) for item in value]
    if isinstance(value, np.generic):
        return value.item()
    return value

def calculate_rsi(series, period=14):
    if len(series) < period + 1: return 50
    delta = series.diff()
    gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
    rs = gain / loss if loss.iloc[-1] != 0 else 100
    return 100 - (100 / (1 + rs))

def analyze_candle_and_chart(df):
    """Return a plain-language latest-candle read and a simple trend structure."""
    if len(df) < 25:
        return "Insufficient candles", "Insufficient price history", []
    current = df.iloc[-1]
    previous = df.iloc[-2]
    body = abs(float(current["Close"] - current["Open"]))
    full_range = max(float(current["High"] - current["Low"]), 0.01)
    upper_wick = float(current["High"] - max(current["Open"], current["Close"]))
    lower_wick = float(min(current["Open"], current["Close"]) - current["Low"])

    if (current["Close"] > current["Open"] and previous["Close"] < previous["Open"]
            and current["Open"] <= previous["Close"] and current["Close"] >= previous["Open"]):
        candle_pattern = "Bullish engulfing"
    elif (current["Close"] < current["Open"] and previous["Close"] > previous["Open"]
            and current["Open"] >= previous["Close"] and current["Close"] <= previous["Open"]):
        candle_pattern = "Bearish engulfing"
    elif lower_wick >= max(body * 2.0, full_range * 0.45) and upper_wick <= full_range * 0.2:
        candle_pattern = "Hammer / long lower rejection"
    elif upper_wick >= max(body * 2.0, full_range * 0.45) and lower_wick <= full_range * 0.2:
        candle_pattern = "Shooting star / upper rejection"
    elif body <= full_range * 0.1:
        candle_pattern = "Doji / indecision"
    else:
        candle_pattern = "Bullish candle" if current["Close"] >= current["Open"] else "Bearish candle"

    recent = df.tail(5)
    prior_20 = df["High"].iloc[-21:-1]
    prior_low_20 = df["Low"].iloc[-21:-1]
    if len(prior_20) and current["Close"] > prior_20.max():
        chart_pattern = "20-day range breakout"
    elif len(prior_low_20) and current["Close"] < prior_low_20.min():
        chart_pattern = "20-day range breakdown"
    elif recent["High"].is_monotonic_increasing and recent["Low"].is_monotonic_increasing:
        chart_pattern = "Higher highs and higher lows"
    elif recent["High"].is_monotonic_decreasing and recent["Low"].is_monotonic_decreasing:
        chart_pattern = "Lower highs and lower lows"
    elif current["Close"] > df["ema20"].iloc[-1] > df["ema50"].iloc[-1]:
        chart_pattern = "Rising trend above EMA20 / EMA50"
    elif current["Close"] < df["ema20"].iloc[-1] < df["ema50"].iloc[-1]:
        chart_pattern = "Falling trend below EMA20 / EMA50"
    else:
        chart_pattern = "Range / mixed structure"

    candles = []
    for index, row in df.tail(25).iterrows():
        timestamp = pd.Timestamp(index)
        candles.append({
            "time": int(timestamp.timestamp() * 1000),
            "open": round(float(row["Open"]), 4), "high": round(float(row["High"]), 4),
            "low": round(float(row["Low"]), 4), "close": round(float(row["Close"]), 4),
            "volume": int(row["Volume"]) if pd.notna(row["Volume"]) else 0,
        })
    return candle_pattern, chart_pattern, candles


def detect_base_pattern(df):
    """Find conservative CAN SLIM style cup/handle, undercut W and flat bases."""
    if len(df) < 45:
        return None
    close = df["Close"].astype(float).to_numpy()
    high = df["High"].astype(float).to_numpy()
    low = df["Low"].astype(float).to_numpy()
    volume = df["Volume"].astype(float).to_numpy()
    latest = close[-1]
    avg_volume = float(np.nanmean(volume[-21:-1])) if len(volume) > 21 else 0.0
    volume_ratio = float(volume[-1] / avg_volume) if avg_volume > 0 else 0.0
    candidates = []

    # Flat base: a prior advance followed by a tight 20–35 session shelf.
    base_len = min(25, len(close) - 20)
    shelf_high = float(np.max(high[-base_len - 1:-1]))
    shelf_low = float(np.min(low[-base_len - 1:-1]))
    prior_start = close[-base_len - 21] if len(close) > base_len + 21 else close[0]
    prior_gain = (shelf_high / prior_start - 1) if prior_start > 0 else 0.0
    shelf_range = (shelf_high - shelf_low) / max(shelf_high, 0.01)
    if prior_gain >= 0.20 and shelf_range <= 0.15 and latest >= shelf_high * 0.94:
        breakout = bool(latest > shelf_high * 1.005 and volume_ratio >= 1.5)
        candidates.append({
            "name": "Flat base", "score": 4 if breakout else 3,
            "buy_point": round(shelf_high, 2), "breakout": breakout,
            "description": "tight shelf after prior advance; " + ("breakout volume confirmed" if breakout else "watch for a close above the shelf"),
        })

    # Cup with handle: near-equal rims, a rounded middle low, then a shallow handle.
    cup = close[-70:]
    cup_high = high[-70:]
    cup_low = low[-70:]
    n = len(cup)
    left_end = max(8, int(n * 0.24))
    right_start, right_end = int(n * 0.55), int(n * 0.84)
    left_rim = float(np.max(cup_high[:left_end]))
    right_rim = float(np.max(cup_high[right_start:right_end])) if right_end > right_start else 0.0
    bottom_index = int(np.argmin(cup_low))
    bottom = float(np.min(cup_low))
    depth = (left_rim - bottom) / max(left_rim, 0.01)
    rim_gap = abs(right_rim - left_rim) / max(left_rim, 0.01)
    handle_low = float(np.min(cup_low[-10:]))
    handle_high = float(np.max(cup_high[-10:-1])) if n > 10 else float(np.max(cup_high[:-1]))
    handle_pullback = (right_rim - handle_low) / max(right_rim, 0.01)
    rounded_bottom = int(n * 0.25) <= bottom_index <= int(n * 0.72)
    if (left_rim > 0 and right_rim > 0 and rounded_bottom and 0.08 <= depth <= 0.35
            and rim_gap <= 0.06 and bottom < handle_low and 0.02 <= handle_pullback <= 0.15
            and handle_high <= right_rim * 1.03):
        buy_point = max(right_rim, handle_high)
        breakout = bool(latest > buy_point * 1.005 and volume_ratio >= 1.5)
        candidates.append({
            "name": "Cup with handle", "score": 4 if breakout else 3,
            "buy_point": round(buy_point, 2), "breakout": breakout,
            "description": "near-equal cup rims with a shallow handle; " + ("breakout volume confirmed" if breakout else "watch for a close above the handle"),
        })

    # Undercut double bottom: second trough dips below the first, then price
    # must reclaim the intervening peak before it is treated as a breakout.
    window = low[-45:]
    highs_window = high[-45:]
    pivots = [i for i in range(2, len(window) - 2)
              if window[i] <= min(window[i - 2:i]) and window[i] <= min(window[i + 1:i + 3])]
    pairs = []
    for left_i in pivots:
        for right_i in pivots:
            gap = right_i - left_i
            if not 8 <= gap <= 32:
                continue
            first, second = float(window[left_i]), float(window[right_i])
            undercut = (first - second) / max(first, 0.01)
            if 0.003 <= undercut <= 0.08:
                neckline = float(np.max(highs_window[left_i:right_i + 1]))
                if second < neckline * 0.97:
                    pairs.append((right_i, neckline, second))
    if pairs:
        _, neckline, second_low = max(pairs, key=lambda pair: pair[0])
        breakout = bool(latest > neckline * 1.005 and volume_ratio >= 1.5)
        if latest >= second_low * 1.03:
            candidates.append({
                "name": "Undercut double bottom", "score": 4 if breakout else 3,
                "buy_point": round(neckline, 2), "breakout": breakout,
                "description": "second low undercut the first; " + ("neckline breakout volume confirmed" if breakout else "watch for a neckline reclaim"),
            })

    return max(candidates, key=lambda item: (item["score"], item["breakout"])) if candidates else None


def analyze_stock(symbol):
    try:
        ticker = yf.Ticker(symbol)
        df = None
        for attempt in range(3):
            try:
                df = ticker.history(period="1y", interval="1d", timeout=YAHOO_TIMEOUT_SECONDS)
                break
            except Exception as fetch_error:
                err_msg = str(fetch_error).lower()
                is_rate_limit = "ratelimit" in type(fetch_error).__name__.lower() or "429" in err_msg or "too many requests" in err_msg
                if not is_rate_limit or attempt == 2:
                    raise
                time.sleep(2 ** attempt)
        if df is None:
            return None, None, None, None, "No daily history returned"
        if df.empty or len(df) < 200: return None, None, None, None, "No usable daily history"

        prev = df.iloc[-2]
        row_date = pd.Timestamp(df.index[-1]).date()
        current_dt = datetime.now()
        today = current_dt.date()
        market_open = current_dt.weekday() < 5 and dt_time(9, 15) <= current_dt.time() < dt_time(15, 30)
        is_live_bar = row_date == today and market_open
        for p in [20, 50, 100, 200]:
            df[f'ema{p}'] = df['Close'].ewm(span=p, adjust=False).mean()

        df['rsi'] = calculate_rsi(df['Close'])
        # Read the latest row after adding indicators because ``iloc`` returns
        # a row snapshot that does not acquire columns added later.
        curr = df.iloc[-1]
        entry = round(curr['Close'], 2)
        high_52w = df['High'].max()
        low_52w = df['Low'].min()
        candle_pattern, chart_pattern, candle_history = analyze_candle_and_chart(df)
        base_pattern = detect_base_pattern(df)
        if base_pattern:
            chart_pattern = f"{base_pattern['name']} · {'breakout' if base_pattern['breakout'] else 'forming'}"

        change_pct = ((curr['Close'] - prev['Close']) / prev['Close']) * 100
        avg_vol = df['Volume'].iloc[:-1].tail(20).mean()
        if is_live_bar:
            now = datetime.now().time()
            elapsed = max(0.02, min(1.0, ((now.hour * 60 + now.minute) - (9 * 60 + 15)) / 375))
            vol_ratio = curr['Volume'] / (avg_vol * elapsed) if avg_vol > 0 else 0
        else:
            vol_ratio = curr['Volume'] / avg_vol if avg_vol > 0 else 0
        swing_window = df.tail(SWING_LOOKBACK_DAYS)
        positional_window = df.tail(POSITIONAL_LOOKBACK_DAYS)
        swing_return = ((curr['Close'] / swing_window['Close'].iloc[0]) - 1) * 100
        positional_return = ((curr['Close'] / positional_window['Close'].iloc[0]) - 1) * 100

        # Institutional Rating
        score = 0
        if curr['Close'] > df['ema20'].iloc[-1]: score += 2
        if df['ema20'].iloc[-1] > df['ema50'].iloc[-1]: score += 1
        if vol_ratio > 2.0: score += 2
        if 40 < curr['rsi'] < 70: score += 2
        if curr['Close'] > high_52w * 0.98: score += 3

        # Independent bearish score makes confirmed sell-side setups visible,
        # instead of silently filtering the whole universe with long-only rules.
        bear_score = 0
        if curr['Close'] < df['ema20'].iloc[-1]: bear_score += 2
        if df['ema20'].iloc[-1] < df['ema50'].iloc[-1]: bear_score += 1
        if vol_ratio > 2.0: bear_score += 2
        if 30 < curr['rsi'] < 60: bear_score += 2
        if curr['Close'] < low_52w * 1.02: bear_score += 3

        rating = min(10, score)
        bear_rating = min(10, bear_score)
        setup_score = max(rating, bear_rating, base_pattern["score"] if base_pattern else 0)
        base_info = {
            "symbol": symbol, "sector": SYMBOL_SECTOR.get(symbol, get_sector(symbol)), "price": entry,
            "target_price": round(entry * 1.02, 2), "sl_price": round(entry * 0.98, 2),
            "change": round(change_pct, 2), "rating": f"{rating}/10", "vol_ratio": round(vol_ratio, 1),
            "bear_rating": bear_rating, "setup_score": setup_score,
            "base_pattern": base_pattern["name"] if base_pattern else "",
            "base_score": base_pattern["score"] if base_pattern else 0,
            "base_buy_point": base_pattern["buy_point"] if base_pattern else 0,
            "base_breakout": base_pattern["breakout"] if base_pattern else False,
            "traded_volume": int(curr['Volume']) if pd.notna(curr['Volume']) else 0,
            "average_volume": int(avg_vol) if pd.notna(avg_vol) else 0,
            "candle_pattern": candle_pattern, "chart_pattern": chart_pattern,
            "candles": candle_history,
            "ema20": round(df['ema20'].iloc[-1], 2), "ema200": round(df['ema200'].iloc[-1], 2),
            "rsi14": round(float(curr['rsi']), 1), "bar_date": row_date.strftime("%Y-%m-%d"),
            "live_bar": is_live_bar,
            "swing_return_7d": round(swing_return, 2), "positional_return_15d": round(positional_return, 2),
            "justification": (
                f"{base_pattern['name']} base: {base_pattern['description']}; buy point {base_pattern['buy_point']}; "
                if base_pattern else ""
            ) + f"{candle_pattern}; {chart_pattern}; {round(vol_ratio, 1)}x relative volume."
        }

        # Long and short conditions are evaluated independently; only aligned,
        # price-confirmed candidates are labeled as watch setups.
        intraday_buy = is_live_bar and change_pct > 0.1 and vol_ratio >= 0.8 and curr['Close'] > df['ema20'].iloc[-1]
        intraday_sell = is_live_bar and change_pct < -0.1 and vol_ratio >= 0.8 and curr['Close'] < df['ema20'].iloc[-1]
        intra = ({**base_info, "recommendation": "INTRADAY BUY WATCH", "holding_window": "Intraday", "analysis_side": "Bullish"}
                 if intraday_buy else
                 {**base_info, "recommendation": "INTRADAY SELL WATCH", "holding_window": "Intraday", "analysis_side": "Bearish"}
                 if intraday_sell else None)

        swing_buy = (rating >= 4 and swing_return > 0 and curr['Close'] > df['ema20'].iloc[-1]) or bool(base_pattern and base_pattern["breakout"])
        swing_sell = bear_rating >= 4 and swing_return < 0 and curr['Close'] < df['ema20'].iloc[-1]
        swing = ({**base_info, "recommendation": "SWING BUY WATCH", "holding_window": "Up to 7 trading days", "analysis_side": "Bullish"}
                 if swing_buy else
                 {**base_info, "recommendation": "SWING SELL WATCH", "holding_window": "Up to 7 trading days", "analysis_side": "Bearish"}
                 if swing_sell else None)

        pos_buy = rating >= 6 and positional_return > 0 and curr['Close'] > df['ema50'].iloc[-1] and df['ema50'].iloc[-1] > df['ema200'].iloc[-1] * 0.95
        pos_sell = bear_rating >= 6 and positional_return < 0 and curr['Close'] < df['ema50'].iloc[-1] and df['ema50'].iloc[-1] < df['ema200'].iloc[-1] * 1.05
        pos = ({**base_info, "recommendation": "POS BUY WATCH", "holding_window": "Up to 15 trading days", "analysis_side": "Bullish"}
               if pos_buy else
               {**base_info, "recommendation": "POS SELL / REDUCE WATCH", "holding_window": "Up to 15 trading days", "analysis_side": "Bearish"}
               if pos_sell else None)

        watch = None
        if not any((intra, swing, pos)) and (max(rating, bear_rating) >= 3 or base_pattern):
            side = "Bullish" if base_pattern else "Bullish" if rating > bear_rating else "Bearish" if bear_rating > rating else "Mixed"
            reason = (
                f"{base_pattern['name']} base watch; {base_pattern['description']}; buy point {base_pattern['buy_point']}"
                if base_pattern else f"{side} watch: candle/price/volume confirmation needed"
            )
            watch = {**base_info, "recommendation": f"{base_pattern['name'].upper()} WATCH" if base_pattern else f"{side.upper()} WATCH", "holding_window": "Watchlist", "analysis_side": side, "justification": reason}
        return intra, swing, pos, watch, None
    except Exception as exc:
        detail = f" ({exc.args[0]})" if isinstance(exc, KeyError) and exc.args else ""
        return None, None, None, None, f"{type(exc).__name__}{detail}"

def _publish_scan_doc(db, user_id, document, payload):
    if document == "status":
        payload = {**payload, "updated_at_epoch": time.time()}
    if user_id:
        db.collection("users").document(user_id).collection("stock_scans").document(document).set(payload, merge=True, retry=None, timeout=8)
    else:
        sync_to_firestore("stock_scans", document, payload)


def _status_publisher():
    """Publish the latest progress off-thread so Firestore latency can't stall scanning."""
    global _latest_status_update
    while True:
        _status_publish_event.wait()
        with _status_publish_lock:
            update = _latest_status_update
            _latest_status_update = None
            _status_publish_event.clear()
        if update is None:
            continue
        db, user_id, payload, completed = update
        try:
            _publish_scan_doc(db, user_id, "status", payload)
        except Exception as error:
            print(f"Could not publish scan status: {type(error).__name__}")
        finally:
            completed.set()


def _queue_scan_status(db, user_id, payload, wait_seconds=0):
    """Keep only the newest pending progress update if the network is slow."""
    global _latest_status_update, _status_publisher_started
    completed = threading.Event()
    with _status_publish_lock:
        if _latest_status_update is not None:
            _latest_status_update[3].set()
        _latest_status_update = (db, user_id, payload, completed)
        _status_publish_event.set()
        if not _status_publisher_started:
            threading.Thread(target=_status_publisher, name="stock-scan-status", daemon=True).start()
            _status_publisher_started = True
    if wait_seconds:
        completed.wait(wait_seconds)
    return completed


def run_live_scan(user_id=None):
    start_time = time.time()

    # 1. Initialize Firestore for History Rotation
    if not firebase_admin._apps:
        possible_creds = [
            os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "service-account.json")),
            os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "service-account.json")),
            "service-account.json"
        ]
        cred_path = next((p for p in possible_creds if os.path.exists(p)), None)
        if cred_path:
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        else:
            firebase_admin.initialize_app()
    db = firestore.client()

    total_symbols = len(WATCHLIST)
    start_str = datetime.now().strftime("%H:%M:%S")
    _queue_scan_status(db, user_id, {
        "status": f"Scanning 0/{total_symbols} stocks...",
        "is_scanning": True,
        "progress": 0,
        "start_time": start_str
    })

    print(f"Executing Global Scan: {total_symbols} symbols...")
    intra_list, swing_list, pos_list, watch_list = [], [], [], []
    failure_counts = {}
    analyzed_count = 0

    completed = 0
    last_update_pct = -1

    with concurrent.futures.ThreadPoolExecutor(max_workers=SCAN_WORKERS) as executor:
        futures = {executor.submit(analyze_stock, sym): sym for sym in WATCHLIST}
        for future in concurrent.futures.as_completed(futures):
            completed += 1
            i, s, p, watch, failure = future.result()
            if failure:
                failure_counts[failure] = failure_counts.get(failure, 0) + 1
            else:
                analyzed_count += 1
            if i: intra_list.append(i)
            if s: swing_list.append(s)
            if p: pos_list.append(p)
            if watch: watch_list.append(watch)

            pct = int((completed / total_symbols) * 100)
            # Publish each percentage point so the mobile progress indicator
            # moves promptly instead of appearing stuck at 0% for a long scan.
            if pct > last_update_pct and (pct % 5 == 0 or completed == total_symbols):
                last_update_pct = pct
                _queue_scan_status(db, user_id, {
                    "status": f"Scanning {completed}/{total_symbols} ({pct}%)...",
                    "is_scanning": True,
                    "progress": pct,
                    "start_time": start_str
                })
                if completed == total_symbols or pct % 5 == 0:
                    print(f"Scan progress: {completed}/{total_symbols} ({pct}%)")

    def get_score(x):
        try:
            return int(x.get('setup_score', str(x.get('rating', '0')).split('/')[0]))
        except Exception:
            return 0

    # Sort & cap to top 50 per category to stay below Firestore's document limit.
    intra_list = sorted(intra_list, key=get_score, reverse=True)[:50]
    swing_list = sorted(swing_list, key=get_score, reverse=True)[:50]
    pos_list = sorted(pos_list, key=get_score, reverse=True)[:50]
    watch_list = sorted(watch_list, key=lambda x: (get_score(x), x.get("vol_ratio", 0)), reverse=True)[:50]

    def report_rows(items):
        return [{key: value for key, value in item.items() if key != "candles"} for item in items]

    new_report = {
        "timestamp": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
        "intraday": report_rows(intra_list),
        "swing": report_rows(swing_list),
        "positional": report_rows(pos_list),
        "watchlist": report_rows(watch_list),
        "scan_diagnostics": {
            "universe_size": total_symbols,
            "analyzed": analyzed_count,
            "failed": total_symbols - analyzed_count,
            "qualified": len(intra_list) + len(swing_list) + len(pos_list),
            "watchlist_count": len(watch_list),
            "failure_types": failure_counts,
        }
    }
    new_report.update(build_sectioned_report(new_report))
    new_report = to_native_values(new_report)

    def mobile_row(item):
        return {
            "symbol": item["symbol"], "entry": item["price"], "close": item["price"],
            "change_pct": item["change"], "volume_ratio": item["vol_ratio"],
            "rsi14": 0, "pattern": item.get("holding_window", "INTRADAY"),
            "score": get_score(item), "signals": [item.get("justification", "")],
            "target_2pct": round(item["price"] * 1.02, 2), "stop_loss": item["sl_price"],
            "risk_reward": round((item["target_price"] - item["price"]) / max(item["price"] - item["sl_price"], .01), 2), "recommendation": item["recommendation"],
            "sector": item.get("sector", ""),
            "rsi14": item.get("rsi14", 0), "bar_date": item.get("bar_date", ""),
            "volume_ratio": item.get("vol_ratio", 0), "live_bar": item.get("live_bar", False),
            "target_price": item.get("target_price", 0), "ema20": item.get("ema20", 0),
            "traded_volume": item.get("traded_volume", 0), "average_volume": item.get("average_volume", 0),
            "candle_pattern": item.get("candle_pattern", ""), "chart_pattern": item.get("chart_pattern", ""),
            "base_pattern": item.get("base_pattern", ""), "base_score": item.get("base_score", 0),
            "base_buy_point": item.get("base_buy_point", 0), "base_breakout": item.get("base_breakout", False),
            "analysis_side": item.get("analysis_side", "Mixed"),
            "candles": item.get("candles", []),
        }
    mobile_results = to_native_values([mobile_row(item) for item in intra_list + swing_list + pos_list + watch_list])

    report_dir = os.path.join(os.path.dirname(__file__), "reports")
    os.makedirs(report_dir, exist_ok=True)
    local_report = os.path.join(report_dir, "latest_live_scan.json")
    with open(local_report, "w", encoding="utf-8") as report_file:
        json.dump({**new_report, "latest_results": mobile_results}, report_file, ensure_ascii=False, indent=2)
    print(f"Local scan report saved: {local_report}")

    # 2. ROTATION LOGIC: Save last 3 scans
    try:
        if user_id:
            _publish_scan_doc(db, user_id, "comprehensive", new_report)
            _publish_scan_doc(db, user_id, "latest", {"timestamp": new_report["timestamp"], "results": mobile_results})
            print(f"User scan published privately for {user_id}.")
        else:
            current_latest_snap = db.collection("stock_scans").document("comprehensive").get(retry=None, timeout=8)
            current_h1_snap = db.collection("stock_scans").document("history_1").get(retry=None, timeout=8)
            current_latest = current_latest_snap.to_dict()
            current_h1 = current_h1_snap.to_dict()

            if current_h1:
                db.collection("stock_scans").document("history_2").set(current_h1, retry=None, timeout=8)
            if current_latest:
                db.collection("stock_scans").document("history_1").set(current_latest, retry=None, timeout=8)

            sync_to_firestore("stock_scans", "comprehensive", new_report)
            sync_to_firestore("stock_scans", "latest", {"timestamp": new_report["timestamp"], "results": mobile_results})
            print("Rotation Complete: Latest, History_1, History_2 updated.")
    except Exception as e:
        print(f"Rotation failed: {e}")
        if not user_id:
            sync_to_firestore("stock_scans", "comprehensive", new_report)
            sync_to_firestore("stock_scans", "latest", {"timestamp": new_report["timestamp"], "results": mobile_results})

    _queue_scan_status(db, user_id, {
        "status": "Idle / Complete",
        "is_scanning": False,
        "progress": 100,
        "last_scan_time": new_report["timestamp"],
        "results": f"{len(intra_list)} Intraday, {len(swing_list)} Swing, {len(pos_list)} Positional, {len(watch_list)} Watch | {analyzed_count}/{total_symbols} symbols analyzed"
    }, wait_seconds=8)
    print(f"Scan Finished: {new_report['timestamp']}")

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Scan stocks and publish results to the requesting account.")
    parser.add_argument("--user-id", default=None)
    args = parser.parse_args()
    # The listener starts the UI in a scanning state before this process begins.
    # Never leave that state behind if an unexpected top-level error occurs.
    try:
        run_live_scan(args.user_id)
    except Exception as error:
        print(f"Scan failed: {error}")
        try:
            db = firestore.client() if firebase_admin._apps else None
            if db:
                _queue_scan_status(db, args.user_id, {
                    "status": "Idle / Scan failed",
                    "is_scanning": False,
                    "progress": 0,
                    "last_scan_time": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
                    "results": f"Scan failed: {type(error).__name__}"
                }, wait_seconds=8)
            else:
                sync_to_firestore("stock_scans", "status", {
                "status": "Idle / Scan failed",
                "is_scanning": False,
                "progress": 0,
                "last_scan_time": datetime.now().strftime("%d-%m-%Y %H:%M:%S"),
                "results": f"Scan failed: {type(error).__name__}"
                })
        finally:
            raise
