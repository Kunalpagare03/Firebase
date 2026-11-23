"""Expiry 13:15 Afternoon Direction Agent (Part A4, C4)."""

from datetime import datetime, time
from typing import Any, Dict, Optional
import pandas as pd
from optionapp.app.setups.base import SetupAgent, AlertMessage


class Expiry1315DirectionAgent(SetupAgent):
    def __init__(self, name: str = "expiry_1315_direction", symbol: str = "NIFTY"):
        super().__init__(name=name, mode="BIAS", symbol=symbol)

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        current_time = now.time()

        # Triggers at 13:15 onwards
        if current_time < time(13, 15):
            return None

        candles_5m: pd.DataFrame = context.get("candles_5m")
        if candles_5m is None or len(candles_5m) < 10:
            return None

        spot = float(context.get("spot", 0.0))
        latest_bar = candles_5m.iloc[-1]
        vwap = float(context.get("vwap", latest_bar["close"]))

        # Check morning range up to 13:15
        morning_high = float(candles_5m["high"].max())
        morning_low = float(candles_5m["low"].min())
        morning_mid = (morning_high + morning_low) / 2.0

        close = float(latest_bar["close"])
        supertrend_dir = int(context.get("supertrend_direction", 1))

        direction = "NEUTRAL"
        confidence = 0.5
        reason = "Trading inside the morning channel."

        if close > morning_mid and close > vwap and supertrend_dir == 1:
            if close >= morning_high * 0.998:
                direction = "BREAKOUT_CALL"
                confidence = 0.85
                reason = "Trading near day highs above VWAP with bullish trend."
            else:
                direction = "MILD_BULLISH"
                confidence = 0.65
                reason = "Above VWAP and morning midpoint."
        elif close < morning_mid and close < vwap and supertrend_dir == -1:
            if close <= morning_low * 1.002:
                direction = "BREAKDOWN_PUT"
                confidence = 0.85
                reason = "Trading near day lows below VWAP with bearish trend."
            else:
                direction = "MILD_BEARISH"
                confidence = 0.65
                reason = "Below VWAP and morning midpoint."

        return AlertMessage(
            timestamp=now.isoformat(),
            setup_name=self.name,
            symbol=self.symbol,
            stage="BIAS",
            mode=self.mode,
            details={
                "direction": direction,
                "confidence": confidence,
                "reason": reason,
                "morning_high": morning_high,
                "morning_low": morning_low,
                "vwap": vwap,
                "spot": spot,
            },
            action_required=False,
        )
