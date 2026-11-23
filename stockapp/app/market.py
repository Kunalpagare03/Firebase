from typing import Dict, Any, Optional
import pandas as pd
from stockapp.app.settings import settings
from stockapp.app.data_prices import get_nifty_and_vix
from stockapp.app.indicators import calculate_all_indicators
from stockapp.app.log import logger

class MarketTrafficLight:
    def __init__(self, mode: Optional[str] = None):
        self.mode = mode or settings.traffic_light.mode

    def evaluate(self, market_breadth: Dict[str, float], offline_mode: bool = False) -> Dict[str, Any]:
        """
        Evaluates the 5 states of the market traffic light.
        """
        data = get_nifty_and_vix(offline_mode=offline_mode)
        nifty_df = data.get("nifty", pd.DataFrame())
        vix_df = data.get("vix", pd.DataFrame())

        nifty_close = 24000.0
        nifty_sma50 = 23800.0
        nifty_sma200 = 23200.0
        vix_val = 14.5

        if not nifty_df.empty and len(nifty_df) >= 50:
            nifty_ind = calculate_all_indicators(nifty_df)
            last_nifty = nifty_ind.iloc[-1]
            nifty_close = float(last_nifty["Close"])
            nifty_sma50 = float(last_nifty["SMA50"])
            nifty_sma200 = float(last_nifty["SMA200"]) if "SMA200" in last_nifty and not pd.isna(last_nifty["SMA200"]) else (nifty_close * 0.95)

        if not vix_df.empty and len(vix_df) > 0:
            vix_val = float(vix_df.iloc[-1]["Close"])

        breadth_sma50 = market_breadth.get("pct_above_sma50", 50.0)
        is_panic = (vix_val >= settings.traffic_light.vix_panic_threshold) or (breadth_sma50 < settings.traffic_light.breadth_panic_threshold)

        above_sma200 = nifty_close > nifty_sma200
        above_sma50 = nifty_close > nifty_sma50

        # State Determination
        if above_sma200 and above_sma50:
            state = 1
            color = "GREEN"
            size_multiplier = 1.0
            description = "Nifty > SMA200 and > SMA50 (Full Bullish Trend)"
        elif above_sma200 and not above_sma50:
            state = 2
            color = "AMBER"
            size_multiplier = 1.0
            description = "Nifty > SMA200 but < SMA50 (Bullish Pullback)"
        elif not above_sma200 and above_sma50:
            state = 3
            color = "AMBER"
            size_multiplier = 0.5
            description = "Nifty < SMA200 but > SMA50 (Counter-trend Rally)"
        elif not above_sma200 and not above_sma50 and is_panic:
            state = 4
            color = "AMBER"
            size_multiplier = 0.5
            description = "Nifty < both & Panic (VIX >= 22 or Breadth < 30% - Capitulation Dip Opportunity)"
        else:
            state = 5
            color = "RED"
            size_multiplier = 0.0
            description = "Nifty < both & No Panic (Downtrend Grind - WAIT, Paper Trade Only)"

        effective_multiplier = size_multiplier if self.mode == "STRICT" else 1.0

        result = {
            "state": state,
            "color": color,
            "raw_size_multiplier": size_multiplier,
            "effective_size_multiplier": effective_multiplier,
            "mode": self.mode,
            "description": description,
            "nifty_close": nifty_close,
            "nifty_sma50": nifty_sma50,
            "nifty_sma200": nifty_sma200,
            "vix": vix_val,
            "breadth_sma50": breadth_sma50,
            "is_panic": is_panic
        }
        logger.info(f"Traffic Light: State {state} [{color}] - {description} (Size: {effective_multiplier})")
        return result
