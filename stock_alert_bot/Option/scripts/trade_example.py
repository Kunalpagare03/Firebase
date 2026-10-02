"""
Example script showing dry-run order placement for Zerodha or Dhan.
This will NOT place live orders when `dry_run=True`.
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from brokers import zerodha, dhan


def example_zerodha():
    # Attempt to authenticate using env vars; if no token, function will raise.
    try:
        client = zerodha.authenticate()
    except Exception as e:
        print("Zerodha authenticate failed (expected if no token):", e)
        client = None

    payload = zerodha.place_order(
        client=client,
        tradingsymbol="RELIANCE",
        quantity=1,
        transaction_type="BUY",
        dry_run=True,
    )
    print("Zerodha dry-run payload:", payload)


def example_dhan():
    try:
        token = dhan.authenticate()
    except Exception as e:
        print("Dhan authenticate failed (expected in scaffold):", e)
        token = {"access_token": "EXAMPLE"}

    # If SDK module was returned, fall back to a placeholder token for dry-run
    if isinstance(token, dict) and token.get("sdk_module"):
        print("Dhan SDK detected; falling back to dry-run placeholder token.")
        token = {"access_token": "EXAMPLE"}

    resp = dhan.place_order(token, tradingsymbol="RELIANCE", quantity=1, side="BUY", dry_run=True)
    print("Dhan dry-run payload:", resp)


if __name__ == '__main__':
    print("--- Zerodha example ---")
    example_zerodha()
    print("--- Dhan example ---")
    example_dhan()
