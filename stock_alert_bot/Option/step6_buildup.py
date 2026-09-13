import pandas as pd
from step2_parse import parse_option_chain


def classify_buildup(price_change, oi_change):
    """
    Standard OI buildup classification for a single option (call or put leg).
    """
    if price_change > 0 and oi_change > 0:
        return "Long Buildup"       # bullish for this instrument
    elif price_change < 0 and oi_change > 0:
        return "Short Buildup"      # bearish for this instrument
    elif price_change > 0 and oi_change < 0:
        return "Short Covering"     # bullish (shorts exiting)
    elif price_change < 0 and oi_change < 0:
        return "Long Unwinding"     # bearish (longs exiting)
    else:
        return "Flat / Unclear"


def analyze_buildup(df_prev, df_curr):
    """
    Compares two option chain snapshots (previous vs current) strike by strike
    and classifies buildup pattern separately for Calls and Puts.
    """
    merged = df_curr.merge(
        df_prev[["strike", "call_ltp", "call_oi", "put_ltp", "put_oi"]],
        on="strike", suffixes=("", "_prev")
    )

    if merged.empty:
        empty = pd.DataFrame(columns=["strike", "call_buildup", "put_buildup"])
        return empty, {
            "call_buildup_counts": {},
            "put_buildup_counts": {},
            "overall_bullish_votes": 0,
            "overall_bearish_votes": 0,
            "overall_read": "Mixed / Neutral",
        }

    rows = []
    for _, r in merged.iterrows():
        call_price_chg = r["call_ltp"] - r["call_ltp_prev"]
        call_oi_chg = r["call_oi"] - r["call_oi_prev"]
        put_price_chg = r["put_ltp"] - r["put_ltp_prev"]
        put_oi_chg = r["put_oi"] - r["put_oi_prev"]

        rows.append({
            "strike": r["strike"],
            "call_buildup": classify_buildup(call_price_chg, call_oi_chg),
            "put_buildup": classify_buildup(put_price_chg, put_oi_chg),
        })

    result = pd.DataFrame(rows)

    # Aggregate into an overall sentiment read
    call_counts = result["call_buildup"].value_counts().to_dict()
    put_counts = result["put_buildup"].value_counts().to_dict()

    bullish_call_signals = call_counts.get("Long Buildup", 0) + call_counts.get("Short Covering", 0)
    bearish_call_signals = call_counts.get("Short Buildup", 0) + call_counts.get("Long Unwinding", 0)

    bullish_put_signals = put_counts.get("Short Buildup", 0) + put_counts.get("Long Unwinding", 0)
    # note: bearish activity in PUTS (short buildup / long unwinding on puts)
    # is actually often read as bullish for the underlying, since it reflects
    # traders reducing put protection or writers selling puts confidently
    bearish_put_signals = put_counts.get("Long Buildup", 0) + put_counts.get("Short Covering", 0)

    overall_bullish_votes = bullish_call_signals + bullish_put_signals
    overall_bearish_votes = bearish_call_signals + bearish_put_signals

    if overall_bullish_votes > overall_bearish_votes:
        overall_read = "Leaning Bullish"
    elif overall_bearish_votes > overall_bullish_votes:
        overall_read = "Leaning Bearish"
    else:
        overall_read = "Mixed / Neutral"

    summary = {
        "call_buildup_counts": call_counts,
        "put_buildup_counts": put_counts,
        "overall_bullish_votes": overall_bullish_votes,
        "overall_bearish_votes": overall_bearish_votes,
        "overall_read": overall_read,
    }

    return result, summary


def load_mock_snapshot(offset=0):
    """
    Two slightly different mock snapshots to simulate 'previous' vs 'current'.
    offset shifts prices/OI to simulate market movement between snapshots.
    On your machine: save two real get_option_chain() pulls a few minutes apart.
    """
    base = {
        "records": {
            "underlyingValue": 24850.35 + offset,
            "data": [
                {"strikePrice": 24600,
                 "CE": {"openInterest": 12500 + offset*50, "changeinOpenInterest": 800, "totalTradedVolume": 21000, "impliedVolatility": 13.2, "lastPrice": 305.5 + offset*2},
                 "PE": {"openInterest": 45200 - offset*100, "changeinOpenInterest": 6100, "totalTradedVolume": 38000, "impliedVolatility": 14.1, "lastPrice": 55.2 - offset}},
                {"strikePrice": 24700,
                 "CE": {"openInterest": 18700 + offset*80, "changeinOpenInterest": 1200, "totalTradedVolume": 26500, "impliedVolatility": 13.0, "lastPrice": 220.1 + offset*2},
                 "PE": {"openInterest": 38900 - offset*60, "changeinOpenInterest": 4200, "totalTradedVolume": 31000, "impliedVolatility": 13.8, "lastPrice": 80.4 - offset}},
                {"strikePrice": 24800,
                 "CE": {"openInterest": 26100 + offset*100, "changeinOpenInterest": -1500, "totalTradedVolume": 41000, "impliedVolatility": 12.7, "lastPrice": 140.3 + offset*3},
                 "PE": {"openInterest": 29800 - offset*40, "changeinOpenInterest": 2100, "totalTradedVolume": 27000, "impliedVolatility": 13.4, "lastPrice": 120.6 - offset}},
                {"strikePrice": 24900,
                 "CE": {"openInterest": 41500 + offset*150, "changeinOpenInterest": 5300, "totalTradedVolume": 52000, "impliedVolatility": 12.5, "lastPrice": 78.9 + offset*2},
                 "PE": {"openInterest": 21000 - offset*30, "changeinOpenInterest": -900, "totalTradedVolume": 19500, "impliedVolatility": 13.1, "lastPrice": 175.2 - offset}},
                {"strikePrice": 25000,
                 "CE": {"openInterest": 58200 + offset*200, "changeinOpenInterest": 9100, "totalTradedVolume": 71000, "impliedVolatility": 12.3, "lastPrice": 38.4 + offset*1.5},
                 "PE": {"openInterest": 15600 - offset*20, "changeinOpenInterest": -2100, "totalTradedVolume": 14000, "impliedVolatility": 12.9, "lastPrice": 240.7 - offset}},
            ]
        }
    }
    return base


if __name__ == "__main__":
    raw_prev = load_mock_snapshot(offset=0)    # "10 minutes ago"
    raw_curr = load_mock_snapshot(offset=1)    # "now" -- market ticked up

    df_prev, spot_prev = parse_option_chain(raw_prev)
    df_curr, spot_curr = parse_option_chain(raw_curr)

    print(f"Previous spot: {spot_prev} -> Current spot: {spot_curr}\n")

    result, summary = analyze_buildup(df_prev, df_curr)

    print("Per-strike buildup classification:")
    print(result.to_string(index=False))

    print("\n--- Summary ---")
    for k, v in summary.items():
        print(f"{k}: {v}")
