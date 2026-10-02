import os
import requests

TOKEN = os.environ.get("DHAN_ACCESS_TOKEN")
SYMBOL = os.environ.get("SYMBOL", "NIFTY")

if not TOKEN:
    print("DHAN_ACCESS_TOKEN not set in environment. Set it and re-run.")
    raise SystemExit(1)

HEADERS = {"Authorization": f"Bearer {TOKEN}", "Accept": "application/json"}

endpoints = [
    "https://api.dhan.co/v2/option-chain",
    "https://api.dhan.co/v2/option_chain",
    "https://api.dhan.co/v2/options/chain",
    "https://api.dhan.co/v2/options/option-chain",
    "https://api.dhan.co/v2/market/option-chain",
    "https://api.dhan.co/v1/option-chain",
    "https://api.dhan.co/v2/option-chain/{}",
    "https://api.dhan.co/v2/option_chain/{}",
    # Common quote/ltp endpoints to help identify available APIs
    "https://api.dhan.co/v2/quote",
    "https://api.dhan.co/v2/market/quote",
    "https://api.dhan.co/v2/market/ltp",
    "https://api.dhan.co/v2/market/quote/{}",
    "https://api.dhan.co/v2/quote/{}",
]

def try_get(url, params=None):
    try:
        r = requests.get(url, headers=HEADERS, params=params, timeout=10)
        print(f"URL: {r.url} -> {r.status_code} ({len(r.content)} bytes)")
        if r.status_code == 200:
            # print a small snippet of JSON to help identify schema
            try:
                j = r.json()
                import json

                s = json.dumps(j, indent=2)[:800]
                print(s)
            except Exception:
                print("Response not JSON or cannot decode snippet.")
        else:
            print("Non-200 response; headers:", dict(r.headers))
    except Exception as e:
        print(f"Request failed: {e}")

if __name__ == "__main__":
    print(f"Probing {len(endpoints)} endpoints for symbol={SYMBOL}")
    for ep in endpoints:
        if "{}" in ep:
            url = ep.format(SYMBOL)
            print("\nTrying (path variant):", url)
            try_get(url)
        else:
            print("\nTrying (param variant):", ep)
            try_get(ep, params={"symbol": SYMBOL})
