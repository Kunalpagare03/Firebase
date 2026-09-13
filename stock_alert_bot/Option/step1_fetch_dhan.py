import os
import time
import requests
from requests.exceptions import RequestException
import json
import numpy as np
import yfinance as yf
from datetime import datetime, timedelta

DEFAULT_DHAN_OPTION_CHAIN_URL = "https://api.dhan.co/v2/option-chain"
DHAN_UNDERLYING_IDS = {
    "NIFTY": 13,
    "NIFTY 50": 13,
    "NSEI": 13,
    "BANKNIFTY": 25,
    "NSEBANK": 25,
    "FINNIFTY": 27,
    "SENSEX": 51,
}

DHAN_SEGMENTS = {
    "SENSEX": "BSE_FNO",
}


def _load_token(token=None):
    if token:
        return token
    token = os.environ.get("DHAN_ACCESS_TOKEN")
    if token:
        return token
    token_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "dhan_access_token.txt")
    if os.path.isfile(token_path):
        return open(token_path, "r").read().strip()
    return None


def _load_client_id(token):
    client_id = os.environ.get("DHAN_CLIENT_ID")
    if client_id:
        return client_id
    try:
        import base64
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        claims = json.loads(base64.urlsafe_b64decode(payload).decode("utf-8"))
        return claims.get("dhanClientId") or claims.get("dhanClientID") or claims.get("client_id")
    except (IndexError, ValueError, KeyError, UnicodeDecodeError, json.JSONDecodeError):
        return None


def _normalize_dhan_sdk_response(response):
    data = response.get("data", response) if isinstance(response, dict) else response
    if isinstance(data, dict) and "data" in data and isinstance(data["data"], dict):
        data = data["data"]
    if not isinstance(data, dict) or "oc" not in data:
        raise ValueError("Dhan option-chain response has no expected 'oc' data")

    rows = []
    for strike, legs in data["oc"].items():
        call = legs.get("ce", {})
        put = legs.get("pe", {})
        rows.append({
            "strike_price": float(strike),
            "CALL": {
                "ltp": call.get("last_price", 0),
                "oi": call.get("oi", 0),
                "volume": call.get("volume", 0),
                "iv": call.get("implied_volatility", 0),
            },
            "PUT": {
                "ltp": put.get("last_price", 0),
                "oi": put.get("oi", 0),
                "volume": put.get("volume", 0),
                "iv": put.get("implied_volatility", 0),
            },
        })
    if not rows:
        raise ValueError("Dhan option-chain response contains no strikes")
    return {"spot_price": data.get("last_price"), "data": rows}


def get_option_chain_dhan(symbol="NIFTY", token=None, url=None, retries=3, backoff=1, expiry=None):
    """Fetch option-chain from Dhan v2 API.

    - `token`: Dhan access token; if None reads `DHAN_ACCESS_TOKEN` env var.
    - `url`: base URL for option-chain endpoint (defaults to DEFAULT_DHAN_OPTION_CHAIN_URL).
    Returns parsed JSON on success.
    """
    token = _load_token(token)
    if not token:
        raise RuntimeError("DHAN_ACCESS_TOKEN not set in environment")

    # The official SDK supplies the required segment and expiry parameters.
    try:
        import dhanhq
        client_id = _load_client_id(token)
        if not client_id:
            raise RuntimeError("DHAN_CLIENT_ID not set in environment")
        try:
            client = dhanhq.dhanhq(client_id, token, disable_ssl=True)
        except TypeError:
            context = dhanhq.DhanContext(client_id, token)
            client = dhanhq.dhanhq(context)
        segment = os.environ.get("DHAN_SEGMENT", DHAN_SEGMENTS.get(symbol.upper(), "NSE_FNO"))
        underlying_id = DHAN_UNDERLYING_IDS.get(symbol.upper(), symbol)
        expiries = client.expiry_list(underlying_id, segment)
        if isinstance(expiries, dict) and expiries.get("status") == "failure":
            raise RuntimeError(expiries.get("data", expiries))
        selected_expiry = expiry or (expiries[0] if expiries else None)
        if not selected_expiry:
            raise RuntimeError(f"No Dhan expiry available for {symbol}")
        response = client.option_chain(underlying_id, segment, selected_expiry)
        if isinstance(response, dict) and response.get("status") == "failure":
            raise RuntimeError(response.get("data", response))
        normalized = _normalize_dhan_sdk_response(response)
        normalized["expiry"] = selected_expiry
        return normalized
    except ImportError:
        pass

    url = url or os.environ.get("DHAN_OPTION_CHAIN_URL") or DEFAULT_DHAN_OPTION_CHAIN_URL

    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}

    params = {"symbol": symbol}

    attempt = 0
    while True:
        attempt += 1
        try:
            resp = requests.get(url, headers=headers, params=params, timeout=10)
            resp.raise_for_status()
            return resp.json()
        except (RequestException, ValueError) as e:
            if attempt >= retries:
                raise
            time.sleep(backoff * attempt)
            continue


def generate_mock_option_chain(ticker_symbol="^NSEI", strike_spacing=None, strikes_each_side=10, out_path=None):
    """Generate a mock option-chain using yfinance spot as the ATM center.

    Returns a JSON-like dict compatible with downstream parser.
    """
    # Allow overriding spot price for testing/dev via env var
    spot_override = os.environ.get('MOCK_SPOT_PRICE') or os.environ.get('DHAN_SPOT_PRICE')
    if spot_override:
        try:
            spot_price = float(spot_override)
        except Exception:
            spot_price = None
    else:
        spot_price = None

    ticker = yf.Ticker(ticker_symbol)
    hist = ticker.history(period="1d") if spot_price is None else None
    # try common alternative suffix for Indian equities
    if hist is not None and hist.empty and not ticker_symbol.startswith("^") and not "." in ticker_symbol:
        alt = ticker_symbol + ".NS"
        ticker = yf.Ticker(alt)
        hist = ticker.history(period="1d")
        if not hist.empty:
            ticker_symbol = alt
    if hist is None or hist.empty:
        # If an explicit override was provided, keep it. Otherwise fall back to
        # reasonable synthetic defaults when yfinance is unavailable.
        if spot_price is None:
            if ticker_symbol.upper().startswith('^NSE') or 'NIFTY' in ticker_symbol.upper():
                spot_price = 24288.0
            elif 'RELIANCE' in ticker_symbol.upper():
                spot_price = 1300.0
            else:
                spot_price = 1000.0
        # continue with synthetic spot
    else:
        spot_price = float(hist["Close"].iloc[-1])

    if strike_spacing is None:
        # default spacing for NIFTY vs single equities
        strike_spacing = 50 if ticker_symbol in ("^NSEI", "NIFTY", "NIFTY 50") else 100

    atm_strike = round(spot_price / strike_spacing) * strike_spacing
    strikes = [int(atm_strike + (i * strike_spacing)) for i in range(-strikes_each_side, strikes_each_side + 1)]

    expiry_date = (datetime.now() + timedelta(days=5)).strftime("%Y-%m-%d")

    chain_data = {
        "underlying": ticker_symbol,
        "spot_price": round(spot_price, 2),
        "expiry": expiry_date,
        "data": []
    }

    for strike in strikes:
        distance = (strike - spot_price) / spot_price
        call_ltp = max(1.0, round(spot_price * 0.02 * np.exp(-5 * distance), 2))
        put_ltp = max(1.0, round(spot_price * 0.02 * np.exp(5 * distance), 2))

        chain_data["data"].append({
            "strike_price": strike,
            "CALL": {
                "ltp": call_ltp,
                "bid": round(call_ltp * 0.98, 2),
                "ask": round(call_ltp * 1.02, 2),
                "oi": int(10000 * np.exp(-abs(distance) * 3))
            },
            "PUT": {
                "ltp": put_ltp,
                "bid": round(put_ltp * 0.98, 2),
                "ask": round(put_ltp * 1.02, 2),
                "oi": int(12000 * np.exp(-abs(distance) * 3))
            }
        })

    # persist to data cache for inspection
    os.makedirs("data", exist_ok=True)
    safe = ticker_symbol.replace("^", "").replace("/", "_")
    if out_path is None:
        out_path = os.path.join("data", f"option_chain_{safe}.json")
    with open(out_path, "w") as f:
        json.dump(chain_data, f, indent=2)

    return chain_data
