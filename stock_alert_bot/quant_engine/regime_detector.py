"""
Regime Detection & Multi-Horizon Probability Calibration Engine
Analyzes market regime (Trending, Sideways, Volatile, Breakout, Reversal, Compressed)
and provides calibrated multi-horizon forecasts (1, 3, 5, 10 candles).
"""

import math
from typing import Dict, Any, List

class RegimeDetector:
    def __init__(self):
        pass

    def detect_regime(
        self,
        rsi: float,
        adx: float,
        volume_ratio: float,
        atr: float,
        price_change_pct: float,
        bb_width_pct: float = 0.02
    ) -> Dict[str, Any]:
        """
        Classifies the current market regime based on technical features.
        """
        regime = "Sideways"
        confidence = 0.5
        
        if bb_width_pct < 0.015 and adx < 20:
            regime = "Compressed"
            confidence = 0.85
        elif volume_ratio > 2.0 and abs(price_change_pct) > 0.8:
            regime = "Breakout"
            confidence = 0.90
        elif adx >= 25:
            if price_change_pct > 0.3 and rsi >= 55:
                regime = "Trending (Bullish)"
                confidence = min(0.95, 0.5 + (adx / 100.0))
            elif price_change_pct < -0.3 and rsi <= 45:
                regime = "Trending (Bearish)"
                confidence = min(0.95, 0.5 + (adx / 100.0))
            else:
                regime = "Volatile"
                confidence = 0.70
        elif (rsi > 70 and price_change_pct < 0) or (rsi < 30 and price_change_pct > 0):
            regime = "Reversal"
            confidence = 0.80
        else:
            regime = "Sideways"
            confidence = 0.65

        return {
            "regime": regime,
            "confidence": round(confidence, 2),
            "adx": adx,
            "rsi": rsi,
            "is_trending": "Trending" in regime or regime == "Breakout",
            "is_compressed": regime == "Compressed"
        }

    def calculate_multi_horizon_probabilities(
        self,
        rsi: float,
        pcr_oi: float,
        oi_buildup: str,
        regime: str,
        volume_ratio: float
    ) -> Dict[str, Dict[str, float]]:
        """
        Estimates calibrated probabilities over 1, 3, 5, and 10 candles.
        """
        # Base bullish bias score from inputs (-1.0 to +1.0)
        bias = 0.0
        
        if rsi > 55: bias += 0.25
        elif rsi < 45: bias -= 0.25
        
        if pcr_oi > 1.2: bias += 0.30
        elif pcr_oi < 0.8: bias -= 0.30
        
        if "Long Buildup" in oi_buildup or "Short Covering" in oi_buildup:
            bias += 0.30
        elif "Short Buildup" in oi_buildup or "Long Unwinding" in oi_buildup:
            bias -= 0.30

        if volume_ratio > 1.5:
            bias *= 1.2

        # Sigmoid calibration to map bias into true probabilities
        def sigmoid(x: float) -> float:
            return 1.0 / (1.0 + math.exp(-x))

        raw_bull = sigmoid(bias * 1.5)
        raw_bear = sigmoid(-bias * 1.5)

        horizons = [1, 3, 5, 10]
        results = {}

        for h in horizons:
            # Decay towards mean as horizon increases
            decay = math.exp(-0.05 * (h - 1))
            bull_p = 0.33 + (raw_bull - 0.5) * decay
            bear_p = 0.33 + (raw_bear - 0.5) * decay
            side_p = max(0.05, 1.0 - bull_p - bear_p)

            # Normalize to sum exactly 100%
            total = bull_p + bear_p + side_p
            results[f"horizon_{h}_candle"] = {
                "bullish_pct": round((bull_p / total) * 100, 1),
                "bearish_pct": round((bear_p / total) * 100, 1),
                "sideways_pct": round((side_p / total) * 100, 1)
            }

        return results
