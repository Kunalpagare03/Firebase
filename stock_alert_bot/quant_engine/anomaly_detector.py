"""
Manipulation & Market Anomaly Detector
Identifies abnormal market behaviors (OI bursts, IV shocks, price-volume divergence,
spoofing/liquidity drops, and pinning risk) and flags overall anomaly status.
"""

from typing import Dict, Any, List

class AnomalyDetector:
    def __init__(self):
        pass

    def inspect_market_behavior(
        self,
        spot_price_change_pct: float,
        option_oi_change_pct: float,
        volume_ratio: float,
        iv_change_pct: float = 0.0,
        bid_ask_spread_pct: float = 0.01
    ) -> Dict[str, Any]:
        """
        Evaluates potential abnormal activity and manipulation risk.
        Returns status: NORMAL, WATCH, ANOMALY, or HIGH_ANOMALY_RISK.
        """
        flags: List[str] = []
        anomaly_score = 0

        # Anomaly 1: Massive OI Spike without Spot Price Movement (Options Trap / Writing Pressure)
        if abs(spot_price_change_pct) < 0.15 and option_oi_change_pct > 40.0:
            flags.append("Abnormal OI buildup without spot movement (Potential Trap)")
            anomaly_score += 35

        # Anomaly 2: Extreme Volume Spike with minimal price action
        if volume_ratio > 4.0 and abs(spot_price_change_pct) < 0.2:
            flags.append("Volume surge with flat price action (Heavy Institutional Churn)")
            anomaly_score += 30

        # Anomaly 3: Sudden IV Shock / Expansion (>30% IV surge)
        if iv_change_pct > 30.0:
            flags.append("Unusual Implied Volatility shock")
            anomaly_score += 25

        # Anomaly 4: Liquidity Disappearance / Wide Bid-Ask Spread
        if bid_ask_spread_pct > 0.05:
            flags.append("Severe liquidity drop / wide bid-ask spread")
            anomaly_score += 20

        # Status Classification
        if anomaly_score >= 60:
            status = "HIGH_ANOMALY_RISK"
        elif anomaly_score >= 35:
            status = "ANOMALY"
        elif anomaly_score >= 20:
            status = "WATCH"
        else:
            status = "NORMAL"

        return {
            "status": status,
            "anomaly_score": anomaly_score,
            "flags": flags,
            "has_anomaly": anomaly_score >= 35,
            "summary": "; ".join(flags) if flags else "Normal market microstructure behavior"
        }
