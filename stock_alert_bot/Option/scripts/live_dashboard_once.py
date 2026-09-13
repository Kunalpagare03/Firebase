"""
Fetch live option-chain once, build dashboard, print result.
May be blocked by NSE; errors will be shown.
"""
import os
import sys
import json

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step1_fetch import get_option_chain
from step1_fetch_dhan import get_option_chain_dhan
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard, print_dashboard

# Load config for symbol and retries
CFG_PATH = os.path.join(ROOT, "config", "settings.json")
cfg = {}
if os.path.isfile(CFG_PATH):
    with open(CFG_PATH, "r") as f:
        cfg = json.load(f)

SYMBOL = cfg.get("symbol", "NIFTY")
RETRIES = cfg.get("nse_retry_count", 3)
BACKOFF = cfg.get("nse_retry_backoff_seconds", 2)

def main():
    print(f"Attempting one-shot live fetch for {SYMBOL} (retries={RETRIES})...")
    try:
        if cfg.get("data_source") == "dhan":
            try:
                data = get_option_chain_dhan(SYMBOL, url=cfg.get("dhan_option_chain_url"), retries=RETRIES, backoff=BACKOFF)
            except Exception as dhan_error:
                print(f"Dhan unavailable ({dhan_error}); trying NSE...")
                data = get_option_chain(SYMBOL, retries=RETRIES, backoff=BACKOFF)
                print("Using NSE live option chain.")
        else:
            data = get_option_chain(SYMBOL, retries=RETRIES, backoff=BACKOFF)
    except Exception as e:
        print("Live fetch failed:", e)
        return

    try:
        df, spot = parse_option_chain(data, expiry=cfg.get("expiry"))
        # For the single-run dashboard we need a previous snapshot; reuse current as prev
        dashboard = build_dashboard(df, df, spot)
        print_dashboard(dashboard)
    except Exception as e:
        print("Failed to parse/build dashboard:", e)

if __name__ == '__main__':
    main()
