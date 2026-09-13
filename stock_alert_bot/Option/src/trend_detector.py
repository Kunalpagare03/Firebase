# src/trend_detector.py
import pandas as pd
import numpy as np


def analyze_price_structure(values):
    """Classify trend from recent price structure, not just the latest candle."""
    prices = [float(value) for value in values if value is not None and pd.notna(value)]
    if len(prices) < 5:
        return {
            "trend": "Neutral",
            "pattern": "Insufficient price history",
            "higher_high": None,
            "higher_low": None,
            "lower_high": None,
            "lower_low": None,
            "confidence": 0,
        }

    prices = prices[-60:]
    window = max(2, len(prices) // 3)
    previous = prices[-(window * 2):-window]
    recent = prices[-window:]
    threshold = max((max(prices) - min(prices)) * 0.001, 0.5)

    previous_high, recent_high = max(previous), max(recent)
    previous_low, recent_low = min(previous), min(recent)
    higher_high = recent_high > previous_high + threshold
    higher_low = recent_low > previous_low + threshold
    lower_high = recent_high < previous_high - threshold
    lower_low = recent_low < previous_low - threshold

    if lower_high and lower_low:
        trend = "Bearish"
        pattern = "Lower highs and lower lows"
    elif higher_high and higher_low:
        trend = "Bullish"
        pattern = "Higher highs and higher lows"
    elif lower_low or (prices[-1] < prices[0] - threshold):
        trend = "Bearish"
        pattern = "Bearish structure: lower low"
    elif higher_high or (prices[-1] > prices[0] + threshold):
        trend = "Bullish"
        pattern = "Bullish structure: higher high"
    else:
        trend = "Neutral"
        pattern = "Range-bound / mixed structure"

    confirmations = sum((lower_high, lower_low)) if trend == "Bearish" else sum((higher_high, higher_low)) if trend == "Bullish" else 0
    return {
        "trend": trend,
        "pattern": pattern,
        "higher_high": higher_high,
        "higher_low": higher_low,
        "lower_high": lower_high,
        "lower_low": lower_low,
        "confidence": round(confirmations / 2, 2),
        "previous_high": round(previous_high, 2),
        "recent_high": round(recent_high, 2),
        "previous_low": round(previous_low, 2),
        "recent_low": round(recent_low, 2),
    }


def analyze_option_chain(file_path):
    """Analyze option chain data and detect trend changes."""
    df = pd.read_csv(file_path)
    df['captured_at'] = pd.to_datetime(df['captured_at'])
    df.sort_values('captured_at', inplace=True)

    summary = df.groupby('captured_at').agg({
        'spot_price': 'mean',
        'call_oi': 'sum',
        'put_oi': 'sum',
        'call_iv': 'mean',
        'put_iv': 'mean'
    }).reset_index()

    # Derived metrics
    summary['OI_ratio'] = summary['put_oi'] / summary['call_oi']
    summary['IV_diff'] = summary['call_iv'] - summary['put_iv']
    summary['Trend'] = np.where(summary['OI_ratio'] > 1.1, 'Bullish',
                         np.where(summary['OI_ratio'] < 0.9, 'Bearish', 'Neutral'))
    summary['Trend_Change'] = summary['Trend'].ne(summary['Trend'].shift())

    return summary
