"""Comprehensive Setup Library for PAPER, INFO, and WARN strategies (Part A4, C4)."""

from datetime import datetime, time
from typing import Any, Dict, List, Optional
import pandas as pd
from optionapp.app.setups.base import SetupAgent, AlertMessage
from optionapp.app.calendar import round_to_strike, get_strike_step
from optionapp.app.analytics.bigmove import evaluate_big_move_watch
from optionapp.app.analytics.patterns import detect_candlestick_patterns, detect_chart_patterns


class ShortStraddle920Agent(SetupAgent):
    """9:20 AM Short Straddle with 25% stop loss per leg (Part A4). Mode: PAPER."""
    def __init__(self, name: str = "short_straddle_920", symbol: str = "NIFTY", stop_pct: float = 0.25):
        super().__init__(name=name, mode="PAPER", symbol=symbol)
        self.stop_pct = stop_pct

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        if not (time(9, 20) <= now.time() <= time(9, 25)):
            return None

        spot = float(context.get("spot", 0.0))
        if spot <= 0:
            return None

        step = get_strike_step(self.symbol)
        atm = round_to_strike(spot, step)

        return AlertMessage(
            timestamp=now.isoformat(),
            setup_name=self.name,
            symbol=self.symbol,
            stage="READY",
            mode=self.mode,
            details={
                "atm_strike": atm,
                "stop_pct": self.stop_pct,
                "legs": [
                    {"type": "CE", "action": "SELL", "strike": atm, "stop_pct": self.stop_pct},
                    {"type": "PE", "action": "SELL", "strike": atm, "stop_pct": self.stop_pct},
                ],
                "message": f"9:20 AM ATM Short Straddle at {atm} READY. Stop 25% per leg.",
            },
            action_required=False,
        )


class EmaCross15mAgent(SetupAgent):
    """15m EMA 9 / 21 Trend Crossover. Mode: PAPER."""
    def __init__(self, name: str = "ema_9_21_cross_15m", symbol: str = "NIFTY"):
        super().__init__(name=name, mode="PAPER", symbol=symbol)

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        candles_15m: pd.DataFrame = context.get("candles_15m")
        if candles_15m is None or len(candles_15m) < 25:
            return None

        ema9 = candles_15m["close"].ewm(span=9, adjust=False).mean()
        ema21 = candles_15m["close"].ewm(span=21, adjust=False).mean()

        # Check for crossover on latest bar
        curr_9 = ema9.iloc[-1]
        curr_21 = ema21.iloc[-1]
        prev_9 = ema9.iloc[-2]
        prev_21 = ema21.iloc[-2]

        if prev_9 <= prev_21 and curr_9 > curr_21:
            return AlertMessage(
                timestamp=now.isoformat(),
                setup_name=self.name,
                symbol=self.symbol,
                stage="READY",
                mode=self.mode,
                details={
                    "type": "BULLISH_CROSS",
                    "ema9": float(curr_9),
                    "ema21": float(curr_21),
                    "message": "15m Bullish Crossover: EMA 9 crossed above EMA 21.",
                },
                action_required=False,
            )
        elif prev_9 >= prev_21 and curr_9 < curr_21:
            return AlertMessage(
                timestamp=now.isoformat(),
                setup_name=self.name,
                symbol=self.symbol,
                stage="READY",
                mode=self.mode,
                details={
                    "type": "BEARISH_CROSS",
                    "ema9": float(curr_9),
                    "ema21": float(curr_21),
                    "message": "15m Bearish Crossover: EMA 9 crossed below EMA 21.",
                },
                action_required=False,
            )

        return None


class BigMoveWatchAgent(SetupAgent):
    """Big-Move Watch Agent (VIX >= 22, Range >= 2.5%, Gap >= 1.2%). Mode: INFO."""
    def __init__(self, name: str = "big_move_watch", symbol: str = "NIFTY"):
        super().__init__(name=name, mode="INFO", symbol=symbol)

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        vix = float(context.get("vix", 14.5))
        spot = float(context.get("spot", 0.0))
        prev_close = float(context.get("prev_close", spot))
        open_price = float(context.get("open", spot))
        high = float(context.get("high", spot))
        low = float(context.get("low", spot))

        result = evaluate_big_move_watch(vix, open_price, high, low, prev_close)
        if result["is_active"]:
            return AlertMessage(
                timestamp=now.isoformat(),
                setup_name=self.name,
                symbol=self.symbol,
                stage="INFO",
                mode=self.mode,
                details=result,
                action_required=False,
            )
        return None


class PatternWarningAgent(SetupAgent):
    """Candlestick and Chart Pattern Warning Agent. Mode: WARN."""
    def __init__(self, name: str = "pattern_warning", symbol: str = "NIFTY"):
        super().__init__(name=name, mode="WARN", symbol=symbol)

    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        now: datetime = context.get("now", datetime.now())
        candles_5m: pd.DataFrame = context.get("candles_5m")
        if candles_5m is None or len(candles_5m) < 10:
            return None

        patterns = detect_candlestick_patterns(candles_5m)
        chart_pats = detect_chart_patterns(candles_5m)
        all_pats = patterns + chart_pats

        if all_pats:
            top_pat = all_pats[0]
            return AlertMessage(
                timestamp=now.isoformat(),
                setup_name=self.name,
                symbol=self.symbol,
                stage="WARN",
                mode=self.mode,
                details={
                    "pattern": top_pat.get("pattern"),
                    "type": top_pat.get("type"),
                    "all_detected": [p.get("pattern") for p in all_pats],
                    "message": f"Pattern detected on 5m: {top_pat.get('pattern')} ({top_pat.get('type')}).",
                },
                action_required=False,
            )

        return None
