import pandas as pd
import numpy as np
from step2_parse import parse_option_chain
from step3_pcr import calculate_pcr
from step4_maxpain import calculate_max_pain
from step5_support_resistance import find_support_resistance
from step6_buildup import analyze_buildup, load_mock_snapshot
from step9_deep_dive_analysis import perform_deep_dive

def json_safe(v):
    if isinstance(v, dict): return {k: json_safe(i) for k, i in v.items()}
    if isinstance(v, list): return [json_safe(i) for i in v]
    if isinstance(v, (np.int64, np.int32, np.integer)): return int(v)
    if isinstance(v, (np.float64, np.float32, np.floating)): return float(v)
    return v

def build_dashboard(df_prev, df_curr, spot_curr):
    pcr = calculate_pcr(df_curr)
    max_pain, pain_table = calculate_max_pain(df_curr)
    support, resistance = find_support_resistance(df_curr, top_n=3, spot=spot_curr, range_points=500)
    buildup_detail, buildup_summary = analyze_buildup(df_prev, df_curr)

    top_strikes = df_curr.copy()
    top_strikes["total_oi"] = top_strikes["call_oi"] + top_strikes["put_oi"]

    previous_by_strike = df_prev.drop_duplicates("strike").set_index("strike")
    top_strikes["window_call_oi_change"] = top_strikes["strike"].map(previous_by_strike["call_oi"]).rsub(top_strikes["call_oi"]).fillna(0)
    top_strikes["window_put_oi_change"] = top_strikes["strike"].map(previous_by_strike["put_oi"]).rsub(top_strikes["put_oi"]).fillna(0)

    # Map buildup labels from step 6
    buildup_by_strike = buildup_detail.set_index("strike") if not buildup_detail.empty else pd.DataFrame()
    if not buildup_by_strike.empty:
        top_strikes["call_buildup"] = top_strikes["strike"].map(buildup_by_strike["call_buildup"]).fillna("Flat")
        top_strikes["put_buildup"] = top_strikes["strike"].map(buildup_by_strike["put_buildup"]).fillna("Flat")
    else:
        top_strikes["call_buildup"] = "N/A"
        top_strikes["put_buildup"] = "N/A"

    top_strikes["oi_side"] = top_strikes.apply(
        lambda row: "Put-side support" if row["window_put_oi_change"] > row["window_call_oi_change"]
        else "Call-side resistance" if row["window_call_oi_change"] > row["window_put_oi_change"]
        else "Both sides / sideways",
        axis=1,
    )

    top_strikes = top_strikes.nlargest(5, "total_oi")

    score = 0
    if pcr["pcr_oi"] and pcr["pcr_oi"] > 1.1: score += 2
    elif pcr["pcr_oi"] and pcr["pcr_oi"] < 0.9: score -= 2

    if buildup_summary["overall_read"] == "Leaning Bullish": score += 3
    elif buildup_summary["overall_read"] == "Leaning Bearish": score -= 3

    final_signal = "Bullish" if score > 0 else "Bearish" if score < 0 else "Neutral"

    dashboard = {
        "spot_price": spot_curr,
        "pcr_oi": round(pcr["pcr_oi"], 3) if pcr["pcr_oi"] else 0,
        "pcr_volume": round(pcr["pcr_volume"], 3) if pcr["pcr_volume"] else 0,
        "max_pain": max_pain,
        "support_zones": support["strike"].tolist(),
        "resistance_zones": resistance["strike"].tolist(),
        "top_strikes": top_strikes[["strike", "call_oi", "put_oi", "total_oi", "call_buildup", "put_buildup", "oi_side"]].to_dict("records"),
        "final_signal": final_signal,
        "combined_sentiment_read": f"Leaning {final_signal}" if final_signal != "Neutral" else "Neutral",
        "deep_dive": perform_deep_dive(df_curr, spot_curr),
        "oi_buildup_overall": buildup_summary["overall_read"]
    }
    return json_safe(dashboard)

def print_dashboard(dashboard):
    print(f"Signal: {dashboard['final_signal']}")

if __name__ == "__main__":
    from utils.firebase_sync import sync_to_firestore
    from stock_alert.greeks import analyze_chain_greeks
    from step1_fetch import get_option_chain

    symbol = "NIFTY"
    raw_curr = get_option_chain(symbol)
    df_curr, spot_curr = parse_option_chain(raw_curr)

    dashboard = build_dashboard(df_curr, df_curr, spot_curr)
    dashboard["greeks"] = json_safe(analyze_chain_greeks(df_curr, spot_curr))
    dashboard["fetched_at"] = pd.Timestamp.now().strftime("%H:%M:%S")
    dashboard["spot_history"] = [{"time": int(pd.Timestamp.now().timestamp()*1000), "value": spot_curr}]

    sync_to_firestore("option_sentiment", symbol, dashboard)
    print(f"Manual Dashboard Sync Complete for {symbol}")
