"""Big-Move Watch Agent (Part A4, C4)."""

from typing import Dict, Optional


def evaluate_big_move_watch(
    vix: float,
    spot_open: float,
    spot_high: float,
    spot_low: float,
    prev_close: float,
    vix_threshold: float = 22.0,
    range_pct_threshold: float = 0.025,
    gap_pct_threshold: float = 0.012,
) -> Dict[str, any]:
    """
    Big-Move Watch evaluates three volatile regime triggers:
    1. VIX >= 22.0 (High Implied Volatility regime)
    2. Day Range >= 2.5% ((High - Low) / Prev_Close)
    3. Gap >= 1.2% (abs(Open - Prev_Close) / Prev_Close)
    """
    if prev_close <= 0:
        return {"is_active": False, "reasons": []}

    day_range = (spot_high - spot_low) / prev_close
    gap = abs(spot_open - prev_close) / prev_close

    reasons = []
    if vix >= vix_threshold:
        reasons.append(f"Elevated India VIX: {vix:.2f} >= {vix_threshold:.1f}")
    if day_range >= range_pct_threshold:
        reasons.append(f"Wide Intraday Range: {day_range*100:.2f}% >= {range_pct_threshold*100:.1f}%")
    if gap >= gap_pct_threshold:
        direction = "UP" if spot_open > prev_close else "DOWN"
        reasons.append(f"Large Gap {direction}: {gap*100:.2f}% >= {gap_pct_threshold*100:.1f}%")

    is_active = len(reasons) > 0

    return {
        "is_active": is_active,
        "mode": "INFO",
        "vix": vix,
        "day_range_pct": day_range * 100.0,
        "gap_pct": gap * 100.0,
        "reasons": reasons,
        "guidance": "Wide strikes & reduced size recommended. Beware gamma expansion." if is_active else "Normal volatility regime.",
    }
