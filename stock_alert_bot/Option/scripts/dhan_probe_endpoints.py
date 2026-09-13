import requests
import sys
import os
import json

ENDPOINTS = [
    "/option-chain",
    "/option_chain",
    "/market/option-chain",
    "/market/quote",
    "/quote",
    "/market/ltp",
    "/market/quote/{symbol}",
    "/quote/{symbol}",
    "/v2/option-chain",
]


def probe(base, token, symbol="NIFTY"):
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    for ep in ENDPOINTS:
        path = ep.format(symbol=symbol)
        url = base.rstrip('/') + path
        try:
            r = requests.get(url, headers=headers, params={"symbol": symbol}, timeout=10)
            print(f"URL: {url} -> {r.status_code} | {len(r.content)} bytes")
            if r.status_code == 200:
                try:
                    j = r.json()
                    print(json.dumps(j, indent=2)[:2000])
                except Exception:
                    print('Non-JSON or too large; headers:', dict(r.headers))
            else:
                print('Headers:', dict(r.headers))
        except Exception as e:
            print('Request failed for', url, e)


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default=os.environ.get('DHAN_BASE_URL', 'https://api.dhan.co'))
    parser.add_argument('--token')
    parser.add_argument('--symbol', default='NIFTY')
    args = parser.parse_args()
    token = args.token or os.environ.get('DHAN_ACCESS_TOKEN')
    if not token:
        print('Provide --token or set DHAN_ACCESS_TOKEN env var')
        sys.exit(1)
    probe(args.base, token, symbol=args.symbol)


if __name__ == '__main__':
    main()
