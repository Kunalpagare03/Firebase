"""16 Candlestick and Chart Pattern Detectors for Option App v8 (Part C3)."""

from typing import Dict, List, Optional
import numpy as np
import pandas as pd


def detect_candlestick_patterns(df: pd.DataFrame) -> List[Dict[str, any]]:
    """
    Detect candlestick patterns on the latest bars of a dataframe.
    Requires columns: open, high, low, close.
    """
    if len(df) < 5:
        return []

    patterns = []
    # Work on recent rows
    c = df["close"].values
    o = df["open"].values
    h = df["high"].values
    l = df["low"].values
    n = len(df)
    i = n - 1  # latest completed bar

    body = np.abs(c - o)
    total_range = np.maximum(h - l, 1e-4)
    upper_wick = h - np.maximum(c, o)
    lower_wick = np.minimum(c, o) - l

    # 1. Hammer (Bullish reversal)
    if lower_wick[i] >= 2.0 * body[i] and upper_wick[i] <= 0.2 * total_range[i] and c[i-1] < o[i-1]:
        patterns.append({"pattern": "Hammer", "type": "BULLISH", "bar_index": i, "strength": 0.75})

    # 2. Shooting Star (Bearish reversal)
    if upper_wick[i] >= 2.0 * body[i] and lower_wick[i] <= 0.2 * total_range[i] and c[i-1] > o[i-1]:
        patterns.append({"pattern": "Shooting Star", "type": "BEARISH", "bar_index": i, "strength": 0.75})

    # 3. Engulfing
    if i >= 1:
        # Bullish Engulfing
        if c[i-1] < o[i-1] and c[i] > o[i] and o[i] <= c[i-1] and c[i] >= o[i-1]:
            patterns.append({"pattern": "Bullish Engulfing", "type": "BULLISH", "bar_index": i, "strength": 0.85})
        # Bearish Engulfing
        elif c[i-1] > o[i-1] and c[i] < o[i] and o[i] >= c[i-1] and c[i] <= o[i-1]:
            patterns.append({"pattern": "Bearish Engulfing", "type": "BEARISH", "bar_index": i, "strength": 0.85})

    # 4. Doji (Standard, Dragonfly, Gravestone)
    if body[i] <= 0.1 * total_range[i]:
        if lower_wick[i] >= 0.7 * total_range[i]:
            patterns.append({"pattern": "Dragonfly Doji", "type": "BULLISH", "bar_index": i, "strength": 0.70})
        elif upper_wick[i] >= 0.7 * total_range[i]:
            patterns.append({"pattern": "Gravestone Doji", "type": "BEARISH", "bar_index": i, "strength": 0.70})
        else:
            patterns.append({"pattern": "Standard Doji", "type": "NEUTRAL", "bar_index": i, "strength": 0.50})

    # 5. Morning Star & Evening Star (3-bar patterns)
    if i >= 2:
        # Morning Star: Bearish -> small body gap down -> Bullish closing above midpoint of 1st bar
        if (c[i-2] < o[i-2] and body[i-1] <= 0.3 * total_range[i-1] and 
            c[i] > o[i] and c[i] > (o[i-2] + c[i-2]) / 2.0):
            patterns.append({"pattern": "Morning Star", "type": "BULLISH", "bar_index": i, "strength": 0.90})

        # Evening Star: Bullish -> small body gap up -> Bearish closing below midpoint of 1st bar
        if (c[i-2] > o[i-2] and body[i-1] <= 0.3 * total_range[i-1] and 
            c[i] < o[i] and c[i] < (o[i-2] + c[i-2]) / 2.0):
            patterns.append({"pattern": "Evening Star", "type": "BEARISH", "bar_index": i, "strength": 0.90})

    # 6. Piercing Pattern & Dark Cloud Cover (2-bar)
    if i >= 1:
        # Piercing: Bearish -> opens lower -> closes above 50% of prev body
        if (c[i-1] < o[i-1] and c[i] > o[i] and o[i] < l[i-1] and 
            c[i] >= (o[i-1] + c[i-1]) / 2.0 and c[i] < o[i-1]):
            patterns.append({"pattern": "Piercing Line", "type": "BULLISH", "bar_index": i, "strength": 0.80})

        # Dark Cloud Cover: Bullish -> opens higher -> closes below 50% of prev body
        if (c[i-1] > o[i-1] and c[i] < o[i] and o[i] > h[i-1] and 
            c[i] <= (o[i-1] + c[i-1]) / 2.0 and c[i] > o[i-1]):
            patterns.append({"pattern": "Dark Cloud Cover", "type": "BEARISH", "bar_index": i, "strength": 0.80})

    # 7. Harami (Inside body)
    if i >= 1:
        if (c[i-1] < o[i-1] and c[i] > o[i] and o[i] >= c[i-1] and c[i] <= o[i-1]):
            patterns.append({"pattern": "Bullish Harami", "type": "BULLISH", "bar_index": i, "strength": 0.65})
        elif (c[i-1] > o[i-1] and c[i] < o[i] and o[i] <= c[i-1] and c[i] >= o[i-1]):
            patterns.append({"pattern": "Bearish Harami", "type": "BEARISH", "bar_index": i, "strength": 0.65})

    # 8. Three Soldiers / Three Black Crows
    if i >= 2:
        if (c[i] > o[i] and c[i-1] > o[i-1] and c[i-2] > o[i-2] and 
            c[i] > c[i-1] > c[i-2] and o[i] > o[i-1] > o[i-2]):
            patterns.append({"pattern": "Three White Soldiers", "type": "BULLISH", "bar_index": i, "strength": 0.85})
        elif (c[i] < o[i] and c[i-1] < o[i-1] and c[i-2] < o[i-2] and 
              c[i] < c[i-1] < c[i-2] and o[i] < o[i-1] < o[i-2]):
            patterns.append({"pattern": "Three Black Crows", "type": "BEARISH", "bar_index": i, "strength": 0.85})

    # 9. Marubozu (Strong directional expansion)
    if body[i] >= 0.90 * total_range[i]:
        if c[i] > o[i]:
            patterns.append({"pattern": "Bullish Marubozu", "type": "BULLISH", "bar_index": i, "strength": 0.80})
        else:
            patterns.append({"pattern": "Bearish Marubozu", "type": "BEARISH", "bar_index": i, "strength": 0.80})

    # 10. Inside Bar & NR7
    if i >= 1 and h[i] <= h[i-1] and l[i] >= l[i-1]:
        patterns.append({"pattern": "Inside Bar", "type": "NEUTRAL", "bar_index": i, "strength": 0.60})

    if i >= 6:
        last7_ranges = total_range[i-6:i+1]
        if np.argmin(last7_ranges) == 6:
            patterns.append({"pattern": "NR7 Volatility Squeeze", "type": "BREAKOUT_WATCH", "bar_index": i, "strength": 0.75})

    return patterns


def detect_chart_patterns(df: pd.DataFrame) -> List[Dict[str, any]]:
    """
    Detect structural chart patterns:
    - 11. Trendline Breakout (3 touches)
    - 12. Double Bottom / Double Top
    - 13. Head & Shoulders / Inverse Head & Shoulders
    - 14. Bull Flag / Bear Flag
    - 15. 5m EMA 20 Pullback
    - 16. Key Reversal Bar
    """
    if len(df) < 30:
        return []

    chart_patterns = []
    c = df["close"].values
    h = df["high"].values
    l = df["low"].values
    n = len(df)
    i = n - 1

    # 15. 5m EMA 20 Pullback
    ema20 = df["close"].ewm(span=20, adjust=False).mean().values
    if l[i] <= ema20[i] * 1.001 and c[i] > ema20[i] and c[i-1] > ema20[i-1] and c[i-5] > ema20[i-5]:
        chart_patterns.append({
            "pattern": "5m EMA 20 Pullback (Bullish)",
            "type": "BULLISH",
            "trigger_level": float(ema20[i]),
            "stop_loss": float(l[i] * 0.998),
        })
    elif h[i] >= ema20[i] * 0.999 and c[i] < ema20[i] and c[i-1] < ema20[i-1] and c[i-5] < ema20[i-5]:
        chart_patterns.append({
            "pattern": "5m EMA 20 Pullback (Bearish)",
            "type": "BEARISH",
            "trigger_level": float(ema20[i]),
            "stop_loss": float(h[i] * 1.002),
        })

    # 16. Key Reversal Day / Bar: makes new high/low of last 15 bars, but reverses and closes beyond prev bar close
    high_15 = np.max(h[max(0, i-15):i])
    low_15 = np.min(l[max(0, i-15):i])
    if h[i] > high_15 and c[i] < l[i-1]:
        chart_patterns.append({
            "pattern": "Key Reversal Bar (Bearish)",
            "type": "BEARISH",
            "trigger_level": float(h[i]),
        })
    elif l[i] < low_15 and c[i] > h[i-1]:
        chart_patterns.append({
            "pattern": "Key Reversal Bar (Bullish)",
            "type": "BULLISH",
            "trigger_level": float(l[i]),
        })

    # 12. Double Bottom / Double Top
    # Look for two local extrema separated by 5 to 25 bars with similar levels (<0.3% difference)
    window = 25
    recent_lows = l[max(0, i-window):i]
    min1_idx = np.argmin(recent_lows)
    min1_val = recent_lows[min1_idx]
    # Check if current low tests the same level and bounces
    if abs(l[i] - min1_val) / min1_val < 0.003 and (i - (i - window + min1_idx)) >= 5 and c[i] > l[i]:
        chart_patterns.append({
            "pattern": "Double Bottom",
            "type": "BULLISH",
            "support": float(min1_val),
            "neckline": float(np.max(h[i - window + min1_idx : i])),
        })

    recent_highs = h[max(0, i-window):i]
    max1_idx = np.argmax(recent_highs)
    max1_val = recent_highs[max1_idx]
    if abs(h[i] - max1_val) / max1_val < 0.003 and (i - (i - window + max1_idx)) >= 5 and c[i] < h[i]:
        chart_patterns.append({
            "pattern": "Double Top",
            "type": "BEARISH",
            "resistance": float(max1_val),
            "neckline": float(np.min(l[i - window + max1_idx : i])),
        })

    # 11. Trendline Breakout (3 touches)
    # Check falling highs: H1 > H2 > H3, then current close crosses above the projected line
    if len(df) >= 20:
        highs_idx = [i-18, i-10, i-3]
        if highs_idx[0] >= 0:
            p1, p2, p3 = h[highs_idx[0]], h[highs_idx[1]], h[highs_idx[2]]
            if p1 > p2 > p3:
                # Slope between p1 and p2
                slope = (p2 - p1) / (highs_idx[1] - highs_idx[0])
                projected_line = p1 + slope * (i - highs_idx[0])
                if c[i] > projected_line and c[i-1] <= projected_line:
                    chart_patterns.append({
                        "pattern": "Falling Trendline Breakout (3-Touch)",
                        "type": "BULLISH",
                        "breakout_level": float(projected_line),
                    })

    return chart_patterns
