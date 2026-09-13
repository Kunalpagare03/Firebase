import pandas as pd
from step2_parse import parse_option_chain
from step3_pcr import calculate_pcr
from step4_maxpain import calculate_max_pain
from step5_support_resistance import find_support_resistance
from step6_buildup import analyze_buildup, load_mock_snapshot


def build_dashboard(df_prev, df_curr, spot_curr):
    """
    Combines all prior steps into one sentiment dashboard.
    Returns a dict that's easy to print, log, or feed into an HTML/chart layer.
    """
    pcr = calculate_pcr(df_curr)
    max_pain, pain_table = calculate_max_pain(df_curr)
    support, resistance = find_support_resistance(df_curr, top_n=3, spot=spot_curr, range_points=500)
    buildup_detail, buildup_summary = analyze_buildup(df_prev, df_curr)
    top_strikes = df_curr.copy()
    top_strikes["total_oi"] = top_strikes["call_oi"] + top_strikes["put_oi"]
    previous_by_strike = df_prev.drop_duplicates("strike").set_index("strike")
    top_strikes["window_call_oi_change"] = top_strikes["strike"].map(previous_by_strike["call_oi"]).rsub(top_strikes["call_oi"]).fillna(0)
    top_strikes["window_put_oi_change"] = top_strikes["strike"].map(previous_by_strike["put_oi"]).rsub(top_strikes["put_oi"]).fillna(0)
    top_strikes["call_oi_change"] = top_strikes.get("call_oi_change", 0)
    top_strikes["put_oi_change"] = top_strikes.get("put_oi_change", 0)
    top_strikes["oi_side"] = top_strikes.apply(
        lambda row: "Put-side support" if row["window_put_oi_change"] > row["window_call_oi_change"]
        else "Call-side resistance" if row["window_call_oi_change"] > row["window_put_oi_change"]
        else "Both sides / sideways",
        axis=1,
    )
    buildup_by_strike = buildup_detail.set_index("strike") if not buildup_detail.empty else pd.DataFrame()
    if not buildup_by_strike.empty:
        top_strikes["call_buildup"] = top_strikes["strike"].map(buildup_by_strike["call_buildup"]).fillna("Unavailable")
        top_strikes["put_buildup"] = top_strikes["strike"].map(buildup_by_strike["put_buildup"]).fillna("Unavailable")
    else:
        top_strikes["call_buildup"] = "Unavailable"
        top_strikes["put_buildup"] = "Unavailable"
    top_strikes = top_strikes.nlargest(5, "total_oi")
    put_change = int((df_curr["put_oi"] - df_curr["strike"].map(previous_by_strike["put_oi"]).fillna(df_curr["put_oi"])).sum())
    call_change = int((df_curr["call_oi"] - df_curr["strike"].map(previous_by_strike["call_oi"]).fillna(df_curr["call_oi"])).sum())
    if put_change > call_change:
        oi_directional_read = "Put OI building more: support / upward bias"
        oi_trend = "Bullish OI trend"
    elif call_change > put_change:
        oi_directional_read = "Call OI building more: resistance / downward bias"
        oi_trend = "Bearish OI trend"
    else:
        oi_directional_read = "Call and put OI balanced: sideways bias"
        oi_trend = "Neutral OI trend"
    oi_building_analysis = (
        f"Across the chain, call OI change is {call_change:+,} and put OI change is {put_change:+,}. "
        f"{oi_directional_read}. Per-strike labels show long buildup, short buildup, covering, or "
        "unwinding over the selected comparison window. Confirm with price action before acting."
    )

    pcr_oi = pcr["pcr_oi"]
    pcr_volume = pcr["pcr_volume"]
    atm_distance = abs(df_curr["strike"].sub(spot_curr).abs()).min()
    concentrated_oi = top_strikes["total_oi"].sum() / max(df_curr["call_oi"].sum() + df_curr["put_oi"].sum(), 1)
    risk_score = 0
    risk_reason_parts = []

    if pcr_oi is not None and 0.9 <= pcr_oi <= 1.1:
        risk_score += 2
        risk_reason_parts.append("OI is near neutral PCR")
    elif pcr_oi is not None and (pcr_oi < 0.85 or pcr_oi > 1.15):
        risk_score -= 1

    if pcr_volume is not None and 0.9 <= pcr_volume <= 1.1:
        risk_score += 2
        risk_reason_parts.append("volume is balanced")
    elif pcr_volume is not None and (pcr_volume < 0.85 or pcr_volume > 1.15):
        risk_score -= 1

    if atm_distance <= 100:
        risk_score += 2
        risk_reason_parts.append("spot is near the ATM wall")

    if concentrated_oi > 0.25:
        risk_score += 1
        risk_reason_parts.append("heavy OI is concentrated in a few strikes")

    if abs(call_change) < max(5000, abs(put_change) * 0.2) and abs(put_change) < max(5000, abs(call_change) * 0.2):
        risk_score += 1
        risk_reason_parts.append("OI change is nearly balanced")

    if risk_score >= 5:
        manipulation_risk = "High"
        manipulation_note = "Strong sign of pinning or controlled action: OI and volume are balanced near the current ATM zone, so the move may be hedged rather than directional."
    elif risk_score >= 3:
        manipulation_risk = "Medium"
        manipulation_note = "Mixed positioning near the ATM zone suggests a squeeze or controlled range rather than a clean breakout."
    else:
        manipulation_risk = "Low"
        manipulation_note = "The setup is reasonably directional with OI and volume supporting a more genuine trend."

    # --- Combine into one simple weighted read ---
    # OI and PCR carry the most weight because they directly show positioning.
    # Price-vs-max-pain acts as confirmation, while the headline stays one-way.
    votes = {"bullish": 0, "bearish": 0}

    if pcr["pcr_oi"] and pcr["pcr_oi"] > 1.3:
        votes["bullish"] += 2
    elif pcr["pcr_oi"] and pcr["pcr_oi"] < 0.7:
        votes["bearish"] += 2

    if spot_curr > max_pain:
        votes["bearish"] += 1   # some pull expected back toward max pain
    elif spot_curr < max_pain:
        votes["bullish"] += 1

    if buildup_summary["overall_read"] == "Leaning Bullish":
        votes["bullish"] += 3   # weighted highest -- most direct signal
    elif buildup_summary["overall_read"] == "Leaning Bearish":
        votes["bearish"] += 3

    if put_change > call_change:
        votes["bullish"] += 1
    elif call_change > put_change:
        votes["bearish"] += 1

    bullish_total = votes["bullish"]
    bearish_total = votes["bearish"]
    score = bullish_total - bearish_total

    if abs(score) <= 1:
        combined_read = "Mixed / Neutral"
    elif score > 0:
        combined_read = "Leaning Bullish"
    else:
        combined_read = "Leaning Bearish"

    if score > 0:
        final_signal = "Bullish"
    elif score < 0:
        final_signal = "Bearish"
    else:
        final_signal = "Neutral"

    dashboard = {
        "spot_price": spot_curr,
        "pcr_oi": pcr["pcr_oi"],
        "pcr_oi_reading": pcr["pcr_oi_reading"],
        "pcr_volume": pcr["pcr_volume"],
        "pcr_volume_reading": pcr["pcr_volume_reading"],
        "max_pain": max_pain,
        "support_zones": support["strike"].tolist(),
        "resistance_zones": resistance["strike"].tolist(),
        "top_strikes": top_strikes[["strike", "call_oi", "put_oi", "total_oi", "call_oi_change", "put_oi_change", "window_call_oi_change", "window_put_oi_change", "oi_side", "call_buildup", "put_buildup"]].to_dict("records"),
        "oi_directional_read": oi_directional_read,
        "oi_trend": oi_trend,
        "oi_building_analysis": oi_building_analysis,
        "oi_buildup_overall": buildup_summary["overall_read"],
        "combined_sentiment_read": combined_read,
        "final_signal": final_signal,
        "manipulation_risk": manipulation_risk,
        "manipulation_note": manipulation_note,
        "votes": votes,
        "caveat": (
            "This is a probability/sentiment estimate from CURRENT positioning data, "
            "not a prediction. It can be invalidated instantly by news, FII/DII flows, "
            "global cues, or momentum. Treat as one input alongside price action, not "
            "a standalone signal."
        ),
    }
    return dashboard


def print_dashboard(dashboard):
    print("=" * 55)
    print(" OPTION CHAIN SENTIMENT DASHBOARD")
    print("=" * 55)
    print(f" Spot Price          : {dashboard['spot_price']}")
    print(f" PCR (OI)            : {dashboard['pcr_oi']}  -> {dashboard['pcr_oi_reading']}")
    print(f" PCR (Volume)        : {dashboard['pcr_volume']}  -> {dashboard['pcr_volume_reading']}")
    print(f" Max Pain            : {dashboard['max_pain']}")
    print(f" Support Zones       : {dashboard['support_zones']}")
    print(f" Resistance Zones    : {dashboard['resistance_zones']}")
    print(f" OI Buildup Read     : {dashboard['oi_buildup_overall']}")
    print(f" Vote Tally          : {dashboard['votes']}")
    print("-" * 55)
    print(f" COMBINED SENTIMENT  : {dashboard['combined_sentiment_read']}")
    print("-" * 55)
    print(f" NOTE: {dashboard['caveat']}")
    print("=" * 55)


if __name__ == "__main__":
    import json
    import os
    import sys

    root = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(root, "config", "settings.json"), "r") as f:
        config = json.load(f)

    symbol = config.get("symbol", "NIFTY")
    source = "mock" if "--mock" in sys.argv else config.get("data_source", "nse")
    try:
        if source == "mock":
            raw_prev = load_mock_snapshot(offset=0)
            raw_curr = load_mock_snapshot(offset=1)
        elif source == "dhan":
            from step1_fetch_dhan import get_option_chain_dhan
            try:
                raw_curr = get_option_chain_dhan(
                    symbol,
                    url=config.get("dhan_option_chain_url"),
                    retries=config.get("nse_retry_count", 3),
                    backoff=config.get("nse_retry_backoff_seconds", 2),
                    expiry=config.get("expiry"),
                )
            except Exception as dhan_error:
                print(f"Dhan unavailable ({dhan_error}); trying NSE...")
                from step1_fetch import get_option_chain
                try:
                    raw_curr = get_option_chain(
                        symbol,
                        retries=config.get("nse_retry_count", 3),
                        backoff=config.get("nse_retry_backoff_seconds", 2),
                    )
                    source = "nse-fallback"
                except Exception as nse_error:
                    raise RuntimeError(f"Dhan failed: {dhan_error}; NSE failed: {nse_error}") from nse_error
            raw_prev = raw_curr
        else:
            from step1_fetch import get_option_chain
            raw_curr = get_option_chain(
                symbol,
                retries=config.get("nse_retry_count", 3),
                backoff=config.get("nse_retry_backoff_seconds", 2),
            )
            raw_prev = raw_curr

        expiry = config.get("expiry")
        df_prev, spot_prev = parse_option_chain(raw_prev, expiry=expiry)
        df_curr, spot_curr = parse_option_chain(raw_curr, expiry=expiry)
    except Exception as exc:
        print(f"Live {source} fetch failed: {exc}")
        raise SystemExit(1)

    dashboard = build_dashboard(df_prev, df_curr, spot_curr)
    print(f"Data source: {source} ({'live' if source != 'mock' else 'synthetic'})")
    print_dashboard(dashboard)
    if source != "mock":
        try:
            from stock_alert.price_action import analyze_price_action, print_price_action
            print_price_action(analyze_price_action(symbol))
        except Exception as exc:
            print(f"Price-action data unavailable: {exc}")
    from stock_alert.greeks import analyze_chain_greeks
    greek_result = analyze_chain_greeks(df_curr, spot_curr, expiry=expiry)
    print(f"Greeks: {greek_result}")
