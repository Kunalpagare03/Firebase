"""Capital Ladder & Risk Management (Part A14)."""

from typing import Dict, Tuple
from optionapp.app.log import get_logger

logger = get_logger("capital")


class CapitalLadder:
    """
    Enforces the capital ladder stages starting from Rs 5,000.
    Stage 0: < 50k INR -> PAPER ONLY
    Stage 1: 50k - 3L INR -> Max 1 lot, 1 open trade, defined risk spreads/condors only
    Stage 2: 3L - 5L INR -> Up to 2 lots, max 2 open trades
    Stage 3: 5L - 10L INR -> Up to 4 lots, max 3 open trades
    Stage 4: 10L+ INR -> Full multi-strategy, max 4 open trades
    """

    def __init__(self, capital_inr: float = 50000.0):
        self.capital = float(capital_inr)
        self.stage = self._calculate_stage(self.capital)

    @staticmethod
    def _calculate_stage(capital: float) -> int:
        if capital < 50000.0:
            return 0
        elif capital < 300000.0:
            return 1
        elif capital < 500000.0:
            return 2
        elif capital < 1000000.0:
            return 3
        else:
            return 4

    def update_capital(self, new_capital: float):
        self.capital = float(new_capital)
        old_stage = self.stage
        self.stage = self._calculate_stage(self.capital)
        if self.stage != old_stage:
            logger.info(f"Capital ladder stage changed: {old_stage} -> {self.stage} (Capital: Rs {self.capital:,.2f})")

    def get_stage_info(self) -> Dict[str, any]:
        descriptions = {
            0: "Stage 0 (< 50k): Strictly PAPER TRADING only. Zero risk to capital.",
            1: "Stage 1 (50k - 3L): Single lot defined-risk spreads/condors only. 1 open trade.",
            2: "Stage 2 (3L - 5L): Up to 2 lots, max 2 open trades.",
            3: "Stage 3 (5L - 10L): Up to 4 lots, max 3 open trades.",
            4: "Stage 4 (10L+): Full allocation, max 4 open trades.",
        }
        max_lots = {0: 0, 1: 1, 2: 2, 3: 4, 4: 10}
        max_trades = {0: 0, 1: 1, 2: 2, 3: 3, 4: 4}

        return {
            "capital": self.capital,
            "stage": self.stage,
            "description": descriptions.get(self.stage, ""),
            "max_lots": max_lots.get(self.stage, 0),
            "max_open_trades": max_trades.get(self.stage, 0),
            "is_live_allowed": self.stage >= 1,
        }

    def can_trade_live(self, setup_mode: str) -> Tuple[bool, str]:
        """Check if live execution is allowed for the setup."""
        if setup_mode.upper() != "LIVE":
            return False, "Setup is configured in PAPER/INFO mode."
        if self.stage < 1:
            return False, f"Capital Rs {self.capital:,.2f} is in Stage 0 (< 50k). Only PAPER execution permitted."
        return True, "Approved for LIVE execution."

    def calculate_lot_size(self, symbol: str, risk_per_lot_inr: float, risk_pct_override: float = 0.015) -> int:
        """
        Calculate allowed lots based on:
        1. Ladder maximum allowed lots
        2. 1.5% max risk per trade
        """
        if self.stage == 0:
            return 1  # 1 lot paper

        stage_max_lots = {1: 1, 2: 2, 3: 4, 4: 10}.get(self.stage, 1)
        max_allowed_risk_inr = self.capital * risk_pct_override

        if risk_per_lot_inr <= 0:
            return 1

        calculated_lots = int(max_allowed_risk_inr // risk_per_lot_inr)
        lots = max(1, min(stage_max_lots, calculated_lots))
        return lots
