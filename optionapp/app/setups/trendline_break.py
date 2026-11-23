"""3-Touch Trendline Breakout Strategy (Part A4, C4)."""

from datetime import datetime
from typing import Any, Dict, Optional
import pandas as pd
from optionapp.app.setups.base import SetupAgent, AlertMessage


class TrendlineBreakoutAgent(SetupAgent):
    def __init__(self, name: str = "trendline_breakout", symbol: str = "NIFTY"):
        super().__init__(name=name, mode="PAPER", symbol=symbol)

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        candles_5m: pd.DataFrame = context.get("candles_5m")
        if candles_5m is None or len(candles_5m) < 25:
            return None

        h = candles_5m["high"].values
        c = candles_5m["close"].values
        n = len(candles_5m)
        i = n - 1

        # Look for 3 lower swing highs
        idx1, idx2, idx3 = i - 18, i - 10, i - 3
        if idx1 < 0:
            return None

        p1, p2, p3 = h[idx1], h[idx2], h[idx3]
        if p1 > p2 > p3:
            slope = (p2 - p1) / (idx2 - idx1)
            line_val = p1 + slope * (i - idx1)

            # Breakout bar
            if c[i] > line_val and c[i-1] <= line_val:
                return AlertMessage(
                    timestamp=now.isoformat(),
                    setup_name=self.name,
                    symbol=self.symbol,
                    stage="READY",
                    mode=self.mode,
                    details={
                        "breakout_level": float(line_val),
                        "close": float(c[i]),
                        "touches": [float(p1), float(p2), float(p3)],
                        "stop_loss": float(candles_5m["low"].iloc[i]),
                        "target": float(c[i] + (line_val - p3) * 1.5),
                        "message": f"3-Touch Falling Trendline Breakout above {line_val:.1f}.",
                    },
                    action_required=False,
                )

        return None
