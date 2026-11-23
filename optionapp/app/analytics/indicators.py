"""Technical indicators for Option App v8 (Part C3)."""

from typing import Dict, List, Optional, Tuple
import numpy as np
import pandas as pd


def compute_ema(series: pd.Series, period: int) -> pd.Series:
    """Exponential Moving Average."""
    return series.ewm(span=period, adjust=False).mean()


def compute_sma(series: pd.Series, period: int) -> pd.Series:
    """Simple Moving Average."""
    return series.rolling(window=period).mean()


def compute_rsi(series: pd.Series, period: int = 14) -> pd.Series:
    """Relative Strength Index with Wilder's exponential smoothing."""
    delta = series.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)

    # Wilder's smoothing uses alpha = 1 / period
    avg_gain = gain.ewm(alpha=1.0 / period, min_periods=period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1.0 / period, min_periods=period, adjust=False).mean()

    rs = avg_gain / avg_loss.replace(0, np.nan)
    rsi = 100.0 - (100.0 / (1.0 + rs))
    return rsi.fillna(50.0)


def compute_atr(df: pd.DataFrame, period: int = 14) -> pd.Series:
    """Average True Range with Wilder's smoothing."""
    high = df["high"]
    low = df["low"]
    close = df["close"]
    prev_close = close.shift(1)

    tr1 = high - low
    tr2 = (high - prev_close).abs()
    tr3 = (low - prev_close).abs()
    tr = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)

    atr = tr.ewm(alpha=1.0 / period, min_periods=period, adjust=False).mean()
    return atr


def compute_adx(df: pd.DataFrame, period: int = 14) -> Tuple[pd.Series, pd.Series, pd.Series]:
    """ADX, +DI, and -DI with Wilder's smoothing."""
    high = df["high"]
    low = df["low"]
    close = df["close"]

    up_move = high.diff()
    down_move = -low.diff()

    plus_dm = np.where((up_move > down_move) & (up_move > 0), up_move, 0.0)
    minus_dm = np.where((down_move > up_move) & (down_move > 0), down_move, 0.0)

    atr = compute_atr(df, period)
    plus_di = 100.0 * pd.Series(plus_dm, index=df.index).ewm(alpha=1.0 / period, adjust=False).mean() / atr
    minus_di = 100.0 * pd.Series(minus_dm, index=df.index).ewm(alpha=1.0 / period, adjust=False).mean() / atr

    dx = 100.0 * (plus_di - minus_di).abs() / (plus_di + minus_di).replace(0, np.nan)
    adx = dx.ewm(alpha=1.0 / period, adjust=False).mean().fillna(20.0)

    return adx, plus_di, minus_di


def compute_bollinger_bands(series: pd.Series, period: int = 20, num_std: float = 2.0) -> Tuple[pd.Series, pd.Series, pd.Series]:
    """Bollinger Bands (Middle, Upper, Lower)."""
    mid = series.rolling(window=period).mean()
    std = series.rolling(window=period).std()
    upper = mid + (num_std * std)
    lower = mid - (num_std * std)
    return mid, upper, lower


def compute_macd(series: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9) -> Tuple[pd.Series, pd.Series, pd.Series]:
    """MACD line, Signal line, Histogram."""
    fast_ema = compute_ema(series, fast)
    slow_ema = compute_ema(series, slow)
    macd_line = fast_ema - slow_ema
    signal_line = compute_ema(macd_line, signal)
    hist = macd_line - signal_line
    return macd_line, signal_line, hist


def compute_supertrend(df: pd.DataFrame, period: int = 10, multiplier: float = 3.0) -> Tuple[pd.Series, pd.Series]:
    """
    Supertrend indicator.
    Returns (supertrend_values, direction): direction is 1 (bullish) or -1 (bearish).
    """
    high = df["high"].values
    low = df["low"].values
    close = df["close"].values
    atr = compute_atr(df, period).values

    n = len(df)
    upper_band = np.zeros(n)
    lower_band = np.zeros(n)
    supertrend = np.zeros(n)
    direction = np.zeros(n, dtype=int)

    for i in range(n):
        hl2 = (high[i] + low[i]) / 2.0
        basic_upper = hl2 + (multiplier * atr[i])
        basic_lower = hl2 - (multiplier * atr[i])

        if i == 0:
            upper_band[i] = basic_upper
            lower_band[i] = basic_lower
            direction[i] = 1
            supertrend[i] = lower_band[i]
            continue

        # Upper band adjustment
        if basic_upper < upper_band[i - 1] or close[i - 1] > upper_band[i - 1]:
            upper_band[i] = basic_upper
        else:
            upper_band[i] = upper_band[i - 1]

        # Lower band adjustment
        if basic_lower > lower_band[i - 1] or close[i - 1] < lower_band[i - 1]:
            lower_band[i] = basic_lower
        else:
            lower_band[i] = lower_band[i - 1]

        # Direction flip
        if direction[i - 1] == 1:
            if close[i] < lower_band[i]:
                direction[i] = -1
                supertrend[i] = upper_band[i]
            else:
                direction[i] = 1
                supertrend[i] = lower_band[i]
        else:
            if close[i] > upper_band[i]:
                direction[i] = 1
                supertrend[i] = lower_band[i]
            else:
                direction[i] = -1
                supertrend[i] = upper_band[i]

    return pd.Series(supertrend, index=df.index), pd.Series(direction, index=df.index)


def compute_vwap(df: pd.DataFrame) -> pd.Series:
    """Volume Weighted Average Price (intraday reset)."""
    typical_price = (df["high"] + df["low"] + df["close"]) / 3.0
    volume = df["volume"].replace(0, 1.0)
    pv = typical_price * volume

    # Cumulative intraday
    vwap = pv.cumsum() / volume.cumsum()
    return vwap


def compute_twap(df: pd.DataFrame) -> pd.Series:
    """Time Weighted Average Price."""
    typical_price = (df["high"] + df["low"] + df["close"]) / 3.0
    return typical_price.expanding().mean()


def compute_cpr_and_pivots(high: float, low: float, close: float) -> Dict[str, float]:
    """
    Central Pivot Range (CPR) and Classic Floor Pivots:
    - Pivot (P) = (H + L + C) / 3
    - Bottom Central (BC) = (H + L) / 2
    - Top Central (TC) = (P - BC) + P = 2*P - BC
    - R1 = 2*P - L, S1 = 2*P - H
    - R2 = P + (H - L), S2 = P - (H - L)
    - R3 = H + 2*(P - L), S3 = L - 2*(H - P)
    """
    pivot = (high + low + close) / 3.0
    bc = (high + low) / 2.0
    tc = (2.0 * pivot) - bc

    r1 = (2.0 * pivot) - low
    s1 = (2.0 * pivot) - high
    r2 = pivot + (high - low)
    s2 = pivot - (high - low)
    r3 = high + 2.0 * (pivot - low)
    s3 = low - 2.0 * (high - pivot)

    # Order tc and bc so that cpr_top >= cpr_bottom
    cpr_top = max(tc, bc)
    cpr_bottom = min(tc, bc)
    cpr_width = cpr_top - cpr_bottom
    cpr_width_pct = (cpr_width / pivot) * 100.0 if pivot > 0 else 0.0

    return {
        "pivot": pivot,
        "bc": bc,
        "tc": tc,
        "cpr_top": cpr_top,
        "cpr_bottom": cpr_bottom,
        "cpr_width": cpr_width,
        "cpr_width_pct": cpr_width_pct,
        "is_narrow": cpr_width_pct < 0.25, # Narrow CPR indicates trending day potential
        "r1": r1,
        "s1": s1,
        "r2": r2,
        "s2": s2,
        "r3": r3,
        "s3": s3,
    }
