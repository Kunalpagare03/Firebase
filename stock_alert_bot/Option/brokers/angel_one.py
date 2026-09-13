"""Optional Angel One SmartAPI live index feed."""

import logging
import os
import threading
import time

log = logging.getLogger("brokers.angel_one")

_SYMBOL_TOKENS = {
    "NIFTY": ("1", "99926000"),
    "NIFTY 50": ("1", "99926000"),
    "NSEI": ("1", "99926000"),
    "BANKNIFTY": ("1", "99926009"),
    "NSEBANK": ("1", "99926009"),
    "FINNIFTY": ("1", "99926037"),
    "SENSEX": ("3", "99919000"),
}

_latest_spot = {}
_latest_tick_at = {}
_feed_started = set()
_feed_errors = {}
_feed_lock = threading.Lock()


def _credentials_available():
    names = ("ANGEL_API_KEY", "ANGEL_CLIENT_CODE", "ANGEL_PIN", "ANGEL_TOTP_SECRET")
    return all(os.environ.get(name) for name in names)


def _start_feed(symbol):
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

    exchange_type, token = _SYMBOL_TOKENS[symbol.upper()]
    socket = SmartWebSocketV2(auth_token, api_key, client_code, feed_token)

    def on_data(_, message):
        if not isinstance(message, dict):
            return
        value = message.get("last_traded_price")
        if value is not None:
            _latest_spot[symbol] = float(value) / 100
            _latest_tick_at[symbol] = time.monotonic()

    def on_open(wsapp):
        socket.subscribe(
            f"stock-alert-{symbol.lower()}",
            1,
            [{"exchangeType": int(exchange_type), "tokens": [token]}],
        )

    def on_error(_, error):
        log.warning("Angel One WebSocket error: %s", error)

    def on_close(*_):
        log.warning("Angel One WebSocket closed")

    socket.on_data = on_data
    socket.on_open = on_open
    socket.on_error = on_error
    socket.on_close = on_close
    socket.connect()


def start_live_feed(symbol="NIFTY"):
    """Start one background Angel One feed for a supported index."""
    symbol = symbol.upper()
    if symbol not in _SYMBOL_TOKENS or not _credentials_available():
        return False
    with _feed_lock:
        if symbol in _feed_started:
            return True
        _feed_started.add(symbol)

    def run():
        try:
            _start_feed(symbol)
        except Exception as exc:
            log.warning("Angel One live feed unavailable: %s", exc)
            _feed_errors[symbol] = str(exc)
            with _feed_lock:
                _feed_started.discard(symbol)

    threading.Thread(target=run, name=f"angel-feed-{symbol}", daemon=True).start()
    return True


def get_latest_spot(symbol="NIFTY"):
    """Return the latest Angel One tick, or None until the feed receives one."""
    return _latest_spot.get(symbol.upper())


def get_live_status(symbol="NIFTY"):
    symbol = symbol.upper()
    tick_at = _latest_tick_at.get(symbol)
    return {
        "credentials_configured": _credentials_available(),
        "connected": symbol in _feed_started and tick_at is not None,
        "tick_age_seconds": round(time.monotonic() - tick_at, 1) if tick_at else None,
        "error": _feed_errors.get(symbol),
    }
