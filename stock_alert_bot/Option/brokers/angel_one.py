"""Optional Angel One SmartAPI live index and equity feed."""

import logging
import os
import threading
import time
from collections import deque

log = logging.getLogger("brokers.angel_one")

AUTH_FAILURE_RETRY_BASE_SECONDS = 15 * 60
AUTH_FAILURE_RETRY_MAX_SECONDS = 60 * 60
LIVE_QUOTE_PUBLISH_INTERVAL_SECONDS = 30
FAST_LIVE_QUOTE_PUBLISH_INTERVAL_SECONDS = 10
_FAST_LIVE_QUOTE_SYMBOLS = frozenset({"NIFTY", "BANKNIFTY", "SENSEX"})

_SYMBOL_TOKENS = {
    "NIFTY": ("1", "99926000"),
    "BANKNIFTY": ("1", "99926009"),
    "SENSEX": ("3", "99919000"),
    "RELIANCE": ("1", "2885"),
    "HDFCBANK": ("1", "1333"),
    "ICICIBANK": ("1", "4963"),
    "INFY": ("1", "1594"),
    "TCS": ("1", "11536"),
    "SBIN": ("1", "3045"),
}

_latest_spot = {}
_latest_tick_at = {}
_feed_started = set()
_feed_errors = {}
_last_start_attempt = {}
_quote_sync_started = set()
_last_synced_tick_at = {}
_last_quote_publish_attempt_at = {}
_feed_started_at = {}
_tick_samples = {symbol: deque(maxlen=1000) for symbol in _SYMBOL_TOKENS}
_rapid_active = {symbol: False for symbol in _SYMBOL_TOKENS}
_rapid_events = {symbol: deque(maxlen=20) for symbol in _SYMBOL_TOKENS}
_published_history = {symbol: deque(maxlen=1800) for symbol in _SYMBOL_TOKENS}
_watchdog_started = set()
_shared_start_attempt = 0.0
_auth_failure_count = 0
_auth_retry_after = 0.0
_feed_lock = threading.Lock()
_smart_api_session_lock = threading.Lock()
_smart_api_client = None
_smart_api_session_data = None


class AngelOneAuthenticationError(RuntimeError):
    pass


class _SensitiveSmartApiLogFilter(logging.Filter):
    def filter(self, record):
        try:
            return "request:" not in record.getMessage().lower()
        except Exception:
            return False


_SMARTAPI_LOG_FILTER = _SensitiveSmartApiLogFilter()


def _install_smartapi_log_filter():
    from logzero import logger as smartapi_logger

    logging.getLogger("SmartApi.smartConnect").addFilter(_SMARTAPI_LOG_FILTER)
    smartapi_logger.addFilter(_SMARTAPI_LOG_FILTER)


def get_smart_api_session():
    """Return the shared authenticated SmartAPI client and its session data."""
    global _smart_api_client, _smart_api_session_data

    with _smart_api_session_lock:
        if _smart_api_client is not None and _smart_api_session_data is not None:
            return _smart_api_client, _smart_api_session_data

        _install_smartapi_log_filter()
        from SmartApi import SmartConnect
        import pyotp

        api_key = os.environ.get("ANGEL_API_KEY", "")
        client_code = os.environ.get("ANGEL_CLIENT_CODE", "")
        pin = os.environ.get("ANGEL_PIN", "")
        totp_secret = os.environ.get("ANGEL_TOTP_SECRET", "")
        if not all((api_key, client_code, pin, totp_secret)):
            raise RuntimeError("Configure all Angel One credentials before requesting BSE option data.")

        client = SmartConnect(api_key=api_key)
        session = client.generateSession(
            client_code,
            pin,
            pyotp.TOTP(totp_secret).now(),
        )
        if not session or session.get("status") is not True:
            message = session.get("message") if isinstance(session, dict) else None
            error_code = session.get("errorcode") if isinstance(session, dict) else None
            code = f" ({error_code})" if error_code else ""
            raise AngelOneAuthenticationError(
                f"Angel One login failed{code}: {message or 'authentication was rejected'}"
            )

        data = session.get("data") or {}
        if not (data.get("jwtToken") or data.get("authToken")) or not client.getfeedToken():
            raise RuntimeError("Angel One login did not return feed credentials.")

        _smart_api_client = client
        _smart_api_session_data = data
        _clear_auth_backoff()
        return _smart_api_client, _smart_api_session_data


def _record_auth_failure():
    global _auth_failure_count, _auth_retry_after

    with _feed_lock:
        _auth_failure_count += 1
        retry_seconds = min(
            AUTH_FAILURE_RETRY_BASE_SECONDS * (2 ** min(_auth_failure_count - 1, 2)),
            AUTH_FAILURE_RETRY_MAX_SECONDS,
        )
        _auth_retry_after = time.monotonic() + retry_seconds
        return retry_seconds


def _clear_auth_backoff():
    global _auth_failure_count, _auth_retry_after

    with _feed_lock:
        _auth_failure_count = 0
        _auth_retry_after = 0.0
        for symbol in _SYMBOL_TOKENS:
            _feed_errors.pop(symbol, None)


def _smartapi_price(last_traded_price):
    return float(last_traded_price) / 100.0


def _subscription_tokens_by_exchange():
    tokens_by_exchange = {}
    for exchange_type, token in _SYMBOL_TOKENS.values():
        tokens_by_exchange.setdefault(int(exchange_type), []).append(token)
    return [
        {"exchangeType": exchange_type, "tokens": tokens}
        for exchange_type, tokens in tokens_by_exchange.items()
    ]


def _should_publish_live_quote(symbol, tick_at, now_monotonic):
    if tick_at is None or tick_at == _last_synced_tick_at.get(symbol):
        return False
    last_attempt = _last_quote_publish_attempt_at.get(symbol)
    interval = (
        FAST_LIVE_QUOTE_PUBLISH_INTERVAL_SECONDS
        if symbol in _FAST_LIVE_QUOTE_SYMBOLS
        else LIVE_QUOTE_PUBLISH_INTERVAL_SECONDS
    )
    if (
        last_attempt is not None
        and now_monotonic - last_attempt < interval
    ):
        return False
    _last_quote_publish_attempt_at[symbol] = now_monotonic
    return True


def _sync_live_quotes(symbol):
    """Publish the latest tick at a quota-safe interval for responsive mobile quotes."""
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
        if spot is None:
            continue
        now_mono = time.monotonic()
        if not _should_publish_live_quote(symbol, tick_at, now_mono):
            continue
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
        if price > 0 and 0 <= age <= LIVE_QUOTE_PUBLISH_INTERVAL_SECONDS * 2:
            return price, age
    except Exception as exc:
        log.debug("Could not read shared Angel One quote for %s: %s", symbol, exc)
    return None


def _credentials_available():
    names = ("ANGEL_API_KEY", "ANGEL_CLIENT_CODE", "ANGEL_PIN", "ANGEL_TOTP_SECRET")
    return all(os.environ.get(name) for name in names)


def _is_auth_or_rate_limit_error(error):
    message = str(error).lower()
    return any(token in message for token in (
        "access denied",
        "exceeding access rate",
        "rate limit",
        "too many requests",
        "authentication",
        "invalid totp",
        "invalid pin",
        "login failed",
    ))


def _start_feed():
    _install_smartapi_log_filter()

    from SmartApi.smartWebSocketV2 import SmartWebSocketV2
    smart_api, data = get_smart_api_session()
    api_key = os.environ["ANGEL_API_KEY"]
    client_code = os.environ["ANGEL_CLIENT_CODE"]
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
            spot = _smartapi_price(value)
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
            "stock-alert-underlyings",
            1,
            _subscription_tokens_by_exchange(),
        )

    def on_error(_, error):
        log.warning("Angel One WebSocket error: %s", error)
        for symbol in _SYMBOL_TOKENS:
            _feed_errors[symbol] = str(error)

    def on_close(*_):
        log.warning("Angel One WebSocket closed for underlying feed")
        with _feed_lock:
            _feed_started.clear()

    socket.on_data = on_data
    socket.on_open = on_open
    socket.on_error = on_error
    socket.on_close = on_close
    socket.connect()


def start_live_feed(symbol="NIFTY"):
    """Start one shared Angel One session for supported indices and equities."""
    global _shared_start_attempt
    symbol = symbol.upper()
    if symbol not in _SYMBOL_TOKENS:
        return False
    if not _credentials_available():
        missing = [
            name for name in ("ANGEL_API_KEY", "ANGEL_CLIENT_CODE", "ANGEL_PIN", "ANGEL_TOTP_SECRET")
            if not os.environ.get(name)
        ]
        with _feed_lock:
            _feed_errors[symbol] = "Missing required Angel One configuration: " + ", ".join(missing)
        return False

    now = time.monotonic()
    with _feed_lock:
        if _feed_started:
            return True
        if now < _auth_retry_after:
            return False
        # A single session covers all configured underlyings; avoid parallel logins.
        if now - _shared_start_attempt < 10:
            return False
        _feed_started.update(_SYMBOL_TOKENS)
        for name in _SYMBOL_TOKENS:
            _feed_started_at[name] = now
        _shared_start_attempt = now

    def run():
        try:
            log.info("Initializing shared Angel One index and equity feed")
            _start_feed()
        except AngelOneAuthenticationError as exc:
            retry_seconds = _record_auth_failure()
            error = str(exc)
            log.error(
                "Angel One authentication failed; retry paused for %d seconds: %s",
                retry_seconds,
                error,
            )
            with _feed_lock:
                _feed_started.clear()
                for name in _SYMBOL_TOKENS:
                    _feed_errors[name] = error
        except Exception as exc:
            if _is_auth_or_rate_limit_error(exc):
                retry_seconds = _record_auth_failure()
                log.error(
                    "Angel One authentication/rate limit blocked the live feed; "
                    "retry paused for %d seconds: %s",
                    retry_seconds,
                    exc,
                )
            else:
                log.warning("Angel One live feed unavailable: %s", exc)
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

    if val is not None and time.monotonic() - tick_at <= 30:
        return val

    shared_quote = _get_shared_live_quote(symbol)
    if shared_quote:
        return shared_quote[0]

    start_live_feed(symbol)
    return val


def get_live_status(symbol="NIFTY"):
    symbol = symbol.upper()
    tick_at = _latest_tick_at.get(symbol)
    age = time.monotonic() - tick_at if tick_at is not None else None
    if tick_at is None or age > 15:
        shared_quote = _get_shared_live_quote(symbol)
        if shared_quote:
            return {
                "connected": shared_quote[1] <= LIVE_QUOTE_PUBLISH_INTERVAL_SECONDS * 2,
                "tick_age": round(shared_quote[1], 1),
                "error": None,
            }
    return {
        "connected": symbol in _feed_started and age is not None and age <= 5,
        "tick_age": round(age, 1) if age is not None else None,
        "error": _feed_errors.get(symbol),
    }
