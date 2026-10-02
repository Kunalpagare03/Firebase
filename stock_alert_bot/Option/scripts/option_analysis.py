import json
import os
import math
import pandas as pd


def load_mock_chain(path=None, symbol=None):
    if path is None:
        if symbol is None:
            symbol = os.environ.get("SYMBOL", "^NSEI")
        safe = symbol.replace("^", "").replace("/", "_")
        path = f"data/option_chain_{safe}.json"
        # try alternate common suffix for equities (e.g., RELIANCE -> RELIANCE.NS)
        if not os.path.exists(path) and not safe.endswith('.NS') and not symbol.startswith('^'):
            alt = f"data/option_chain_{safe}.NS.json"
            if os.path.exists(alt):
                path = alt
    if not os.path.exists(path):
        # if symbol was provided, try generating a mock chain for development
        if symbol and not symbol.startswith('^'):
            try:
                # import generator from step1_fetch_dhan
                gen_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'step1_fetch_dhan.py')
                import importlib.util
                spec = importlib.util.spec_from_file_location('step1_fetch_dhan', gen_path)
                mod = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(mod)
                gen = getattr(mod, 'generate_mock_option_chain')
                print(f"Generating mock option chain for {symbol} -> {path}")
                gen(symbol, out_path=path)
            except Exception:
                pass

        # fallback to legacy mock filename
        legacy = "data/option_chain_mock.json"
        if os.path.exists(path):
            pass
        elif os.path.exists(legacy):
            path = legacy
        else:
            raise FileNotFoundError(f"Option chain file not found: {path}")
    with open(path, "r") as f:
        j = json.load(f)
    return j


def to_dataframe(mock):
    rows = []
    spot = mock.get("spot_price") or mock.get("underlying_value")
    for e in mock.get("data", []):
        strike = int(e.get("strike_price") or e.get("strikePrice"))
        call = e.get("CALL", {})
        put = e.get("PUT", {})
        rows.append({
            "strike": strike,
            "call_oi": int(call.get("oi", call.get("openInterest", 0) or 0)),
            "call_volume": int(call.get("volume", call.get("totalTradedVolume", 0) or 0)),
            "call_ltp": float(call.get("ltp", call.get("lastPrice", 0) or 0)),
            "put_oi": int(put.get("oi", put.get("openInterest", 0) or 0)),
            "put_volume": int(put.get("volume", put.get("totalTradedVolume", 0) or 0)),
            "put_ltp": float(put.get("ltp", put.get("lastPrice", 0) or 0)),
        })

    df = pd.DataFrame(rows).sort_values("strike").reset_index(drop=True)
    return df, spot


def calculate_pcr(df):
    call_oi = df["call_oi"].sum()
    put_oi = df["put_oi"].sum()
    call_vol = df["call_volume"].sum()
    put_vol = df["put_volume"].sum()
    pcr_oi = round((put_oi / call_oi), 3) if call_oi else math.nan
    pcr_vol = round((put_vol / call_vol), 3) if call_vol else math.nan
    return {
        "pcr_oi": pcr_oi,
        "pcr_vol": pcr_vol,
        "total_call_oi": call_oi,
        "total_put_oi": put_oi,
        "total_call_vol": call_vol,
        "total_put_vol": put_vol,
    }


def support_resistance(df):
    support = int(df.loc[df["put_oi"].idxmax(), "strike"])
    resistance = int(df.loc[df["call_oi"].idxmax(), "strike"])
    return support, resistance


def oi_skew_reading(df):
    # compare weighted OI around ATM
    df = df.copy()
    df["oi_diff"] = df["put_oi"] - df["call_oi"]
    oi_skew = df["oi_diff"].sum()
    if oi_skew > 0:
        return "Put-heavy OI skew (bias towards bullish hedging / protection)"
    elif oi_skew < 0:
        return "Call-heavy OI skew (bias towards short-call / bearish positioning)"
    else:
        return "Balanced OI across strikes"


def decide_trend(spot, support, resistance, pcr, oi_skew_text):
    # Simple rule-set to produce a human-readable market trend
    if spot is None:
        return "Unknown (spot unavailable)"

    if spot > resistance:
        base = "Bullish — price above call OI resistance"
    elif spot < support:
        base = "Bearish — price below put OI support"
    else:
        base = "Range-bound between OI support/resistance"

    # incorporate PCR
    if pcr.get("pcr_oi") and pcr["pcr_oi"] > 1.3:
        extra = "+ Elevated PCR (puts > calls) — defensive/bullish tilt"
    elif pcr.get("pcr_oi") and pcr["pcr_oi"] < 0.7:
        extra = "+ Low PCR (calls > puts) — aggressive/bearish tilt"
    else:
        extra = ""

    return f"{base}. {oi_skew_text}. {extra}".strip()


def top_oi_strikes(df, n=5):
    df = df.copy()
    df["total_oi"] = df["call_oi"] + df["put_oi"]
    top = df.sort_values("total_oi", ascending=False).head(n)[["strike", "call_oi", "put_oi", "total_oi"]]
    return top


def print_top_oi_formatted(df, n=5):
    """Print top OI strikes in the compact aligned format the user requested."""
    df = df.copy()
    df["total_oi"] = df["call_oi"] + df["put_oi"]
    top = df.sort_values("total_oi", ascending=False).head(n)

    print()
    print(" strike  call_oi  put_oi  total_oi")
    for _, r in top.iterrows():
        print(f"{int(r['strike']):7d}{int(r['call_oi']):8d}{int(r['put_oi']):8d}{int(r['total_oi']):9d}")
    print()


def run(path=None, symbol=None):
    mock = load_mock_chain(path=path, symbol=symbol)
    df, spot = to_dataframe(mock)

    pcr = calculate_pcr(df)
    support, resistance = support_resistance(df)
    oi_text = oi_skew_reading(df)
    trend = decide_trend(spot, support, resistance, pcr, oi_text)
    top = top_oi_strikes(df, n=5)
    print("\nOption Chain Analysis")
    print(f"Underlying spot: {spot}")
    print(f"Support: {support}  Resistance: {resistance}")
    print(f"PCR (OI): {pcr['pcr_oi']}, PCR (Vol): {pcr['pcr_vol']}")
    print(f"OI Skew: {oi_text}")
    print(f"Market Read: {trend}\n")

    print("Top strikes by total OI:")
    print_top_oi_formatted(df, n=5)


if __name__ == "__main__":
    import sys
    arg = sys.argv[1] if len(sys.argv) > 1 else None
    if arg:
        if arg.endswith('.json') or arg.startswith('data/'):
            run(path=arg)
        else:
            run(symbol=arg)
    else:
        run()
