"""
Expiry-Day "Hero Zero" Strategy Engine
Scans for low-premium, high-gamma option contracts on expiry days with explosive potential.
Differentiates legitimate high-gamma setups from theta decay traps.
"""

from typing import Dict, Any

class HeroZeroEngine:
    def __init__(self):
        pass

    def evaluate_hero_zero_setup(
        self,
        symbol: str,
        spot_price: float,
        strike_price: float,
        option_type: str,
        premium: float,
        days_to_expiry: float,
        underlying_momentum_score: float,
        pcr_oi: float
    ) -> Dict[str, Any]:
        """
        Evaluates an expiry-day low premium option for Hero Zero potential.
        """
        # Hero Zero applies primarily on 0-DTE expiry days
        is_expiry_day = days_to_expiry <= 0.25

        if not is_expiry_day:
            return {
                "is_hero_zero_candidate": False,
                "recommendation": "NO_HERO_ZERO_TRADE",
                "reason": "Not an expiry day (Days to expiry > 0.25)",
                "target_multiplier": 1.0
            }

        # Premium filter: Hero Zero contracts are typically low premium (Rs. 5 - Rs. 35)
        if premium < 3.0 or premium > 50.0:
            return {
                "is_hero_zero_candidate": False,
                "recommendation": "NO_HERO_ZERO_TRADE",
                "reason": f"Premium Rs.{round(premium, 2)} outside Hero Zero window (Rs. 5 - Rs. 50)",
                "target_multiplier": 1.0
            }

        distance_pct = abs(spot_price - strike_price) / spot_price * 100.0

        # OTM Strike Check: Must be within 1.5% of spot to avoid pure lottery tickets
        if distance_pct > 1.5:
            return {
                "is_hero_zero_candidate": False,
                "recommendation": "NO_HERO_ZERO_TRADE",
                "reason": f"Strike is too far OTM ({round(distance_pct, 2)}% from spot)",
                "target_multiplier": 1.0
            }

        # Check alignment: CE requires bullish momentum & high PCR; PE requires bearish momentum & low PCR
        aligned = False
        if option_type == "CE" and underlying_momentum_score > 60 and pcr_oi > 1.0:
            aligned = True
        elif option_type == "PE" and underlying_momentum_score < 40 and pcr_oi < 1.0:
            aligned = True

        if not aligned:
            return {
                "is_hero_zero_candidate": False,
                "recommendation": "NO_HERO_ZERO_TRADE",
                "reason": "Option direction not aligned with spot momentum and PCR buildup",
                "target_multiplier": 1.0
            }

        # Calculate explosive target (2x to 4x premium) and strict SL
        target_1 = round(premium * 2.0, 2)
        target_2 = round(premium * 4.0, 2)
        stop_loss = round(max(1.0, premium * 0.4), 2)

        return {
            "is_hero_zero_candidate": True,
            "recommendation": "HERO_ZERO_TRIGGERED",
            "reason": f"High Gamma Expiry Setup detected for {symbol} {strike_price} {option_type}",
            "contract": f"{symbol} {int(strike_price)} {option_type}",
            "entry_premium": round(premium, 2),
            "target_1_premium": target_1,
            "target_2_premium": target_2,
            "stop_loss_premium": stop_loss,
            "target_multiplier": "2x - 4x",
            "expected_holding_time": "15-45 minutes"
        }
