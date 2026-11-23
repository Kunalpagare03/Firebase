import numpy as np
import pandas as pd
from typing import Dict, Any, Tuple

def compute_wilder_atr(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    """Computes Wilder's Average True Range (ATR14)."""
    prev_close = close.shift(1)
    tr1 = high - low
    tr2 = (high - prev_close).abs()
    tr3 = (low - prev_close).abs()
    tr = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)

    atr = np.zeros(len(tr))
    if len(tr) < period:
        return pd.Series(atr, index=close.index)

    # Initial ATR is simple mean of first `period` TRs
    atr[period - 1] = tr.iloc[:period].mean()
    for i in range(period, len(tr)):
        atr[i] = (atr[i - 1] * (period - 1) + tr.iloc[i]) / period

    atr[:period - 1] = np.nan
    return pd.Series(atr, index=close.index)

def compute_wilder_rsi(close: pd.Series, period: int = 14) -> pd.Series:
    """Computes Wilder's Relative Strength Index (RSI). Used for RSI(2) and RSI(14)."""
    delta = close.diff()
    up = delta.clip(lower=0)
    down = -delta.clip(upper=0)

    rsi = np.zeros(len(close))
    if len(close) <= period:
        return pd.Series(rsi, index=close.index)

    avg_up = up.iloc[1:period + 1].mean()
    avg_down = down.iloc[1:period + 1].mean()

    for i in range(period, len(close)):
        if i == period:
            u_smooth = avg_up
            d_smooth = avg_down
        else:
            u_smooth = (u_smooth * (period - 1) + up.iloc[i]) / period
            d_smooth = (d_smooth * (period - 1) + down.iloc[i]) / period

        if d_smooth == 0:
            rsi[i] = 100.0
        else:
            rs = u_smooth / d_smooth
            rsi[i] = 100.0 - (100.0 / (1.0 + rs))

    rsi[:period] = np.nan
    return pd.Series(rsi, index=close.index)

def compute_adx(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    """Computes ADX(14) using Wilder's smoothing."""
    atr = compute_wilder_atr(high, low, close, period=period)
    up_move = high - high.shift(1)
    down_move = low.shift(1) - low

    plus_dm = np.where((up_move > down_move) & (up_move > 0), up_move, 0.0)
    minus_dm = np.where((down_move > up_move) & (down_move > 0), down_move, 0.0)

    plus_dm_s = pd.Series(plus_dm, index=close.index)
    minus_dm_s = pd.Series(minus_dm, index=close.index)

    def wilder_smooth(s: pd.Series, n: int) -> np.ndarray:
        out = np.zeros(len(s))
        if len(s) < n:
            return out
        out[n - 1] = s.iloc[:n].mean()
        for i in range(n, len(s)):
            out[i] = (out[i - 1] * (n - 1) + s.iloc[i]) / n
        out[:n - 1] = np.nan
        return out

    smooth_plus = pd.Series(wilder_smooth(plus_dm_s, period), index=close.index)
    smooth_minus = pd.Series(wilder_smooth(minus_dm_s, period), index=close.index)

    plus_di = 100.0 * (smooth_plus / atr)
    minus_di = 100.0 * (smooth_minus / atr)

    dx = 100.0 * ((plus_di - minus_di).abs() / (plus_di + minus_di).replace(0, np.nan))
    adx_arr = wilder_smooth(dx.fillna(0), period)
    return pd.Series(adx_arr, index=close.index)

def compute_supertrend(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 10, multiplier: float = 3.0) -> Tuple[pd.Series, pd.Series]:
    """Computes Supertrend(10, 3) and direction (+1 bull, -1 bear)."""
    atr = compute_wilder_atr(high, low, close, period=period)
    hl2 = (high + low) / 2.0
    basic_upper = hl2 + (multiplier * atr)
    basic_lower = hl2 - (multiplier * atr)

    final_upper = basic_upper.copy()
    final_lower = basic_lower.copy()
    direction = np.ones(len(close))
    trend = np.zeros(len(close))

    for i in range(1, len(close)):
        # Lower band
        if basic_lower.iloc[i] > final_lower.iloc[i - 1] or close.iloc[i - 1] < final_lower.iloc[i - 1]:
            final_lower.iloc[i] = basic_lower.iloc[i]
        else:
            final_lower.iloc[i] = final_lower.iloc[i - 1]

        # Upper band
        if basic_upper.iloc[i] < final_upper.iloc[i - 1] or close.iloc[i - 1] > final_upper.iloc[i - 1]:
            final_upper.iloc[i] = basic_upper.iloc[i]
        else:
            final_upper.iloc[i] = final_upper.iloc[i - 1]

        # Trend direction
        if direction[i - 1] == 1:
            if close.iloc[i] < final_lower.iloc[i]:
                direction[i] = -1
                trend[i] = final_upper.iloc[i]
            else:
                direction[i] = 1
                trend[i] = final_lower.iloc[i]
        else:
            if close.iloc[i] > final_upper.iloc[i]:
                direction[i] = 1
                trend[i] = final_lower.iloc[i]
            else:
                direction[i] = -1
                trend[i] = final_upper.iloc[i]

    return pd.Series(trend, index=close.index), pd.Series(direction, index=close.index)

def calculate_all_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """Calculates the complete suite of technical indicators required by v4."""
    data = df.copy()
    if len(data) < 20:
        return data

    c = data["Close"]
    h = data["High"]
    l = data["Low"]
    v = data["Volume"]

    # Moving Averages
    data["SMA20"] = c.rolling(window=20).mean()
    data["SMA50"] = c.rolling(window=50).mean()
    data["SMA200"] = c.rolling(window=200).mean()
    data["EMA10"] = c.ewm(span=10, adjust=False).mean()
    data["EMA20"] = c.ewm(span=20, adjust=False).mean()

    # Wilder ATR & RSI
    data["ATR14"] = compute_wilder_atr(h, l, c, period=14)
    data["RSI2"] = compute_wilder_rsi(c, period=2)
    data["RSI14"] = compute_wilder_rsi(c, period=14)

    # Z20 / Bollinger
    rolling_std20 = c.rolling(window=20).std()
    data["Z20"] = (c - data["SMA20"]) / rolling_std20.replace(0, np.nan)

    # RAM (Risk-Adjusted Momentum) = RET(126) / (ATR14 / Close)
    ret_126 = (c / c.shift(126)) - 1.0
    atr_pct = data["ATR14"] / c
    data["RET126"] = ret_126
    data["RAM"] = ret_126 / atr_pct.replace(0, np.nan)

    # High252, Low10 (excluding today)
    data["High252"] = h.shift(1).rolling(window=252).max()
    data["Low10"] = l.shift(1).rolling(window=10).min()

    # ADX(14)
    data["ADX14"] = compute_adx(h, l, c, period=14)

    # Supertrend(10, 3)
    st_val, st_dir = compute_supertrend(h, l, c, period=10, multiplier=3.0)
    data["Supertrend"] = st_val
    data["Supertrend_Dir"] = st_dir

    # SMA200 slope (20 days): SMA200 / SMA200[-20] - 1
    data["SMA200_Slope20"] = (data["SMA200"] / data["SMA200"].shift(20)) - 1.0

    # Up/down volume ratio (50 days)
    up_vol = np.where(c > c.shift(1), v, 0.0)
    down_vol = np.where(c < c.shift(1), v, 0.0)
    up_sum = pd.Series(up_vol, index=c.index).rolling(window=50).sum()
    down_sum = pd.Series(down_vol, index=c.index).rolling(window=50).sum()
    data["UpDown_VolRatio50"] = up_sum / down_sum.replace(0, 1.0)

    # Traded Value (20 days): SMA20(Volume) * Close in Crores
    vol_sma20 = v.rolling(window=20).mean()
    data["TradedValue20_Cr"] = (vol_sma20 * c) / 10_000_000.0

    # 5-day return (for score: lower is better)
    data["RET5"] = (c / c.shift(5)) - 1.0

    # 3-day drop for Crash Dip
    data["C_vs_C3"] = c / c.shift(3)

    return data
