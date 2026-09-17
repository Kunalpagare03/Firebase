# src/trend_detector.py
import pandas as pd
import numpy as np

def analyze_price_structure(values):
    """Detect high-probability institutional chart patterns."""
    prices = [float(value) for value in values if value is not None and pd.notna(value)]
    if len(prices) < 20:
        return {
            "trend": "Neutral",
            "pattern": "Analyzing Candles...",
            "chart_pattern": "Building Data",
            "confidence": 0
        }

    prices_arr = np.array(prices[-100:])
    n = len(prices_arr)

    # Peak and Trough detection
    # Split into 5 segments to find swings
    chunk = n // 5
    s1, s2, s3, s4, s5 = prices_arr[0:chunk], prices_arr[chunk:chunk*2], prices_arr[chunk*2:chunk*3], prices_arr[chunk*3:chunk*4], prices_arr[chunk*4:]

    h = [np.max(s1), np.max(s2), np.max(s3), np.max(s4), np.max(s5)]
    l = [np.min(s1), np.min(s2), np.min(s3), np.min(s4), np.min(s5)]

    # Calculate average volatility for thresholds
    volatility = (np.max(prices_arr) - np.min(prices_arr))
    thr = max(volatility * 0.05, 1.0) # 5% of range or 1 point

    detected = "Consolidating"
    confidence = 0.5

    # --- POWER PATTERNS (Most Reliable) ---

    # 1. Double Bottom (W-Pattern) - Reversal
    if abs(l[1] - l[3]) < thr and h[2] > l[1] + thr and prices_arr[-1] > h[2]:
        detected = "Double Bottom (W-Pattern)"
        confidence = 0.85

    # 2. Double Top (M-Pattern) - Reversal
    elif abs(h[1] - h[3]) < thr and l[2] < h[1] - thr and prices_arr[-1] < l[2]:
        detected = "Double Top (M-Pattern)"
        confidence = 0.85

    # 3. Bull Flag - Continuation
    elif h[2] > h[1] and h[4] < h[3] and l[4] < l[3] and prices_arr[-1] > h[4]:
        detected = "Bull Flag (Breakout)"
        confidence = 0.90

    # 4. Bear Flag - Continuation
    elif l[2] < l[1] and h[4] > h[3] and l[4] > l[3] and prices_arr[-1] < l[4]:
        detected = "Bear Flag (Breakdown)"
        confidence = 0.90

    # 5. Head & Shoulders - Major Reversal
    elif h[2] > h[1] + thr and h[2] > h[3] + thr and abs(h[1] - h[3]) < thr:
        detected = "Head & Shoulders"
        confidence = 0.88

    # 6. Inverse Head & Shoulders - Major Reversal
    elif l[2] < l[1] - thr and l[2] < l[3] - thr and abs(l[1] - l[3]) < thr:
        detected = "Inverse Head & Shoulders"
        confidence = 0.88

    # 7. Ascending Triangle - Bullish Breakout
    elif abs(h[1] - h[3]) < thr and l[3] > l[1] + thr:
        detected = "Ascending Triangle"
        confidence = 0.82

    # 8. Descending Triangle - Bearish Breakdown
    elif abs(l[1] - l[3]) < thr and h[3] < h[1] - thr:
        detected = "Descending Triangle"
        confidence = 0.82

    # 9. Cup and Handle
    elif h[0] > h[2] and abs(h[0] - h[4]) < thr and l[2] < l[1] and l[2] < l[3]:
        detected = "Cup and Handle"
        confidence = 0.80

    # 10. Rectangle Channel
    elif abs(h[1] - h[3]) < thr and abs(l[1] - l[3]) < thr:
        detected = "Rectangle Channel"
        confidence = 0.75

    # Trend Logic
    trend = "Neutral"
    if prices_arr[-1] > prices_arr[0]: trend = "Bullish"
    elif prices_arr[-1] < prices_arr[0]: trend = "Bearish"

    return {
        "trend": trend,
        "pattern": f"{trend} structure",
        "chart_pattern": detected,
        "confidence": confidence,
        "recent_high": round(float(np.max(prices_arr[-10:])), 2),
        "recent_low": round(float(np.min(prices_arr[-10:])), 2)
    }

def analyze_option_chain(file_path):
    df = pd.read_csv(file_path)
    df['captured_at'] = pd.to_datetime(df['captured_at'])
    df.sort_values('captured_at', inplace=True)
    summary = df.groupby('captured_at').agg({'spot_price': 'mean', 'call_oi': 'sum', 'put_oi': 'sum'}).reset_index()
    return summary
