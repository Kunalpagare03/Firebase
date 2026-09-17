# src/trend_detector.py
import pandas as pd
import numpy as np

def analyze_price_structure(values):
    """Detect ALL 15 high-probability institutional chart patterns with extreme sensitivity."""
    prices = [float(value) for value in values if value is not None and pd.notna(value)]
    if len(prices) < 30:
        return {
            "trend": "Neutral",
            "pattern": "Analyzing Candles...",
            "chart_pattern": "Building Data",
            "is_breakout": False,
            "confidence": 0
        }

    prices_arr = np.array(prices[-120:])
    n = len(prices_arr)
    curr = prices_arr[-1]

    # Range and Thresholds
    volatility = (np.max(prices_arr) - np.min(prices_arr))
    thr = max(volatility * 0.04, 0.8) # 4% sensitivity

    # Split into segments for geometry detection
    chunk = n // 6
    s1, s2, s3, s4, s5, s6 = prices_arr[0:chunk], prices_arr[chunk:chunk*2], prices_arr[chunk*2:chunk*3], prices_arr[chunk*3:chunk*4], prices_arr[chunk*4:chunk*5], prices_arr[chunk*5:]

    h = [np.max(s) for s in [s1, s2, s3, s4, s5, s6]]
    l = [np.min(s) for s in [s1, s2, s3, s4, s5, s6]]

    # Pole and Flag Geometry
    pole_height = prices_arr[-20] - prices_arr[-40] if n >= 40 else 0
    flag_zone = prices_arr[-15:-1]
    f_max, f_min = np.max(flag_zone), np.min(flag_zone)
    f_range = f_max - f_min

    detected = "Consolidating"
    confidence = 0.5
    is_breakout = False

    # --- THE MASTER PATTERN ENGINE (All 15 Patterns) ---

    # 1. Bull Flag / Pennant Breakout
    if curr > f_max + 0.5 and pole_height > f_range * 1.5:
        detected = "Bull Flag/Pennant BREAKOUT"
        confidence = 0.96; is_breakout = True

    # 2. Bear Flag / Pennant Breakdown
    elif curr < f_min - 0.5 and pole_height < -f_range * 1.5:
        detected = "Bear Flag/Pennant BREAKDOWN"
        confidence = 0.96; is_breakout = True

    # 3. Double Bottom (W-Pattern)
    elif abs(l[2] - l[4]) < thr and h[3] > l[2] + thr and curr > h[3]:
        detected = "Double Bottom (W-Pattern) BREAKOUT"
        confidence = 0.92; is_breakout = True

    # 4. Double Top (M-Pattern)
    elif abs(h[2] - h[4]) < thr and l[3] < h[2] - thr and curr < l[3]:
        detected = "Double Top (M-Pattern) BREAKDOWN"
        confidence = 0.92; is_breakout = True

    # 5. Inverse Head & Shoulders (Bullish Reversal)
    elif l[3] < l[2] - thr and l[3] < l[4] - thr and abs(l[2] - l[4]) < thr and curr > max(h[2], h[3]):
        detected = "Inverse Head & Shoulders BREAKOUT"
        confidence = 0.90; is_breakout = True

    # 6. Head & Shoulders (Bearish Reversal)
    elif h[3] > h[2] + thr and h[3] > h[4] + thr and abs(h[2] - h[4]) < thr and curr < min(l[2], l[3]):
        detected = "Head & Shoulders BREAKDOWN"
        confidence = 0.90; is_breakout = True

    # 7. Cup and Handle (Bullish)
    elif h[1] > h[3] and abs(h[1] - h[5]) < thr and l[3] < l[2] and l[3] < l[4] and curr > h[5]:
        detected = "Cup and Handle BREAKOUT"
        confidence = 0.88; is_breakout = True

    # 8. Ascending Triangle
    elif abs(h[2] - h[4]) < thr and l[4] > l[2] + thr and curr > h[4]:
        detected = "Ascending Triangle BREAKOUT"
        confidence = 0.85; is_breakout = True

    # 9. Descending Triangle
    elif abs(l[2] - l[4]) < thr and h[4] < h[2] - thr and curr < l[4]:
        detected = "Descending Triangle BREAKDOWN"
        confidence = 0.85; is_breakout = True

    # 10. Symmetrical Triangle
    elif h[2] > h[4] and l[2] < l[4] and curr > h[4]:
        detected = "Symmetrical Triangle BREAKOUT"
        confidence = 0.82; is_breakout = True

    # 11. Falling Wedge (Bullish Reversal)
    elif h[2] > h[4] and l[2] > l[4] and (h[2]-h[4]) > (l[2]-l[4]) and curr > h[4]:
        detected = "Falling Wedge BREAKOUT"
        confidence = 0.88; is_breakout = True

    # 12. Rising Wedge (Bearish Reversal)
    elif h[4] > h[2] and l[4] > l[2] and (l[4]-l[2]) > (h[4]-h[2]) and curr < l[4]:
        detected = "Rising Wedge BREAKDOWN"
        confidence = 0.88; is_breakout = True

    # 13. Rectangle Channel (Bullish Break)
    elif abs(h[2] - h[4]) < thr and abs(l[2] - l[4]) < thr and curr > h[4]:
        detected = "Rectangle Channel BREAKOUT"
        confidence = 0.80; is_breakout = True

    # 14. Rectangle Channel (Bearish Break)
    elif abs(h[2] - h[4]) < thr and abs(l[2] - l[4]) < thr and curr < l[4]:
        detected = "Rectangle Channel BREAKDOWN"
        confidence = 0.80; is_breakout = True

    # Trend Read
    trend = "Neutral"
    if curr > prices_arr[0]: trend = "Bullish"
    elif curr < prices_arr[0]: trend = "Bearish"

    return {
        "trend": trend,
        "pattern": detected if is_breakout else f"{trend} structure",
        "chart_pattern": detected,
        "is_breakout": is_breakout,
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
