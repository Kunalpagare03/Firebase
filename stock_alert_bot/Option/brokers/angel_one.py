"""Optional Angel One SmartAPI live index feed."""

import logging
import os
import threading
import time
from collections import deque

log = logging.getLogger("brokers.angel_one")

_SYMBOL_TOKENS = {
    "NIFTY": ("1", "99926000"),
    "BANKNIFTY": ("1", "99926009"),
}

_latest_spot = {}
_latest_tick_at = {}
_feed_started = set()
_feed_errors = {}
_last_start_attempt = {}
_quote_sync_started = set()
_last_synced_tick_at = {}
_feed_started_at = {}
_tick_samples = {symbol: deque(maxlen=1000) for symbol in _SYMBOL_TOKENS}
_rapid_active = {symbol: False for symbol in _SYMBOL_TOKENS}
_rapid_events = {symbol: deque(maxlen=20) for symbol in _SYMBOL_TOKENS}
_published_history = {symbol: deque(maxlen=1800) for symbol in _SYMBOL_TOKENS}
_watchdog_started = set()
_shared_start_attempt = 0.0
_feed_lock = threading.Lock()


def _sync_live_quotes(symbol):
    """Publish the latest tick once per second for responsive mobile quotes."""
    from utils.firebase_sync import sync_to_firestore

    try:
        from firebase_admin import firestore
        existing = firestore.client().collection("live_quotes").document(symbol).get(timeout=3)
        for event in (existing.to_dict() or {}).get("rapid_activity_events", []):
            _rapid_events[symbol].append(event)
    except Exception as exc:
        log.debug("Could not load rapid-activity history for %s: %s", symbol, exc)

    while True:
        time.sleep(1)
        with _feed_lock:
            if symbol not in _feed_started:
                _quote_sync_started.discard(symbol)
                return
        with _feed_lock:
            tick_at = _latest_tick_at.get(symbol)
            spot = _latest_spot.get(symbol)
            samples = list(_tick_samples[symbol])
        if tick_at is None or spot is None or tick_at == _last_synced_tick_at.get(symbol):
            continue
        now_mono = time.monotonic()
        tick_epoch = time.time() - max(0.0, now_mono - tick_at)
        point = {"time": int(tick_epoch * 1000), "value": spot}
        if not _published_history[symbol] or _published_history[symbol][-1]["time"] != point["time"]:
            _published_history[symbol].append(point)
        recent = [(at, price) for at, price in samples if now_mono - at <= 10]
        tick_rate = len(recent) / 10.0
        move_pct = (
            abs(recent[-1][1] - recent[0][1]) / recent[0][1] * 100
            if len(recent) > 1 and recent[0][1] else 0.0
        )
        # This is a fast-tape proxy, not proof that high-frequency firms caused the move.
        rapid_activity = tick_rate >= 3.0 and move_pct >= 0.025
        published_at = time.time()
        if rapid_activity and not _rapid_active[symbol]:
            _rapid_events[symbol].append({
                "at": time.strftime("%H:%M:%S", time.localtime(published_at)),
                "spot": spot,
                "tick_rate_10s": round(tick_rate, 1),
                "move_10s_pct": round(move_pct, 4),
            })
        _rapid_active[symbol] = rapid_activity
        written = sync_to_firestore("live_quotes", symbol, {
            "symbol": symbol,
            "spot_price": spot,
            "updated_at": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(published_at)),
            "tick_at_epoch": tick_epoch,
            "published_at_epoch": published_at,
            "tick_rate_10s": round(tick_rate, 1),
            "move_10s_pct": round(move_pct, 4),
            "rapid_activity": rapid_activity,
            "activity_note": "Rapid price/tick activity" if rapid_activity else "No rapid activity detected",
            "rapid_activity_events": list(_rapid_events[symbol]),
            # Keep a compact rolling 30-minute one-second tape for proper
            # live 1m candles. The longer 1D/1W history remains in option_sentiment.
            "spot_history": list(_published_history[symbol]),
            "source": "angel_one",
        })
        if written:
            _last_synced_tick_at[symbol] = tick_at


def _get_shared_live_quote(symbol):
    """Read a recent Angel One tick published by another local feed worker."""
    try:
        from firebase_admin import firestore

        snapshot = firestore.client().collection("live_quotes").document(symbol).get(timeout=3)
        if not snapshot.exists:
            return None
        data = snapshot.to_dict() or {}
        if data.get("source") != "angel_one":
            return None
        updated_at = float(data.get("tick_at_epoch") or data.get("updated_at_epoch") or 0)
        age = time.time() - updated_at
        price = float(data.get("spot_price") or 0)
        if price > 0 and 0 <= age <= 30:
            return price, age
    except Exception as exc:
        log.debug("Could not read shared Angel One quote for %s: %s", symbol, exc)
    return None


def _credentials_available():
    names = ("ANGEL_API_KEY", "ANGEL_CLIENT_CODE", "ANGEL_PIN", "ANGEL_TOTP_SECRET")
    return all(os.environ.get(name) for name in names)


def _start_feed():
    from SmartApi import SmartConnect
    from SmartApi.smartWebSocketV2 import SmartWebSocketV2
    import pyotp

    api_key = os.environ["ANGEL_API_KEY"]
    client_code = os.environ["ANGEL_CLIENT_CODE"]
    pin = os.environ["ANGEL_PIN"]
    totp = pyotp.TOTP(os.environ["ANGEL_TOTP_SECRET"]).now()
    smart_api = SmartConnect(api_key=api_key)
    session = smart_api.generateSession(client_code, pin, totp)
    if not session or session.get("status") is not True:
        raise RuntimeError(f"Angel One login failed: {session}")

    data = session.get("data") or {}
    auth_token = data.get("jwtToken") or data.get("authToken")
    feed_token = smart_api.getfeedToken()
    if not auth_token or not feed_token:
        raise RuntimeError("Angel One login did not return feed credentials")

    socket = SmartWebSocketV2(auth_token, api_key, client_code, feed_token)

    for symbol in _SYMBOL_TOKENS:
        with _feed_lock:
            should_start_sync = symbol not in _quote_sync_started
            _quote_sync_started.add(symbol)
        if should_start_sync:
            threading.Thread(
                target=_sync_live_quotes, args=(symbol,),
                name=f"angel-quote-sync-{symbol.lower()}", daemon=True,
            ).start()

    def on_data(_, message):
        if not isinstance(message, dict):
            return
        symbol = next((name for name, (_, token) in _SYMBOL_TOKENS.items()
                       if str(message.get("token")) == token), None)
        if symbol is None:
            return
        value = message.get("last_traded_price")
        if value is not None:
            # SmartAPI V2 price is LTP * 100
            spot = float(value) / 100.0 if float(value) > 100000 else float(value)
            tick_at = time.monotonic()
            with _feed_lock:
                _latest_spot[symbol] = spot
                _latest_tick_at[symbol] = tick_at
                _tick_samples[symbol].append((tick_at, spot))
                while _tick_samples[symbol] and tick_at - _tick_samples[symbol][0][0] > 60:
                    _tick_samples[symbol].popleft()
            log.info(f"LIVE TICK [{symbol}]: {spot}")

    def on_open(wsapp):
        socket.subscribe(
            "stock-alert-indices",
            1,
            [{"exchangeType": 1, "tokens": [token for _, token in _SYMBOL_TOKENS.values()]}],
        )

    def on_error(_, error):
        log.warning("Angel One WebSocket error: %s", error)
        for symbol in _SYMBOL_TOKENS:
            _feed_errors[symbol] = str(error)

    def on_close(*_):
        log.warning("Angel One WebSocket closed for index feed")
        with _feed_lock:
            _feed_started.clear()

    socket.on_data = on_data
    socket.on_open = on_open
    socket.on_error = on_error
    socket.on_close = on_close
    socket.connect()


def start_live_feed(symbol="NIFTY"):
    """Start one shared Angel One session and subscribe to both indices."""
    global _shared_start_attempt
    symbol = symbol.upper()
    if symbol not in _SYMBOL_TOKENS or not _credentials_available():
        return False

    now = time.monotonic()
    with _feed_lock:
        if _feed_started:
            return True
        # A single session covers both indices; avoid parallel logins and rate limits.
        if now - _shared_start_attempt < 10:
            return False
        _feed_started.update(_SYMBOL_TOKENS)
        for name in _SYMBOL_TOKENS:
            _feed_started_at[name] = now
        _shared_start_attempt = now

    def run():
        try:
            log.info("Initializing shared Angel One index feed")
            _start_feed()
        except Exception as exc:
            log.warning("Angel One live feed unavailable: %s", exc)
            _feed_errors[symbol] = str(exc)
            with _feed_lock:
                _feed_started.clear()
            for name in _SYMBOL_TOKENS:
                _feed_errors[name] = str(exc)

    threading.Thread(target=run, name=f"angel-feed-{symbol}", daemon=True).start()
    with _feed_lock:
        for name in _SYMBOL_TOKENS:
            if name not in _watchdog_started:
                _watchdog_started.add(name)
                threading.Thread(target=_feed_watchdog, args=(name,), name=f"angel-watchdog-{name.lower()}", daemon=True).start()
    return True


def _feed_watchdog(symbol):
    """Reconnect a dropped or stale socket without waiting for the 5-minute scan."""
    while True:
        time.sleep(5)
        now = time.monotonic()
        with _feed_lock:
            started = symbol in _feed_started
            tick_at = _latest_tick_at.get(symbol)
            started_at = _feed_started_at.get(symbol, 0)
            stale = (tick_at is not None and now - tick_at > 15) or (
                tick_at is None and started and now - started_at > 30
            )
            if stale:
                _feed_started.clear()
        if not started or stale:
            start_live_feed(symbol)


def get_latest_spot(symbol="NIFTY"):
    """Return the latest Angel One tick, or None until the feed receives one."""
    symbol = symbol.upper()
    val = _latest_spot.get(symbol)
    tick_at = _latest_tick_at.get(symbol, 0)

    # If feed died (no ticks for 30s), try to restart
    if val is None or (time.monotonic() - tick_at > 30):
        start_live_feed(symbol)
        shared_quote = _get_shared_live_quote(symbol)
        if shared_quote:
            return shared_quote[0]
    return val


def get_live_status(symbol="NIFTY"):
    symbol = symbol.upper()
    tick_at = _latest_tick_at.get(symbol)
    age = time.monotonic() - tick_at if tick_at is not None else None
    if tick_at is None or age > 15:
        shared_quote = _get_shared_live_quote(symbol)
        if shared_quote:
            return {"connected": shared_quote[1] <= 5, "tick_age": round(shared_quote[1], 1), "error": None}
    return {
        "connected": symbol in _feed_started and age is not None and age <= 5,
        "tick_age": round(age, 1) if age is not None else None,
        "error": _feed_errors.get(symbol),
    }
