import os
import csv
import logging
from datetime import datetime

from brokers import dhan

log = logging.getLogger("stock_alert.execution")

ORDERS_LOG = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs", "orders.csv")


def _ensure_orders_log():
    os.makedirs(os.path.dirname(ORDERS_LOG), exist_ok=True)
    if not os.path.isfile(ORDERS_LOG):
        with open(ORDERS_LOG, "w", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["timestamp", "signal", "tradingsymbol", "side", "quantity", "order_type", "response"])


def place_order_for_signal(signal, tradingsymbol="RELIANCE", quantity=1, product="MIS", order_type="MARKET"):
    """Execute a live order for a simple bullish/bearish signal.

    Requirements in environment:
      - DHAN_ACCESS_TOKEN must be set
      - LIVE_TRADING must be set to "true" (string) to allow live orders

    WARNING: This function places real orders when LIVE_TRADING=true.
    """
    if signal not in ("Leaning Bullish", "Leaning Bearish"):
        log.info("No execution for signal: %s", signal)
        return None

    live_flag = os.environ.get("LIVE_TRADING", "false").lower()
    confirm_flag = os.environ.get("CONFIRM_LIVE", "false").lower()
    if live_flag != "true" or confirm_flag != "true":
        raise RuntimeError("Live trading disabled. Set LIVE_TRADING=true and CONFIRM_LIVE=true in environment to enable live orders.")

    token = os.environ.get("DHAN_ACCESS_TOKEN")
    if not token:
        raise RuntimeError("DHAN_ACCESS_TOKEN not set in environment")

    side = "BUY" if signal == "Leaning Bullish" else "SELL"

    client = {"access_token": token}

    # Allow overriding defaults via environment
    tradingsymbol = os.environ.get("EXEC_TRADINGSYMBOL", tradingsymbol)
    quantity = int(os.environ.get("EXEC_QUANTITY", quantity))
    product = os.environ.get("EXEC_PRODUCT", product)
    order_type = os.environ.get("EXEC_ORDER_TYPE", order_type)

    _ensure_orders_log()

    # Place order via brokers.dhan (note: this calls live endpoint)
    try:
        resp = dhan.place_order(client, tradingsymbol=tradingsymbol, quantity=quantity, side=side, order_type=order_type, dry_run=False)
    except Exception as e:
        log.exception("Order placement failed")
        resp = {"error": str(e)}

    # Log
    with open(ORDERS_LOG, "a", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([datetime.utcnow().isoformat(), signal, tradingsymbol, side, quantity, order_type, repr(resp)])

    return resp
