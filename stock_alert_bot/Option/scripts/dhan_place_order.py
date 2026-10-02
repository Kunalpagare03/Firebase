"""
Place a dry-run Dhan order using DHAN_ACCESS_TOKEN from environment.
Do NOT put your token in files. Set env var locally before running.

PowerShell example to set token:
  setx DHAN_ACCESS_TOKEN "<paste-your-token-here>"
Then open a new terminal for the env var to take effect.

Run:
  python scripts/dhan_place_order.py
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from brokers import dhan


def main():
    token = os.environ.get("DHAN_ACCESS_TOKEN")
    if not token:
        print("DHAN_ACCESS_TOKEN not set. Set it with `setx DHAN_ACCESS_TOKEN \"<token>\"` and open a new terminal.")
        return

    client = {"access_token": token}

    # Example: dry-run place an order for RELIANCE
    resp = dhan.place_order(client, tradingsymbol="RELIANCE", quantity=1, side="BUY", order_type="MARKET", dry_run=True)
    print("Dry-run response:")
    print(resp)


if __name__ == '__main__':
    main()
