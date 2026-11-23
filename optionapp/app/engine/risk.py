"""Risk Manager and Kill Switches (Part A14, F2)."""

from typing import Dict, List, Optional, Tuple
from datetime import datetime, time
from optionapp.app.log import get_logger

logger = get_logger("risk")


class RiskManager:
    def __init__(
        self,
        capital: float = 50000.0,
        daily_loss_limit_pct: float = 0.03,
        max_structure_loss_pct: float = 0.05,
        max_open_trades: int = 3,
        max_trades_per_day: int = 3,
        stale_data_timeout_seconds: int = 60,
    ):
        self.capital = capital
        self.daily_loss_limit_pct = daily_loss_limit_pct
        self.max_structure_loss_pct = max_structure_loss_pct
        self.max_open_trades = max_open_trades
        self.max_trades_per_day = max_trades_per_day
        self.stale_data_timeout_seconds = stale_data_timeout_seconds

        self.daily_realized_pnl = 0.0
        self.trades_taken_today = 0
        self.kill_switch_active = False
        self.kill_switch_reason = ""

    def reset_daily(self):
        self.daily_realized_pnl = 0.0
        self.trades_taken_today = 0
        self.kill_switch_active = False
        self.kill_switch_reason = ""

    def check_new_trade(
        self,
        open_positions: List[Dict[str, any]],
        last_data_ts: Optional[datetime] = None,
        now: Optional[datetime] = None,
    ) -> Tuple[bool, str]:
        """Verify whether a new trade can be initiated under risk boundaries."""
        current_time = now or datetime.now()

        # 1. Kill Switch
        if self.kill_switch_active:
            return False, f"Risk Kill Switch ACTIVE: {self.kill_switch_reason}"

        # 2. Daily Loss Limit (3.0% of total capital)
        max_loss_inr = self.capital * self.daily_loss_limit_pct
        total_open_unrealized = sum(float(p.get("unrealized_pnl", 0.0)) for p in open_positions)
        total_daily_pnl = self.daily_realized_pnl + total_open_unrealized

        if total_daily_pnl <= -max_loss_inr:
            self.kill_switch_active = True
            self.kill_switch_reason = f"Daily hard loss limit hit: -Rs {abs(total_daily_pnl):,.2f} >= -Rs {max_loss_inr:,.2f} (3.0%)."
            logger.critical(self.kill_switch_reason)
            return False, self.kill_switch_reason

        # 3. Max Open Trades Limit
        active_count = len([p for p in open_positions if p.get("status") == "OPEN"])
        if active_count >= self.max_open_trades:
            return False, f"Maximum open trades limit reached ({active_count}/{self.max_open_trades})."

        # 4. Max Trades Per Day Limit
        if self.trades_taken_today >= self.max_trades_per_day:
            return False, f"Maximum trades for today reached ({self.trades_taken_today}/{self.max_trades_per_day})."

        # 5. Stale Data Timeout
        if last_data_ts is not None:
            elapsed = (current_time - last_data_ts).total_seconds()
            if elapsed > self.stale_data_timeout_seconds:
                return False, f"Stale market feed: no updates for {int(elapsed)}s (> {self.stale_data_timeout_seconds}s limit)."

        return True, "Risk validation passed."

    def check_position_stop(self, position: Dict[str, any]) -> Tuple[bool, str]:
        """Check if an individual structure has breached the max 5% structure loss limit."""
        unrealized = float(position.get("unrealized_pnl", 0.0))
        allocated_capital = float(position.get("allocated_capital", self.capital))
        max_loss = allocated_capital * self.max_structure_loss_pct

        if unrealized <= -max_loss:
            return True, f"Structure max loss breached: -Rs {abs(unrealized):,.2f} >= -Rs {max_loss:,.2f} (5%). Emergency exit."

        return False, "Structure within risk limits."
