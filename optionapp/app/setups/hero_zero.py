"""Afternoon Hero-Zero Expiry Setup (Part A4, C4)."""

from datetime import datetime, time
from typing import Any, Dict, Optional
from optionapp.app.setups.base import SetupAgent, AlertMessage
from optionapp.app.calendar import round_to_strike, get_strike_step, get_lot_size
from optionapp.app.analytics.options_math import black76_price, check_hero_zero_discount
from optionapp.app.log import get_logger

logger = get_logger("hero_zero")


class AfternoonHeroZeroAgent(SetupAgent):
    def __init__(self, name: str = "afternoon_hero_zero", symbol: str = "NIFTY", max_ask_pct: float = 0.70):
        super().__init__(name=name, mode="PAPER", symbol=symbol)
        self.max_ask_pct = max_ask_pct

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        current_time = now.time()

        # Window: 13:15 to 14:45 on expiry day
        if not (time(13, 15) <= current_time <= time(14, 45)):
            return None

        # Check directional bias
        direction_bias = context.get("direction_bias", "NEUTRAL")
        if direction_bias not in ("BREAKOUT_CALL", "BREAKDOWN_PUT", "STRONG_BULLISH", "STRONG_BEARISH"):
            return None

        spot = float(context.get("spot", 0.0))
        if spot <= 0:
            return None

        step = get_strike_step(self.symbol)
        atm = round_to_strike(spot, step)
        vix = float(context.get("vix", 14.5))
        iv = vix / 100.0

        # Remaining time to expiry in years (~2 hours = 2 / (365 * 24))
        remaining_hours = max(0.5, (15.5 - (current_time.hour + current_time.minute / 60.0)))
        t_years = remaining_hours / (365.0 * 24.0)

        chain = context.get("chain", {})
        chain_rows = chain.get("data", [])

        is_call = "CALL" in direction_bias or "BULLISH" in direction_bias
        opt_type = "CE" if is_call else "PE"

        # Select target strike: ATM or 1 strike OTM
        target_strike = atm if is_call else atm
        if "BREAKOUT_CALL" in direction_bias:
            target_strike = atm + step  # slightly OTM
        elif "BREAKDOWN_PUT" in direction_bias:
            target_strike = atm - step

        # Find option row
        target_row = next((r for r in chain_rows if float(r.get("strike_price", 0)) == target_strike), None)
        leg_data = (target_row.get("CALL" if is_call else "PUT") or {}) if target_row else {}

        ask_price = float(leg_data.get("ltp", 0.0))
        if ask_price <= 0:
            # Fallback estimation for simulation
            ask_price = round(black76_price(is_call, spot, target_strike, t_years, iv, 0.06), 2)

        # Theoretical fair price
        fair_price = black76_price(is_call, spot, target_strike, t_years, iv, 0.06)
        is_discounted, ratio = check_hero_zero_discount(ask_price, fair_price, self.max_ask_pct)

        # Alert if candidate passes discount filter or is evaluated
        lot_size = get_lot_size(self.symbol)
        stage = "READY" if (is_discounted or ask_price <= 25.0) else "FORMING"

        return AlertMessage(
            timestamp=now.isoformat(),
            setup_name=self.name,
            symbol=self.symbol,
            stage=stage,
            mode=self.mode,
            details={
                "direction": direction_bias,
                "option_type": opt_type,
                "strike": target_strike,
                "ask_price": ask_price,
                "fair_price": round(fair_price, 2),
                "discount_ratio": round(ratio, 2),
                "lot_size": lot_size,
                "max_risk_inr": round(ask_price * lot_size, 2),
                "target_1": round(ask_price * 2.0, 2),
                "target_2": round(ask_price * 3.0, 2),
                "message": f"Hero-Zero {self.symbol} {target_strike} {opt_type} at Rs {ask_price:.2f} (Fair: Rs {fair_price:.2f}).",
            },
            action_required=(stage == "READY"),
        )
