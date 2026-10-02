"""
Expected Value (EV) & Risk/Reward (RR) Evaluation Engine
Calculates trade EV including win/loss probabilities, targets, stop-loss, spread, slippage, and costs.
Enforces strict RR >= 1:4 filtering.
"""

from typing import Dict, Any

class EvRrEngine:
    def __init__(self, min_rr_ratio: float = 4.0):
        self.min_rr_ratio = min_rr_ratio

    def evaluate_trade_setup(
        self,
        entry_price: float,
        target_price: float,
        stop_loss_price: float,
        win_probability: float,
        est_slippage_pct: float = 0.005,
        transaction_cost_per_trade: float = 40.0
    ) -> Dict[str, Any]:
        """
        Calculates Risk/Reward, Expected Value, and returns decision recommendation.
        """
        if entry_price <= 0 or target_price <= 0 or stop_loss_price <= 0:
            return {
                "decision": "NO_TRADE",
                "reason": "Invalid price inputs",
                "risk_reward_ratio": "1:0.0",
                "expected_value": 0.0
            }

        reward = abs(target_price - entry_price)
        risk = abs(entry_price - stop_loss_price)

        if risk <= 0:
            return {
                "decision": "NO_TRADE",
                "reason": "Zero or negative risk distance",
                "risk_reward_ratio": "1:0.0",
                "expected_value": 0.0
            }

        rr_numeric = reward / risk
        rr_str = f"1:{round(rr_numeric, 2)}"

        # Account for slippage on entry & exit
        effective_entry = entry_price * (1.0 + est_slippage_pct)
        effective_target = target_price * (1.0 - est_slippage_pct)
        effective_stop = stop_loss_price * (1.0 - est_slippage_pct)

        effective_reward = max(0.0, effective_target - effective_entry)
        effective_risk = max(1.0, effective_entry - effective_stop)

        loss_probability = max(0.0, 1.0 - win_probability)

        # Expected Value calculation (EV = Win_Prob * Gain - Loss_Prob * Loss - Costs)
        ev = (win_probability * effective_reward) - (loss_probability * effective_risk) - (transaction_cost_per_trade / 100.0)

        # Enforce minimum RR >= 1:4 and positive EV
        if rr_numeric < self.min_rr_ratio:
            decision = "NO_TRADE"
            reason = f"Risk/Reward {rr_str} below minimum required 1:{self.min_rr_ratio}"
        elif ev <= 0:
            decision = "NO_TRADE"
            reason = f"Negative expected value ({round(ev, 2)}) after slippage and costs"
        elif win_probability < 0.50:
            decision = "WAIT"
            reason = f"Win probability ({round(win_probability * 100, 1)}%) below 50% threshold"
        else:
            decision = "TRADE"
            reason = f"Strong setup with RR {rr_str} and positive EV ({round(ev, 2)})"

        return {
            "decision": decision,
            "reason": reason,
            "risk_reward_numeric": round(rr_numeric, 2),
            "risk_reward_ratio": rr_str,
            "expected_value": round(ev, 2),
            "win_probability_pct": round(win_probability * 100, 1),
            "effective_risk": round(effective_risk, 2),
            "effective_reward": round(effective_reward, 2),
            "meets_min_rr": rr_numeric >= self.min_rr_ratio
        }
