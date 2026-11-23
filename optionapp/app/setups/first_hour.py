"""First-Hour Bias Agent (09:15 - 10:15 IST) (Part A4, C4)."""

from datetime import datetime, time
from typing import Any, Dict, Optional
import pandas as pd
from optionapp.app.setups.base import SetupAgent, AlertMessage


class FirstHourBiasAgent(SetupAgent):
    def __init__(self, name: str = "first_hour_bias", symbol: str = "NIFTY"):
        super().__init__(name=name, mode="BIAS", symbol=symbol)

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        current_time = now.time()

        # Evaluates starting at 10:15 AM
        if current_time < time(10, 15):
            return None

        candles_df: pd.DataFrame = context.get("candles_15m") or context.get("candles_5m")
        if candles_df is None or candles_df.empty:
            return None

        # Filter today's morning candles (09:15 to 10:15)
        today_date = now.date()
        try:
            today_candles = candles_df[candles_df.index.date == today_date]
        except Exception:
            today_candles = candles_df

        if len(today_candles) < 3:
            return None

        first_hour_open = float(today_candles["open"].iloc[0])
        first_hour_high = float(today_candles["high"].iloc[:4].max())
        first_hour_low = float(today_candles["low"].iloc[:4].min())
        first_hour_close = float(today_candles["close"].iloc[min(3, len(today_candles)-1)])

        range_total = max(1.0, first_hour_high - first_hour_low)
        close_pos = (first_hour_close - first_hour_low) / range_total

        cpr = context.get("cpr", {})
        cpr_top = float(cpr.get("cpr_top", first_hour_open))
        cpr_bottom = float(cpr.get("cpr_bottom", first_hour_open))

        bias = "NEUTRAL"
        strength = 0.5
        reason = "Choppy first hour range."

        if first_hour_close > first_hour_open and close_pos >= 0.75 and first_hour_close > cpr_top:
            bias = "STRONG_BULLISH"
            strength = 0.85
            reason = "First hour closed near highs above CPR. Strong buying interest."
        elif first_hour_close < first_hour_open and close_pos <= 0.25 and first_hour_close < cpr_bottom:
            bias = "STRONG_BEARISH"
            strength = 0.85
            reason = "First hour closed near lows below CPR. Strong selling pressure."
        elif first_hour_close > first_hour_open:
            bias = "MILD_BULLISH"
            strength = 0.60
            reason = "Green first hour, but closed inside range."
        elif first_hour_close < first_hour_open:
            bias = "MILD_BEARISH"
            strength = 0.60
            reason = "Red first hour, but closed inside range."

        return AlertMessage(
            timestamp=now.isoformat(),
            setup_name=self.name,
            symbol=self.symbol,
            stage="BIAS",
            mode=self.mode,
            details={
                "bias": bias,
                "strength": strength,
                "reason": reason,
                "first_hour_open": first_hour_open,
                "first_hour_high": first_hour_high,
                "first_hour_low": first_hour_low,
                "first_hour_close": first_hour_close,
                "first_hour_range": range_total,
                "cpr_top": cpr_top,
                "cpr_bottom": cpr_bottom,
            },
            action_required=False,
        )
