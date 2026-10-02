"""
Fake Breakout & Fake Breakdown Detection Module (Option Quant Engine)
Identifies Bull Traps (Fake Breakouts) and Bear Traps (Fake Breakdowns) using price action,
wick ratio, volume confirmation, and option chain OI writing patterns.
"""

from typing import Dict, Any

class FakeBreakoutDetector:
    def __init__(self):
        pass

    def inspect_breakout(
        self,
        spot_price: float,
        resistance_level: float,
        support_level: float,
        candle_open: float,
        candle_high: float,
        candle_low: float,
        candle_close: float,
        volume_ratio: float,
        call_oi_change_pct: float = 0.0,
        put_oi_change_pct: float = 0.0,
        rsi: float = 50.0
    ) -> Dict[str, Any]:
        """
        Analyzes whether a breakout or breakdown is genuine or a fake trap.
        """
        candle_range = max(0.01, candle_high - candle_low)
        upper_wick = candle_high - max(candle_open, candle_close)
        lower_wick = min(candle_open, candle_close) - candle_low

        upper_wick_ratio = upper_wick / candle_range
        lower_wick_ratio = lower_wick / candle_range

        is_above_resistance = spot_price >= resistance_level and resistance_level > 0
        is_below_support = spot_price <= support_level and support_level > 0

        status = "NORMAL_PRICE_ACTION"
        is_fake_breakout = False
        is_fake_breakdown = False
        reason = "Price action within normal support/resistance bounds."
        recommendation = "NEUTRAL"

        # 1. FAKE BREAKOUT (BULL TRAP) DETECTION
        if is_above_resistance or (candle_high > resistance_level and resistance_level > 0):
            # Bull Trap Criteria:
            # - Volume fails to expand (< 1.3x SMA20)
            # - Rejection upper wick (> 35% of range)
            # - Call OI writing surge (Call writers capping the move)
            if volume_ratio < 1.3 or upper_wick_ratio > 0.35 or call_oi_change_pct > 25.0:
                status = "FAKE_BREAKOUT"
                is_fake_breakout = True
                recommendation = "AVOID_BUYING_CE_OR_CONSIDER_PE_REVERSAL"
                reason = (
                    f"Fake Breakout (Bull Trap) detected above ₹{resistance_level:.0f}: "
                    f"Volume ratio ({volume_ratio:.2f}x) unconfirmed, upper rejection wick ({upper_wick_ratio*100:.0f}%), "
                    f"heavy Call writing (+{call_oi_change_pct:.1f}% OI)."
                )
            else:
                status = "VALID_BREAKOUT"
                recommendation = "CONFIRMED_BULLISH_BREAKOUT"
                reason = f"Confirmed Breakout above ₹{resistance_level:.0f} with {volume_ratio:.2f}x volume support."

        # 2. FAKE BREAKDOWN (BEAR TRAP) DETECTION
        elif is_below_support or (candle_low < support_level and support_level > 0):
            # Bear Trap Criteria:
            # - Volume fails to expand (< 1.3x SMA20)
            # - Rejection lower wick (> 35% of range)
            # - Put OI writing surge (Put writers defending support)
            if volume_ratio < 1.3 or lower_wick_ratio > 0.35 or put_oi_change_pct > 25.0:
                status = "FAKE_BREAKDOWN"
                is_fake_breakdown = True
                recommendation = "AVOID_BUYING_PE_OR_CONSIDER_CE_REVERSAL"
                reason = (
                    f"Fake Breakdown (Bear Trap) detected below ₹{support_level:.0f}: "
                    f"Volume ratio ({volume_ratio:.2f}x) unconfirmed, lower rejection wick ({lower_wick_ratio*100:.0f}%), "
                    f"heavy Put writing (+{put_oi_change_pct:.1f}% OI)."
                )
            else:
                status = "VALID_BREAKDOWN"
                recommendation = "CONFIRMED_BEARISH_BREAKDOWN"
                reason = f"Confirmed Breakdown below ₹{support_level:.0f} with {volume_ratio:.2f}x volume support."

        return {
            "status": status,
            "is_fake_breakout": is_fake_breakout,
            "is_fake_breakdown": is_fake_breakdown,
            "has_trap": is_fake_breakout or is_fake_breakdown,
            "recommendation": recommendation,
            "reason": reason,
            "upper_wick_ratio": round(upper_wick_ratio, 2),
            "lower_wick_ratio": round(lower_wick_ratio, 2),
            "volume_ratio": round(volume_ratio, 2)
        }
