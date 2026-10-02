"""
HFT / Ultra-Short-Term Momentum Burst Detector
Monitors microstructure volume surges, order flow imbalance proxies, and immediate breakout bursts.
"""

from typing import Dict, Any

class HftBurstDetector:
    def __init__(self):
        pass

    def detect_burst(
        self,
        symbol: str,
        spot_price: float,
        tick_rate_10s: float,
        move_10s_pct: float,
        volume_ratio: float
    ) -> Dict[str, Any]:
        """
        Detects ultra-short term volatility & momentum bursts.
        """
        is_burst = (tick_rate_10s > 15.0 and abs(move_10s_pct) > 0.12) or (volume_ratio > 3.0 and abs(move_10s_pct) > 0.15)
        
        direction = "NEUTRAL"
        if move_10s_pct > 0.1:
            direction = "BULLISH_BURST"
        elif move_10s_pct < -0.1:
            direction = "BEARISH_BURST"

        holding_time = "3-10 minutes" if is_burst else "N/A"

        return {
            "is_hft_burst": is_burst,
            "direction": direction,
            "tick_rate_10s": round(tick_rate_10s, 1),
            "move_10s_pct": round(move_10s_pct, 3),
            "expected_holding_time": holding_time,
            "signal_note": f"Rapid activity burst: {direction} with {round(tick_rate_10s, 1)} ticks/10s" if is_burst else "Normal tick velocity"
        }
