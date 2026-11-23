import pandas as pd
import numpy as np
from typing import Dict, List, Tuple
from stockapp.app.data_prices import LIQUID_UNIVERSE, fetch_price_data, get_symbol_sector
from stockapp.app.indicators import calculate_all_indicators
from stockapp.app.log import logger

def compute_universe_metrics(offline_mode: bool = False) -> Tuple[pd.DataFrame, Dict[str, float], Dict[str, float]]:
    """
    Computes snapshot indicators for all stocks in the liquid universe, calculates:
    1. Daily percentile ranks: RAM rank, 6M return rank, Quick Score
    2. Market breadth (% above SMA200, % above SMA50)
    3. Sector breadth (% above SMA50 per sector)
    """
    rows = []
    for sym in LIQUID_UNIVERSE:
        try:
            df = fetch_price_data(sym, offline_mode=offline_mode)
            if df.empty or len(df) < 50:
                continue
            df_ind = calculate_all_indicators(df)
            last = df_ind.iloc[-1]
            prev = df_ind.iloc[-2] if len(df_ind) >= 2 else last

            sector = get_symbol_sector(sym)
            rows.append({
                "symbol": sym,
                "sector": sector,
                "close": float(last["Close"]),
                "open": float(last["Open"]),
                "high": float(last["High"]),
                "low": float(last["Low"]),
                "volume": int(last["Volume"]),
                "turnover_cr": float(last["TradedValue20_Cr"]) if not np.isnan(last["TradedValue20_Cr"]) else 10.0,
                "sma20": float(last["SMA20"]) if not np.isnan(last["SMA20"]) else last["Close"],
                "sma50": float(last["SMA50"]) if not np.isnan(last["SMA50"]) else last["Close"],
                "sma200": float(last["SMA200"]) if not np.isnan(last["SMA200"]) else last["Close"],
                "ema10": float(last["EMA10"]) if not np.isnan(last["EMA10"]) else last["Close"],
                "ema20": float(last["EMA20"]) if not np.isnan(last["EMA20"]) else last["Close"],
                "rsi2": float(last["RSI2"]) if not np.isnan(last["RSI2"]) else 50.0,
                "rsi2_prev": float(prev["RSI2"]) if not np.isnan(prev["RSI2"]) else 50.0,
                "rsi14": float(last["RSI14"]) if not np.isnan(last["RSI14"]) else 50.0,
                "atr14": float(last["ATR14"]) if not np.isnan(last["ATR14"]) else 1.0,
                "z20": float(last["Z20"]) if not np.isnan(last["Z20"]) else 0.0,
                "ram": float(last["RAM"]) if not np.isnan(last["RAM"]) else 0.0,
                "ret126": float(last["RET126"]) if not np.isnan(last["RET126"]) else 0.0,
                "ret5": float(last["RET5"]) if not np.isnan(last["RET5"]) else 0.0,
                "adx14": float(last["ADX14"]) if not np.isnan(last["ADX14"]) else 20.0,
                "supertrend": float(last["Supertrend"]) if not np.isnan(last["Supertrend"]) else last["Close"],
                "supertrend_dir": int(last["Supertrend_Dir"]) if not np.isnan(last["Supertrend_Dir"]) else 1,
                "sma200_slope": float(last["SMA200_Slope20"]) if not np.isnan(last["SMA200_Slope20"]) else 0.0,
                "up_down_vol": float(last["UpDown_VolRatio50"]) if not np.isnan(last["UpDown_VolRatio50"]) else 1.0,
                "c_vs_c3": float(last["C_vs_C3"]) if not np.isnan(last["C_vs_C3"]) else 1.0,
                "low10": float(last["Low10"]) if not np.isnan(last["Low10"]) else last["Low"],
                "c_prev": float(prev["Close"]),
                "h_prev": float(prev["High"]),
                "l_prev": float(prev["Low"]),
            })
        except Exception as e:
            logger.debug(f"Error computing universe row for {sym}: {e}")

    snap_df = pd.DataFrame(rows)
    if snap_df.empty:
        return snap_df, {}, {}

    # Percentile Ranks (0 to 100)
    snap_df["ram_rank"] = snap_df["ram"].rank(pct=True) * 100.0
    snap_df["ret126_rank"] = snap_df["ret126"].rank(pct=True) * 100.0
    snap_df["quick_score"] = (snap_df["ram_rank"] + snap_df["ret126_rank"]) / 2.0

    # Market Breadth
    total_valid = len(snap_df)
    above_sma200 = (snap_df["close"] > snap_df["sma200"]).sum()
    above_sma50 = (snap_df["close"] > snap_df["sma50"]).sum()

    market_breadth = {
        "pct_above_sma200": (above_sma200 / total_valid * 100.0) if total_valid > 0 else 50.0,
        "pct_above_sma50": (above_sma50 / total_valid * 100.0) if total_valid > 0 else 50.0,
        "total_stocks": total_valid
    }

    # Sector Breadth (% above SMA50)
    sector_breadth = {}
    for sec, group in snap_df.groupby("sector"):
        sec_above = (group["close"] > group["sma50"]).sum()
        sector_breadth[sec] = (sec_above / len(group) * 100.0)

    # Attach sector breadth back to snapshot
    snap_df["sector_breadth"] = snap_df["sector"].map(sector_breadth).fillna(50.0)

    return snap_df, market_breadth, sector_breadth
