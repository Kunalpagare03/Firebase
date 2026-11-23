"""Expiry Selling Strategies: Sensex Condor, Nifty Condors A & B, Iron Flies (Part A4, C4)."""

from datetime import datetime, time
from typing import Any, Dict, Optional
from optionapp.app.setups.base import SetupAgent, AlertMessage
from optionapp.app.calendar import calculate_condor_strikes, calculate_ironfly_strikes, get_lot_size
from optionapp.app.log import get_logger

logger = get_logger("expiry_sell")


class ExpiryCondorAgent(SetupAgent):
    def __init__(
        self,
        name: str,
        symbol: str,
        expiry_weekday: int,
        stop_mult: float = 3.0,
        inner_pct: float = 0.005,
        wing_pct: float = 0.015,
        mode: str = "LIVE",
        vix_min: float = 13.0,
    ):
        super().__init__(name=name, mode=mode, symbol=symbol)
        self.expiry_weekday = expiry_weekday
        self.stop_mult = stop_mult
        self.inner_pct = inner_pct
        self.wing_pct = wing_pct
        self.vix_min = vix_min

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        current_time = now.time()

        # Check if today is the expiry day
        if now.weekday() != self.expiry_weekday:
            return None

        # Check entry window: 09:16 AM to 09:20 AM
        if not (time(9, 16) <= current_time <= time(9, 25)):
            return None

        # Check if already traded today
        open_positions = context.get("open_positions", [])
        for pos in open_positions:
            if pos.get("setup_name") == self.name and pos.get("status") == "OPEN":
                return None

        spot = float(context.get("spot", 0.0))
        if spot <= 0:
            return None

        vix = float(context.get("vix", 14.5))

        # Check Low-VIX Rule (< 13.0)
        if vix < self.vix_min:
            return AlertMessage(
                timestamp=now.isoformat(),
                setup_name=self.name,
                symbol=self.symbol,
                stage="CANCELLED",
                mode=self.mode,
                details={
                    "reason": "LOW_VIX_SKIP",
                    "vix": vix,
                    "vix_threshold": self.vix_min,
                    "message": f"India VIX {vix:.2f} < {self.vix_min:.1f}. Rule skips condor to prevent negative expectancy.",
                    "ghost_candidate": True,
                },
                action_required=False,
            )

        # Calculate strikes
        strikes = calculate_condor_strikes(spot, self.symbol, self.inner_pct, self.wing_pct)
        lot_size = get_lot_size(self.symbol)

        return AlertMessage(
            timestamp=now.isoformat(),
            setup_name=self.name,
            symbol=self.symbol,
            stage="READY",
            mode=self.mode,
            details={
                "spot": spot,
                "vix": vix,
                "strikes": strikes,
                "lot_size": lot_size,
                "stop_mult": self.stop_mult,
                "legs": [
                    {"type": "PE", "action": "BUY", "strike": strikes["long_put"], "name": f"{self.symbol} {strikes['long_put']} PE BUY"},
                    {"type": "PE", "action": "SELL", "strike": strikes["short_put"], "name": f"{self.symbol} {strikes['short_put']} PE SELL"},
                    {"type": "CE", "action": "SELL", "strike": strikes["short_call"], "name": f"{self.symbol} {strikes['short_call']} CE SELL"},
                    {"type": "CE", "action": "BUY", "strike": strikes["long_call"], "name": f"{self.symbol} {strikes['long_call']} CE BUY"},
                ],
                "exit_time": "15:15",
                "message": f"Iron Condor READY on {self.symbol}. Short Put {strikes['short_put']}, Short Call {strikes['short_call']}.",
            },
            action_required=True,
        )

    def check_exit(self, position: Dict[str, Any], context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        current_time = now.time()

        # Hard intraday exit at 15:15
        if current_time >= time(15, 15):
            return AlertMessage(
                timestamp=now.isoformat(),
                setup_name=self.name,
                symbol=self.symbol,
                stage="EXIT",
                mode=self.mode,
                details={
                    "reason": "SCHEDULED_EXPIRY_CLOSE",
                    "position_id": position.get("id"),
                    "message": "15:15 PM Expiry Exit Window Reached. Square off all legs.",
                },
                action_required=True,
            )

        # Check per-leg stop loss (3x or 4x on sold legs)
        legs = position.get("legs", [])
        for leg in legs:
            if leg.get("action") == "SELL" and leg.get("status") != "CLOSED":
                entry_price = float(leg.get("entry_price", 0.0))
                current_price = float(leg.get("current_price", entry_price))
                if entry_price > 0 and current_price >= entry_price * self.stop_mult:
                    return AlertMessage(
                        timestamp=now.isoformat(),
                        setup_name=self.name,
                        symbol=self.symbol,
                        stage="EXIT",
                        mode=self.mode,
                        details={
                            "reason": "LEG_STOP_LOSS_HIT",
                            "leg": leg,
                            "entry_price": entry_price,
                            "current_price": current_price,
                            "stop_mult": self.stop_mult,
                            "message": f"Stop loss hit on sold leg {leg.get('strike')} {leg.get('type')}: {current_price:.2f} >= {entry_price * self.stop_mult:.2f} ({self.stop_mult}x).",
                        },
                        action_required=True,
                    )

        return None


class SensexCondorAgent(ExpiryCondorAgent):
    def __init__(self):
        super().__init__(
            name="sensex_condor",
            symbol="SENSEX",
            expiry_weekday=3,  # Thursday
            stop_mult=3.0,
            inner_pct=0.005,
            wing_pct=0.015,
            mode="LIVE",
            vix_min=13.0,
        )


class NiftyCondorAgentA(ExpiryCondorAgent):
    def __init__(self):
        super().__init__(
            name="nifty_condor_a",
            symbol="NIFTY",
            expiry_weekday=1,  # Tuesday
            stop_mult=4.0,
            inner_pct=0.005,
            wing_pct=0.015,
            mode="LIVE",
            vix_min=13.0,
        )


class NiftyCondorAgentB(ExpiryCondorAgent):
    def __init__(self):
        super().__init__(
            name="nifty_condor_b",
            symbol="NIFTY",
            expiry_weekday=1,  # Tuesday
            stop_mult=4.0,
            inner_pct=0.005,
            wing_pct=0.020,
            mode="LIVE",
            vix_min=13.0,
        )


class NiftyCondorNoStopAgent(ExpiryCondorAgent):
    def __init__(self):
        super().__init__(
            name="nifty_condor_nostop",
            symbol="NIFTY",
            expiry_weekday=1,
            stop_mult=999.0,  # No per-leg stop
            inner_pct=0.005,
            wing_pct=0.015,
            mode="PAPER",
            vix_min=13.0,
        )


class IronFlyAgent(SetupAgent):
    def __init__(self, name: str, symbol: str, wing_pct: float = 0.015, mode: str = "PAPER"):
        super().__init__(name=name, mode=mode, symbol=symbol)
        self.wing_pct = wing_pct

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        if not (time(9, 20) <= now.time() <= time(9, 30)):
            return None

        spot = float(context.get("spot", 0.0))
        if spot <= 0:
            return None

        strikes = calculate_ironfly_strikes(spot, self.symbol, self.wing_pct)
        return AlertMessage(
            timestamp=now.isoformat(),
            setup_name=self.name,
            symbol=self.symbol,
            stage="READY",
            mode=self.mode,
            details={
                "spot": spot,
                "strikes": strikes,
                "legs": [
                    {"type": "PE", "action": "BUY", "strike": strikes["long_put"]},
                    {"type": "PE", "action": "SELL", "strike": strikes["short_put"]},
                    {"type": "CE", "action": "SELL", "strike": strikes["short_call"]},
                    {"type": "CE", "action": "BUY", "strike": strikes["long_call"]},
                ],
                "message": f"Iron Fly READY on {self.symbol} at ATM {strikes['short_put']}.",
            },
            action_required=False,
        )
