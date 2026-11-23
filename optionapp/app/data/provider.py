"""Market Data Provider integrating Angel One, Dhan, NSE, and Offline Replay (Part C1)."""

import math
import os
import sys
from datetime import datetime, date
from pathlib import Path
from typing import Any, Dict, List, Optional
import numpy as np
import pandas as pd

from optionapp.app.log import get_logger
from optionapp.app.calendar import round_to_strike, get_strike_step
from optionapp.app.analytics.options_math import black76_price

logger = get_logger("provider")

# Add stock_alert_bot/Option to sys.path if available
OPTION_BOT_DIR = Path(__file__).resolve().parent.parent.parent.parent / "stock_alert_bot" / "Option"
if OPTION_BOT_DIR.exists() and str(OPTION_BOT_DIR) not in sys.path:
    sys.path.insert(0, str(OPTION_BOT_DIR))


class MarketDataProvider:
    def __init__(self, primary: str = "angel", fallback: str = "nse", offline_mode: bool = False):
        self.primary = primary
        self.fallback = fallback
        self.offline_mode = offline_mode
        self._network_offline_until = 0.0
        self._last_spot = {
            "NIFTY": 24200.0,
            "SENSEX": 79500.0,
            "BANKNIFTY": 51500.0,
            "FINNIFTY": 23800.0,
            "INDIA_VIX": 14.5,
        }

    def get_spot_price(self, symbol: str) -> float:
        """Fetch spot price for underlying."""
        sym = symbol.upper()
        now_ts = datetime.now().timestamp()
        if self.offline_mode or now_ts < self._network_offline_until:
            return self._last_spot.get(sym, 24000.0)

        # 1. Try Angel One if configured
        try:
            from brokers.angel_one import get_latest_spot
            spot = get_latest_spot(sym)
            if spot and spot > 0:
                self._last_spot[sym] = float(spot)
                return float(spot)
        except Exception:
            self._network_offline_until = now_ts + 120.0

        # 2. Try Dhan if configured
        try:
            from brokers.dhan import get_latest_spot as get_dhan_spot
            spot = get_dhan_spot(sym)
            if spot and spot > 0:
                self._last_spot[sym] = float(spot)
                return float(spot)
        except Exception:
            pass

        return self._last_spot.get(sym, 24000.0)

    def get_india_vix(self) -> float:
        """Fetch India VIX with caching."""
        now_ts = datetime.now().timestamp()
        if self.offline_mode or now_ts < self._network_offline_until:
            return self._last_spot.get("INDIA_VIX", 14.5)

        if hasattr(self, "_vix_last_fetched") and (now_ts - self._vix_last_fetched < 300):
            return self._last_spot.get("INDIA_VIX", 14.5)

        self._vix_last_fetched = now_ts
        try:
            import yfinance as yf
            vix_ticker = yf.Ticker("^INDIAVIX")
            hist = vix_ticker.history(period="1d")
            if not hist.empty:
                v = float(hist["Close"].iloc[-1])
                self._last_spot["INDIA_VIX"] = v
                return v
        except Exception:
            self._network_offline_until = now_ts + 120.0
        return self._last_spot.get("INDIA_VIX", 14.5)

    def get_option_chain(self, symbol: str, spot: Optional[float] = None) -> Dict[str, Any]:
        """
        Fetch full option chain for symbol.
        Falls back to realistic synthetic generator if market is closed or off-hours.
        """
        sym = symbol.upper()
        current_spot = spot or self.get_spot_price(sym)
        now_ts = datetime.now().timestamp()

        if not self.offline_mode and now_ts >= self._network_offline_until:
            # 1. For SENSEX, try Angel One BFO
            if sym == "SENSEX":
                try:
                    from step1_fetch_angel import get_option_chain_angel
                    chain = get_option_chain_angel(symbol="SENSEX", spot_price=current_spot)
                    if chain and "data" in chain and len(chain["data"]) > 0:
                        return chain
                except Exception:
                    self._network_offline_until = now_ts + 120.0

            # 2. For NIFTY/BANKNIFTY/FINNIFTY, try NSE live fetch
            if sym in ("NIFTY", "BANKNIFTY", "FINNIFTY"):
                try:
                    from step1_fetch import get_option_chain as get_nse_chain
                    chain = get_nse_chain(symbol=sym)
                    if chain and "data" in chain and len(chain["data"]) > 0:
                        return chain
                except Exception:
                    self._network_offline_until = now_ts + 120.0

        # 3. Off-hours / Simulated Option Chain Generator
        return self._generate_synthetic_chain(sym, current_spot)

        # 3. Off-hours / Simulated Option Chain Generator
        return self._generate_synthetic_chain(sym, current_spot)

    def _generate_synthetic_chain(self, symbol: str, spot: float) -> Dict[str, Any]:
        """
        Generate realistic option chain with exact strike steps and Black-76 pricing.
        Used for off-hours development, testing, and replay.
        """
        step = get_strike_step(symbol)
        atm = round_to_strike(spot, step)
        vix = self.get_india_vix()
        iv = vix / 100.0
        t_years = 1.0 / 365.0  # Expiry day assumption

        strikes = [atm + i * step for i in range(-15, 16)]
        rows = []

        for k in strikes:
            c_price = round(max(0.05, black76_price(True, spot, k, t_years, iv, 0.06)), 2)
            p_price = round(max(0.05, black76_price(False, spot, k, t_years, iv, 0.06)), 2)

            # Realistic OI distribution (bell-shaped around ATM + walls at round numbers)
            dist = abs(k - atm) / step
            base_oi = int(max(5000, 150000 * math.exp(-0.15 * dist)))
            c_oi = base_oi if k >= atm else int(base_oi * 0.4)
            p_oi = base_oi if k <= atm else int(base_oi * 0.4)

            # Round number boost for OI walls
            if k % (step * 5) == 0:
                c_oi = int(c_oi * 2.2)
                p_oi = int(p_oi * 2.2)

            rows.append({
                "strike_price": k,
                "CALL": {
                    "ltp": c_price,
                    "oi": c_oi,
                    "volume": int(c_oi * 1.5),
                    "iv": round(iv * 100, 2),
                },
                "PUT": {
                    "ltp": p_price,
                    "oi": p_oi,
                    "volume": int(p_oi * 1.5),
                    "iv": round(iv * 100, 2),
                },
            })

        return {
            "symbol": symbol,
            "spot_price": spot,
            "expiry": "Current Expiry",
            "source": "Synthesized Black-76 Live Engine",
            "timestamp": datetime.now().isoformat(),
            "data": rows,
        }
