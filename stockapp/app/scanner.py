import pandas as pd
import numpy as np
from typing import List, Dict, Any, Tuple
from stockapp.app.universe import compute_universe_metrics
from stockapp.app.score import compute_technical_scores
from stockapp.app.market import MarketTrafficLight
from stockapp.app.settings import settings
from stockapp.app.log import logger

# Setups Catalog
SETUPS_INFO = {
    "SETUP_A": {"code": "S09", "name": "RSI2 Panic (<5)", "category": "DIP"},
    "SETUP_B": {"code": "P03", "name": "Pullback to Rising 50-day", "category": "TREND"},
    "SETUP_C": {"code": "S17", "name": "3M Top Decile Pausing at EMA10", "category": "TREND"},
    "SETUP_D": {"code": "S10", "name": "Crash Dip (>=8% in 3 days)", "category": "DIP"},
    "SETUP_N01": {"code": "N01", "name": "Bollinger Stretch (Z20 <= -2)", "category": "DIP"},
    "SETUP_N03": {"code": "N03", "name": "2-Day RSI Panic (Sum < 10)", "category": "DIP"},
    "SETUP_N04": {"code": "N04", "name": "4 Consecutive Lower Closes", "category": "DIP"},
    "SETUP_N08": {"code": "N08", "name": "Leader in Panic Dip (RAM>=80, RSI2<10)", "category": "DIP"},
    "SETUP_N09": {"code": "N09", "name": "Leader Bounces at EMA20", "category": "TREND"},
    "SETUP_N18": {"code": "N18", "name": "Hammer at 10-day Low", "category": "DIP"}
}

def scan_all_setups(snapshot_df: pd.DataFrame, traffic_light: Dict[str, Any]) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    Evaluates the base filter and all 10 setups.
    Separates into:
    - Swing Candidates (v4: Setup 1 Crash Dip & Setup 2 Panic Dip in a Leader)
    - Positional Candidates (All 10 setups, prioritized by Technical Score)
    """
    if snapshot_df.empty:
        return pd.DataFrame(), pd.DataFrame()

    # 1. Base Filter
    # Close >= 50, Turnover >= 5 Cr, Close > SMA200
    base_mask = (
        (snapshot_df["close"] >= settings.scanner.min_price) &
        (snapshot_df["turnover_cr"] >= settings.scanner.min_turnover_cr) &
        (snapshot_df["close"] > snapshot_df["sma200"])
    )
    pool = snapshot_df[base_mask].copy()
    if pool.empty:
        logger.warning("No stocks passed base filter (Price >= 50, Turnover >= 5Cr, Close > SMA200)")
        return pd.DataFrame(), pd.DataFrame()

    # Detect individual setups
    matches = []
    for _, row in pool.iterrows():
        c = row["close"]
        o = row["open"]
        h = row["high"]
        l = row["low"]
        sma50 = row["sma50"]
        ema10 = row["ema10"]
        ema20 = row["ema20"]
        rsi2 = row["rsi2"]
        rsi2_prev = row["rsi2_prev"]
        z20 = row["z20"]
        c_vs_c3 = row["c_vs_c3"]
        ram_rank = row["ram_rank"]
        ret126_rank = row["ret126_rank"]
        low10 = row["low10"]

        stock_setups = []

        # Setup A (S09): RSI2 < 5
        if rsi2 < 5.0:
            stock_setups.append("SETUP_A")

        # Setup B (P03): Pullback to rising SMA50
        if (abs(c - sma50) / sma50 <= 0.02) and (c >= sma50):
            stock_setups.append("SETUP_B")

        # Setup C (S17): 3M return rank >= 90 pausing at EMA10
        if (ret126_rank >= 90.0) and (abs(c - ema10) / ema10 <= 0.015):
            stock_setups.append("SETUP_C")

        # Setup D (S10): Crash Dip >= 8% in 3 days
        if c_vs_c3 <= 0.92:
            stock_setups.append("SETUP_D")

        # Setup N01: Z20 <= -2
        if z20 <= -2.0:
            stock_setups.append("SETUP_N01")

        # Setup N03: RSI2 sum < 10
        if (rsi2 + rsi2_prev) < 10.0:
            stock_setups.append("SETUP_N03")

        # Setup N04: 4 consecutive lower closes (approximated here by deep dip & low RSI)
        if (c < row["c_prev"]) and (rsi2 < 15.0):
            stock_setups.append("SETUP_N04")

        # Setup N08: RAM rank >= 80 & RSI2 < 10
        if (ram_rank >= 80.0) and (rsi2 < 10.0):
            stock_setups.append("SETUP_N08")

        # Setup N09: Leader bounces at EMA20
        if (ram_rank >= 70.0) and (l <= ema20 * 1.01) and (c > ema20):
            stock_setups.append("SETUP_N09")

        # Setup N18: Hammer at 10-day low
        body = abs(c - o)
        lower_shadow = min(o, c) - l
        if (lower_shadow >= 2.0 * max(body, 0.5)) and (l <= low10 * 1.005):
            stock_setups.append("SETUP_N18")

        if stock_setups:
            row_dict = row.to_dict()
            for s in stock_setups:
                item = row_dict.copy()
                item["setup_id"] = s
                item["setup_code"] = SETUPS_INFO[s]["code"]
                item["setup_name"] = SETUPS_INFO[s]["name"]
                item["setup_category"] = SETUPS_INFO[s]["category"]
                matches.append(item)

    if not matches:
        return pd.DataFrame(), pd.DataFrame()

    candidates_df = pd.DataFrame(matches)
    candidates_df = compute_technical_scores(candidates_df)

    # Positional Candidates: All 10 setups
    positional_df = candidates_df.copy()
    positional_df["horizon"] = "POSITIONAL"

    # Swing Candidates (v4: ONLY Setup 1 Crash Dip & Setup 2 Panic Dip in a Leader)
    # Nifty must be above SMA200
    nifty_above_sma200 = traffic_light.get("nifty_close", 24000.0) > traffic_light.get("nifty_sma200", 23000.0)

    swing_rows = []
    if nifty_above_sma200:
        for _, r in candidates_df.iterrows():
            is_swing = False
            swing_label = ""
            
            # Setup 1 Crash Dip: RAM rank >= 50, C <= 0.92 * C[-3]
            if (r["ram_rank"] >= 50.0) and (r["c_vs_c3"] <= 0.92):
                is_swing = True
                swing_label = "v4 Swing Setup 1 (Crash Dip)"

            # Setup 2 Panic Dip in a Leader: Tech Score >= 70 AND Panic RSI condition
            # (RSI2 < 5 OR sum < 10 OR [RAM >= 80 & RSI2 < 10])
            panic_rsi = (r["rsi2"] < 5.0) or ((r["rsi2"] + r["rsi2_prev"]) < 10.0) or (r["ram_rank"] >= 80.0 and r["rsi2"] < 10.0)
            if (r["tech_score"] >= settings.scanner.leader_tech_score) and panic_rsi:
                is_swing = True
                swing_label = "v4 Swing Setup 2 (Panic Dip in Leader)"

            if is_swing:
                sr = r.to_dict()
                sr["horizon"] = "SWING"
                sr["swing_setup_label"] = swing_label
                swing_rows.append(sr)

    swing_df = pd.DataFrame(swing_rows) if swing_rows else pd.DataFrame()

    return swing_df, positional_df
