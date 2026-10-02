"""Keep the NIFTY Angel One quote socket alive and retry after disconnects."""

import time
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from brokers.angel_one import get_latest_spot, get_live_status, start_live_feed


def main():
    start_live_feed("NIFTY")
    while True:
        get_latest_spot("NIFTY")
        status = get_live_status("NIFTY")
        if status.get("error"):
            print(f"NIFTY Angel One feed retry pending: {status['error']}", flush=True)
        time.sleep(10)


if __name__ == "__main__":
    main()
