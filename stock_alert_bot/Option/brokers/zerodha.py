"""
Scaffold for a Zerodha (Kite) connector.
Fill in with your own credentials and the official Kite Connect SDK.
DO NOT commit API keys/secrets to source control.
"""
import os
import logging

try:
    from kiteconnect import KiteConnect
except Exception:
    KiteConnect = None

KITE_API_KEY = os.environ.get("KITE_API_KEY")
KITE_API_SECRET = os.environ.get("KITE_API_SECRET")
# Optionally set an access token in env to skip interactive login flow
KITE_ACCESS_TOKEN = os.environ.get("KITE_ACCESS_TOKEN")

log = logging.getLogger("brokers.zerodha")


def authenticate(api_key=None, api_secret=None, access_token=None):
    """Return an authenticated KiteConnect client.

    Usage:
      - If `access_token` is provided (env var KITE_ACCESS_TOKEN), this function
        returns a `KiteConnect` instance with the token set (suitable for server usage).
      - Otherwise, you must perform the standard Kite `request_token` -> `generate_session`
        flow to obtain an access token. See Kite Connect docs.

    This function intentionally avoids implementing the web-based login flow.
    Provide `access_token` via environment variables or a secure secrets manager.
    """
    if KiteConnect is None:
        raise RuntimeError("kiteconnect package not installed. Install with `pip install kiteconnect`")

    api_key = api_key or KITE_API_KEY
    api_secret = api_secret or KITE_API_SECRET
    access_token = access_token or KITE_ACCESS_TOKEN

    if not api_key or not api_secret:
        raise ValueError("KITE_API_KEY and KITE_API_SECRET must be set as environment variables or passed in")

    kc = KiteConnect(api_key=api_key)

    if access_token:
        kc.set_access_token(access_token)
        return kc

    # No access token — raise with instructions.
    raise RuntimeError(
        "No access token provided. Complete request_token -> generate_session flow to obtain access token. "
        "See Kite Connect docs: https://kite.trade/docs/connect/v3/"
    )


def place_order(client, tradingsymbol, quantity, transaction_type="BUY", exchange="NSE", order_type="MARKET", product="MIS", price=None, trigger_price=None, variety="regular", dry_run=True):
    """Place an order using the authenticated KiteConnect client.

    - `client`: KiteConnect instance from `authenticate()`
    - Parameters map to Kite's `place_order` signature.
    - `dry_run`: if True, will log and return the payload without sending the order.
    Returns the broker response (or payload for dry_run).
    """
    payload = {
        "tradingsymbol": tradingsymbol,
        "exchange": exchange,
        "transaction_type": transaction_type,
        "quantity": quantity,
        "order_type": order_type,
        "product": product,
    }
    if price is not None:
        payload["price"] = price
    if trigger_price is not None:
        payload["trigger_price"] = trigger_price

    log.info("Prepared order payload: %s", payload)

    if dry_run:
        return {"dry_run": True, "payload": payload}

    # perform the order via KiteConnect
    resp = client.place_order(variety=variety, **payload)
    return resp


def get_positions(client):
    """Return current positions using KiteConnect client."""
    return client.positions()
