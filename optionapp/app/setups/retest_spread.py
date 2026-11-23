"""Breakout Retest Spread Strategy (Part A4, C4)."""

from datetime import datetime
from typing import Any, Dict, Optional
import pandas as pd
from optionapp.app.setups.base import SetupAgent, AlertMessage
from optionapp.app.calendar import round_to_strike, get_strike_step


class BreakoutRetestSpreadAgent(SetupAgent):
    def __init__(self, name: str = "breakout_retest_spread", symbol: str = "NIFTY", lookback: int = 60):
        super().__init__(name=name, mode="PAPER", symbol=symbol)
        self.lookback = lookback

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        candles_5m: pd.DataFrame = context.get("candles_5m")
        if candles_5m is None or len(candles_5m) < self.lookback:
            return None

        h = candles_5m["high"].values
        l = candles_5m["low"].values
        c = candles_5m["close"].values
        n = len(candles_5m)
        i = n - 1

        # Resistance = highest high of previous 60 bars excluding last 5
        resistance = float(h[max(0, i-self.lookback):i-5].max())

        # Check if breakout occurred 3-5 bars ago, and current bar retests and bounces
        recent_high = float(h[i-5:i].max())
        if recent_high > resistance and l[i] <= resistance * 1.001 and c[i] > resistance:
            step = get_strike_step(self.symbol)
            sell_put = round_to_strike(resistance, step)
            buy_put = sell_put - step

            return AlertMessage(
                timestamp=now.isoformat(),
                setup_name=self.name,
                symbol=self.symbol,
                stage="READY",
                mode=self.mode,
                details={
                    "pattern": "Breakout Retest Bull Put Spread",
                    "resistance_level": resistance,
                    "short_put": sell_put,
                    "long_put": buy_put,
                    "legs": [
                        {"type": "PE", "action": "SELL", "strike": sell_put},
                        {"type": "PE", "action": "BUY", "strike": buy_put},
                    ],
                    "message": f"Retest bounce at {resistance:.1f}. Bull Put Spread {sell_put}/{buy_put} READY.",
                },
                action_required=False,
            )

        return None
