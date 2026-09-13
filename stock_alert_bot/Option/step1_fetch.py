import requests
import json
import time
from requests.exceptions import RequestException

def get_option_chain(symbol="NIFTY", retries=3, backoff=1, expiry=None):
    """
    Fetch LIVE option chain from NSE.
    symbol: "NIFTY" or "BANKNIFTY"

    NOTE: This will NOT work in a sandboxed/offline environment --
    it needs real internet access to nseindia.com. Run this on your
    own machine (laptop/PC/server with normal internet access).
    """
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept": "*/*",
        "Referer": "https://www.nseindia.com/option-chain",
    }

    session = requests.Session()
    # NSE requires an initial homepage visit to set cookies before the API works
    try:
        session.get("https://www.nseindia.com", headers=headers, timeout=5)
    except RequestException:
        # best-effort; continue to API attempt
        pass

    # small pause helps avoid being blocked
    time.sleep(1)

    if expiry is None:
        info = session.get(
            "https://www.nseindia.com/api/option-chain-contract-info",
            headers=headers,
            params={"symbol": symbol},
            timeout=10,
        )
        info.raise_for_status()
        contract_info = info.json()
        expiries = contract_info.get("expiryDates", [])
        if not expiries:
            raise RequestException(f"NSE returned no expiries for {symbol}")
        expiry = expiries[0]

    endpoints = [
        ("https://www.nseindia.com/api/option-chain-v3", {"type": "Indices", "symbol": symbol, "expiry": expiry}),
        ("https://www.nseindia.com/api/option-chain-indices", {"symbol": symbol}),
    ]
    last_error = None
    while True:
        for url, params in endpoints:
            try:
                response = session.get(url, headers=headers, params=params, timeout=10)
                response.raise_for_status()
                try:
                    data = response.json()
                    if not isinstance(data, dict) or not data:
                        last_error = RequestException("NSE returned an empty option-chain response")
                        continue
                    return data
                except ValueError:
                    last_error = RequestException(f"Non-JSON response (status={response.status_code})")
            except RequestException as error:
                last_error = error

        if _retry_count(retries) <= 1:
            raise last_error
        retries -= 1
        time.sleep(backoff * (_retry_count(retries) + 1))


def _retry_count(value):
    return max(1, int(value))


if __name__ == "__main__":
    data = get_option_chain("NIFTY")
    print("Underlying value:", data["records"]["underlyingValue"])
    print("Number of strikes:", len(data["records"]["data"]))

    # Save raw snapshot with timestamp -- useful for Step 6/8 (buildup needs 2 snapshots)
    ts = time.strftime("%Y%m%d_%H%M%S")
    fname = f"snapshot_{ts}.json"
    with open(fname, "w") as f:
        json.dump(data, f)
    print(f"Saved snapshot to {fname}")
