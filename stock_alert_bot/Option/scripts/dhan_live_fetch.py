import sys
import os
import requests
import importlib.util
import traceback


def load_dhan_module():
    path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "brokers", "dhan.py")
    spec = importlib.util.spec_from_file_location("brokers.dhan", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

dhan = load_dhan_module()

SYMBOL = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("SYMBOL", "NIFTY")
DEFAULT_INTERVAL = 4


def probe_with_token(token, symbol=SYMBOL):
    base = dhan.BASE_URL
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}

    endpoints = [
        f"{base}/option-chain",
        f"{base}/option-chain",
        f"{base}/option_chain",
        f"{base}/market/option-chain",
        f"{base}/market/quote",
        f"{base}/quote",
        f"{base}/market/ltp",
        f"{base}/market/quote/{symbol}",
        f"{base}/quote/{symbol}",
    ]

    for ep in endpoints:
        try:
            r = requests.get(ep, headers=headers, params={"symbol": symbol}, timeout=3)
            print(f"URL: {r.url} -> {r.status_code} ({len(r.content)} bytes)")
            if r.status_code == 200:
                try:
                    j = r.json()
                    import json
                    print(json.dumps(j, indent=2)[:1000])
                except Exception:
                    print("Response not JSON or cannot decode snippet")
            else:
                print("Non-200 response; headers:", dict(r.headers))
        except Exception as e:
            print("Request failed:", e)


def print_top_oi_from_df(df, n=5):
    df = df.copy()
    df['total_oi'] = df['call_oi'].astype(int) + df['put_oi'].astype(int)
    top = df.sort_values('total_oi', ascending=False).head(n)

    print()
    print(" strike  call_oi  put_oi  total_oi")
    for _, r in top.iterrows():
        print(f"{int(r['strike']):7d}{int(r['call_oi']):8d}{int(r['put_oi']):8d}{int(r['total_oi']):9d}")
    print()


def normalize_option_chain(raw):
    """Return (df, spot) where df has columns strike, call_oi, put_oi.

    Handles multiple raw formats: NSE-style `records.data` and mock generator format.
    """
    try:
        # NSE-style
        if isinstance(raw, dict) and 'records' in raw and 'data' in raw['records']:
            from step2_parse import parse_option_chain
            df, spot = parse_option_chain(raw)
            return df[['strike','call_oi','put_oi']], spot
    except Exception:
        traceback.print_exc()

    # Mock generator format: {'underlying','spot_price','data':[{'strike_price', 'CALL':{'oi'}, 'PUT':{'oi'}}]}
    try:
        if isinstance(raw, dict) and 'data' in raw and isinstance(raw['data'], list):
            rows = []
            spot = raw.get('spot_price') or raw.get('spot') or raw.get('underlying')
            for e in raw['data']:
                strike = e.get('strike_price') or e.get('strike') or e.get('strikePrice')
                call = e.get('CALL') or e.get('CE') or {}
                put = e.get('PUT') or e.get('PE') or {}
                call_oi = call.get('oi') or call.get('openInterest') or 0
                put_oi = put.get('oi') or put.get('openInterest') or 0
                rows.append({'strike': strike, 'call_oi': int(call_oi), 'put_oi': int(put_oi)})
            import pandas as pd
            df = pd.DataFrame(rows).sort_values('strike').reset_index(drop=True)
            return df, spot
    except Exception:
        traceback.print_exc()

    raise RuntimeError('Unrecognized option chain format')


def get_spot_via_sdk(symbol):
    """Try to get LTP/spot for `symbol` using the installed dhanhq SDK."""
    try:
        auth = None
        try:
            auth = dhan.authenticate()
        except Exception:
            pass
        sdk = None
        if isinstance(auth, dict) and auth.get('sdk_module'):
            sdk = auth.get('sdk_module')
        else:
            sdk = None

        if not sdk:
            # try to import directly
            try:
                import dhanhq as sdk
            except Exception:
                sdk = None

        if not sdk:
            return None

        # try client constructor
        client = None
        try:
            cid = os.environ.get('DHAN_CLIENT_ID') or os.environ.get('DHAN_API_KEY')
            token = os.environ.get('DHAN_ACCESS_TOKEN')
            if cid and token:
                client = sdk.dhanhq.dhanhq(cid, token, disable_ssl=True)
        except Exception:
            client = None

        # try marketfeed quick quote
        if client and hasattr(client, 'quote_data'):
            try:
                res = client.quote_data(symbol)
                # try common keys
                if isinstance(res, dict):
                    for k in ('last_price', 'ltp', 'lastPrice', 'ltpPrice'):
                        if k in res:
                            return float(res[k])
                    # nested
                    if 'data' in res and isinstance(res['data'], dict):
                        v = list(res['data'].values())[0]
                        if isinstance(v, dict) and 'ltp' in v:
                            return float(v['ltp'])
            except Exception:
                pass

        # try marketfeed.DhanFeed ticker / quote
        try:
            mf = getattr(sdk, 'marketfeed', None)
            if mf and hasattr(mf, 'Quote'):
                # no simple sync call available generally; skip
                return None
        except Exception:
            pass

    except Exception:
        traceback.print_exc()
    return None


def load_mock_generator():
    path = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'scripts', 'generate_mock_option_chain.py')
    spec = importlib.util.spec_from_file_location('scripts.generate_mock_option_chain', path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.load_generator() if hasattr(mod, 'load_generator') else mod


def main():
    import argparse, time

    parser = argparse.ArgumentParser()
    parser.add_argument('symbol', nargs='?', default=SYMBOL)
    parser.add_argument('--mode', choices=['auto', 'live', 'mock'], default='auto')
    parser.add_argument('--interval', type=float, default=DEFAULT_INTERVAL)
    parser.add_argument('--iterations', type=int, default=0, help='0 means run indefinitely')
    parser.add_argument('--top', type=int, default=5)
    args = parser.parse_args()

    api_key = os.environ.get("DHAN_API_KEY")
    api_secret = os.environ.get("DHAN_API_SECRET")
    client_id = os.environ.get("DHAN_CLIENT_ID")
    access_token = os.environ.get("DHAN_ACCESS_TOKEN")
    SYMBOL_LOCAL = args.symbol
    # Try REST token request first (bypass SDK auto-detection)
    token = None
    if api_key and api_secret:
        try:
            token_url = f"{dhan.BASE_URL}/auth/token"
            r = requests.post(token_url, json={"api_key": api_key, "api_secret": api_secret}, timeout=3)
            if r.status_code == 200:
                j = r.json()
                token = j.get("access_token") or j.get("token")
        except Exception as e:
            print("REST token request failed:", e)
        traceback.print_exc()

    if not token:
        # fallback to brokers.dhan.authenticate (may return sdk module or token)
        try:
            auth = dhan.authenticate(api_key=api_key, api_secret=api_secret)
        except Exception as e:
            print("Authentication failed:", e)
            traceback.print_exc()
            auth = None
        if isinstance(auth, dict) and auth.get("access_token"):
            token = auth.get("access_token")
        elif isinstance(auth, dict) and auth.get("sdk_module"):
            print("SDK available; attempting SDK client to fetch data")
            sdk = auth.get("sdk_module")
            # Try direct SDK helpers first (some SDKs expose top-level functions)
            try:
                if hasattr(sdk, "option_chain"):
                    try:
                        print("Calling sdk.option_chain(...) directly...")
                        res = sdk.option_chain(SYMBOL)
                        import json
                        print(json.dumps(res, indent=2)[:2000])
                        return
                    except Exception as e:
                        print("sdk.option_chain call failed:", e)
                        traceback.print_exc()
                if hasattr(sdk, "quote_data"):
                    try:
                        print("Calling sdk.quote_data(...) directly...")
                        res = sdk.quote_data(SYMBOL)
                        import json
                        print(json.dumps(res, indent=2)[:2000])
                        return
                    except Exception as e:
                        print("sdk.quote_data call failed:", e)
                        traceback.print_exc()
            except Exception:
                traceback.print_exc()
            # Prefer environment-supplied client_id + access_token
            tried = []
            client = None
            sdk_client_id = client_id or os.environ.get("DHAN_CLIENT_ID")
            sdk_access_token = access_token or os.environ.get("DHAN_ACCESS_TOKEN")
            # Try sensible constructor permutations and print verbose errors
            try_constructors = []
            if sdk_client_id and sdk_access_token:
                try_constructors.append((sdk_client_id, sdk_access_token))
            if sdk_access_token:
                try_constructors.append((sdk_access_token, ""))
            if sdk_client_id:
                try_constructors.append((sdk_client_id, ""))
            # Last resort: try api_key and token positions
            if api_key:
                try_constructors.append((api_key, ""))

            for a, b in try_constructors:
                try:
                    print(f"Trying SDK constructor with args: ({repr(a)[:20]}, {repr(b)[:6]})")
                    client = sdk.dhanhq.dhanhq(a, b, disable_ssl=True)
                    print("SDK client created successfully")
                    break
                except Exception as e:
                    print("SDK constructor failed:", e)
                    traceback.print_exc()

            if client:
                # try option_chain or quote_data
                try:
                    print("Fetching option_chain via SDK...")
                    res = client.option_chain(SYMBOL)
                    import json
                    print(json.dumps(res, indent=2)[:2000])
                    return
                except Exception as e:
                    print("SDK option_chain call failed:", e)
                    traceback.print_exc()
            print("No SDK client available or SDK call failed; continuing (mock fallback if requested)")

    # Determine fetch strategy
    use_mock = (args.mode == 'mock')
    if args.mode == 'auto' and not token:
        # if no token and no SDK client, fall back to mock
        use_mock = True

    import importlib

    def fetch_raw_chain():
        # live via SDK or REST
        if not use_mock:
            # if token is present, try REST option-chain endpoint
            if token:
                try:
                    base = dhan.BASE_URL
                    ep = f"{base}/option-chain"
                    r = requests.get(ep, headers={"Authorization": f"Bearer {token}"}, params={"symbol": SYMBOL_LOCAL}, timeout=3)
                    r.raise_for_status()
                    return r.json()
                except Exception:
                    traceback.print_exc()
            # try brokers.dhan.authenticate SDK route again
            try:
                auth = dhan.authenticate(api_key=api_key, api_secret=api_secret)
                if isinstance(auth, dict) and auth.get('sdk_module'):
                    sdk = auth.get('sdk_module')
                    try:
                        client = sdk.dhanhq.dhanhq(client_id or api_key or '', access_token or '', disable_ssl=True)
                        return client.option_chain(SYMBOL_LOCAL)
                    except Exception:
                        traceback.print_exc()
            except Exception:
                traceback.print_exc()

        # fallback -> mock generator
        try:
            # import generator from step1_fetch_dhan
            modpath = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'step1_fetch_dhan.py')
            spec = importlib.util.spec_from_file_location('step1_fetch_dhan', modpath)
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            gen = getattr(mod, 'generate_mock_option_chain')
            return gen(SYMBOL_LOCAL)
        except Exception:
            traceback.print_exc()
            raise

    # Poll and print top strikes
    iter_count = 0
    try:
        while True:
            raw = None
            try:
                raw = fetch_raw_chain()
            except Exception as e:
                print('Fetch failed:', e)
                raw = None

            if raw:
                try:
                    df, spot = normalize_option_chain(raw)
                    # attempt to get live LTP via SDK and show both values
                    try:
                        sdk_spot = get_spot_via_sdk(SYMBOL_LOCAL)
                    except Exception:
                        sdk_spot = None
                    if sdk_spot:
                        print(f"Underlying spot (chain): {spot}  | SDK spot: {sdk_spot}  (using SDK spot)")
                        used_spot = sdk_spot
                    else:
                        print(f"Underlying spot: {spot}")
                        used_spot = spot
                    # try to use option_analysis's formatted print if available
                    try:
                        import importlib
                        oa = importlib.import_module('scripts.option_analysis')
                        if hasattr(oa, 'print_top_oi_formatted'):
                            oa.print_top_oi_formatted(df, n=args.top)
                        else:
                            print_top_oi_from_df(df, n=args.top)
                    except Exception:
                        print_top_oi_from_df(df, n=args.top)
                except Exception as e:
                    print('Normalize/print failed:', e)
                    traceback.print_exc()

            iter_count += 1
            if args.iterations and iter_count >= args.iterations:
                break
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print('Stopped by user')


if __name__ == "__main__":
    main()
