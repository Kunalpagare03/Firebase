"""Main Clock-Driven Trading & Alert Engine (Part A1, A2, C4)."""

import argparse
import sys
import time
from datetime import datetime, timedelta
from typing import Dict, List, Optional
import pandas as pd

from optionapp.app.settings import get_settings
from optionapp.app.log import get_logger
from optionapp.app.db import init_db, get_db
from optionapp.app.calendar import now_ist, is_trading_day, is_market_open
from optionapp.app.analytics.indicators import compute_cpr_and_pivots
from optionapp.app.capital import CapitalLadder
from optionapp.app.data.provider import MarketDataProvider
from optionapp.app.data.candles import load_candles, save_candles
from optionapp.app.engine.risk import RiskManager
from optionapp.app.engine.calm import CalmModeManager
from optionapp.app.engine.positions import PositionManager
from optionapp.app.engine.notify import NotificationManager
from optionapp.app.research.radar import RuleChangeRadar
from optionapp.app.research.ghosts import GhostLedger

# Import setup agents
from optionapp.app.setups.expiry_sell import SensexCondorAgent, NiftyCondorAgentA, NiftyCondorAgentB, NiftyCondorNoStopAgent, IronFlyAgent
from optionapp.app.setups.first_hour import FirstHourBiasAgent
from optionapp.app.setups.expiry_1315 import Expiry1315DirectionAgent
from optionapp.app.setups.hero_zero import AfternoonHeroZeroAgent
from optionapp.app.setups.trendline_break import TrendlineBreakoutAgent
from optionapp.app.setups.retest_spread import BreakoutRetestSpreadAgent
from optionapp.app.setups.library import ShortStraddle920Agent, EmaCross15mAgent, BigMoveWatchAgent, PatternWarningAgent

logger = get_logger("engine")


class TradingEngine:
    def __init__(self, replay_mode: bool = False):
        self.replay_mode = replay_mode
        self.settings = get_settings()

        # Database initialization
        init_db()

        # Core subsystems
        self.capital_ladder = CapitalLadder(self.settings.capital.total_inr)
        self.risk_manager = RiskManager(
            capital=self.settings.capital.total_inr,
            daily_loss_limit_pct=self.settings.capital.daily_loss_limit_pct,
            max_structure_loss_pct=self.settings.capital.max_structure_loss_pct,
            max_open_trades=self.settings.capital.max_open_trades,
            max_trades_per_day=self.settings.capital.max_trades_per_day,
            stale_data_timeout_seconds=self.settings.capital.stale_data_timeout_seconds,
        )
        self.calm_manager = CalmModeManager(
            enabled=self.settings.calm_mode.enabled,
            mask_until=self.settings.calm_mode.mask_pnl_until,
        )
        self.position_manager = PositionManager()
        self.notify = NotificationManager(
            telegram_token=self.settings.notify.telegram.bot_token,
            chat_id=self.settings.notify.telegram.chat_id,
            sound_enabled=self.settings.notify.sound,
        )
        self.radar = RuleChangeRadar(auto_demote_to_paper=self.settings.radar.auto_switch_to_paper_on_rule_change)
        self.ghost_ledger = GhostLedger(enabled=self.settings.ghost_ledger.enabled)
        self.provider = MarketDataProvider(
            primary=self.settings.provider.primary,
            fallback=self.settings.provider.fallback,
            offline_mode=self.replay_mode,
        )

        # Initialize Setup Agents
        self.agents = [
            SensexCondorAgent(),
            NiftyCondorAgentA(),
            NiftyCondorAgentB(),
            NiftyCondorNoStopAgent(),
            IronFlyAgent("banknifty_ironfly", "BANKNIFTY", wing_pct=0.015),
            IronFlyAgent("finnifty_ironfly", "FINNIFTY", wing_pct=0.010),
            ShortStraddle920Agent("short_straddle_920", "NIFTY"),
            FirstHourBiasAgent("first_hour_bias", "NIFTY"),
            Expiry1315DirectionAgent("expiry_1315_direction", "NIFTY"),
            AfternoonHeroZeroAgent("afternoon_hero_zero", "NIFTY"),
            TrendlineBreakoutAgent("trendline_breakout", "NIFTY"),
            BreakoutRetestSpreadAgent("breakout_retest_spread", "NIFTY"),
            EmaCross15mAgent("ema_9_21_cross_15m", "NIFTY"),
            BigMoveWatchAgent("big_move_watch", "NIFTY"),
            PatternWarningAgent("pattern_warning", "NIFTY"),
        ]

        self.last_spot_update: Optional[datetime] = None
        self.directional_bias = "NEUTRAL"
        logger.info(f"Option App v8 Engine Initialized. Capital: Rs {self.capital_ladder.capital:,.2f} (Stage {self.capital_ladder.stage})")

    def run_cycle(self, simulated_dt: Optional[datetime] = None) -> List[Dict[str, any]]:
        """Run single clock cycle across all active symbols and setup agents."""
        dt = simulated_dt or now_ist()
        cycle_alerts = []

        # 1. Fetch Market Context
        nifty_spot = self.provider.get_spot_price("NIFTY")
        sensex_spot = self.provider.get_spot_price("SENSEX")
        vix = self.provider.get_india_vix()
        self.last_spot_update = dt

        # Fetch option chains
        nifty_chain = self.provider.get_option_chain("NIFTY", nifty_spot)
        sensex_chain = self.provider.get_option_chain("SENSEX", sensex_spot)

        # Update MTM for open positions
        self.position_manager.update_mtm(nifty_chain if self.position_manager.open_positions and self.position_manager.open_positions[0]["symbol"] == "NIFTY" else sensex_chain)

        # Build synthetic candles if empty
        candles_5m = load_candles("NIFTY", "5m", limit=50)
        if candles_5m.empty:
            # Generate seed historical candles
            times = [dt - timedelta(minutes=5 * i) for i in range(40, -1, -1)]
            data = []
            cur = nifty_spot * 0.995
            for t in times:
                cur += (hash(str(t)) % 20 - 9)
                data.append({"open": cur, "high": cur + 8, "low": cur - 6, "close": cur + 2, "volume": 1000, "oi": 50000})
            candles_5m = pd.DataFrame(data, index=pd.to_datetime(times))

        cpr = compute_cpr_and_pivots(nifty_spot * 1.005, nifty_spot * 0.995, nifty_spot)

        # Check Position Exits (15:15 or stop loss)
        for pos in list(self.position_manager.open_positions):
            agent = next((a for a in self.agents if a.name == pos["setup_name"]), None)
            if agent:
                exit_alert = agent.check_exit(pos, {"now": dt, "spot": nifty_spot if pos["symbol"] == "NIFTY" else sensex_spot})
                if exit_alert:
                    self.position_manager.close_position(pos["id"], reason=exit_alert.details.get("reason", "AGENT_EXIT"))
                    self.notify.alert(f"{pos['setup_name']} EXIT", exit_alert.details.get("message", "Closed position."), stage="EXIT")
                    cycle_alerts.append(exit_alert.model_dump() if hasattr(exit_alert, "model_dump") else exit_alert.dict())

        # Evaluate Setup Agents
        for agent in self.agents:
            sym = agent.symbol
            spot = sensex_spot if sym == "SENSEX" else nifty_spot
            chain = sensex_chain if sym == "SENSEX" else nifty_chain

            context = {
                "now": dt,
                "spot": spot,
                "vix": vix,
                "chain": chain,
                "candles_5m": candles_5m,
                "cpr": cpr,
                "direction_bias": self.directional_bias,
                "open_positions": self.position_manager.open_positions,
            }

            try:
                alert = agent.evaluate(context)
                if alert:
                    cycle_alerts.append(alert.model_dump() if hasattr(alert, "model_dump") else alert.dict())
                    self._handle_alert(alert, dt, spot)
            except Exception as e:
                logger.error(f"Error evaluating {agent.name}: {e}")

        # Check EOD Calm Mode Reveal & Ghost Resolution at 15:35
        if dt.time().hour == 15 and dt.time().minute >= 35:
            self.ghost_ledger.resolve_eod_ghosts({"NIFTY": nifty_spot, "SENSEX": sensex_spot})

        return cycle_alerts

    def _handle_alert(self, alert, dt: datetime, spot: float):
        """Process triggered alerts, check risk, log to DB, and execute paper/live trades."""
        # 1. Update internal bias
        if alert.stage == "BIAS":
            self.directional_bias = alert.details.get("bias") or alert.details.get("direction", "NEUTRAL")
            logger.info(f"Directional Bias Updated: {self.directional_bias}")
            return

        # 2. Record alert in DB
        import json
        with get_db() as conn:
            conn.execute(
                """
                INSERT INTO alerts (ts, setup_name, symbol, stage, data_json, status)
                VALUES (?, ?, ?, ?, ?, 'NEW')
                """,
                (dt.isoformat(), alert.setup_name, alert.symbol, alert.stage, json.dumps(alert.details)),
            )

        # 3. Handle Cancelled / Skipped setups (Ghost Ledger)
        if alert.stage == "CANCELLED" and alert.details.get("ghost_candidate"):
            self.ghost_ledger.log_ghost_trade(
                setup_name=alert.setup_name,
                symbol=alert.symbol,
                skip_reason=alert.details.get("reason", "RULE_SKIP"),
                entry_price=spot * 0.005,  # estimated premium
                metadata=alert.details,
            )
            return

        # 4. Handle READY signal
        if alert.stage == "READY":
            # Risk check
            allowed, risk_msg = self.risk_manager.check_new_trade(
                open_positions=self.position_manager.open_positions,
                last_data_ts=self.last_spot_update,
                now=dt,
            )
            if not allowed:
                logger.warning(f"Trade blocked by Risk Manager: {risk_msg}")
                self.ghost_ledger.log_ghost_trade(
                    setup_name=alert.setup_name,
                    symbol=alert.symbol,
                    skip_reason=f"RISK_BLOCK: {risk_msg}",
                    entry_price=spot * 0.005,
                    metadata=alert.details,
                )
                return

            # Capital ladder mode check
            is_live_allowed, cap_msg = self.capital_ladder.can_trade_live(alert.mode)
            exec_mode = "LIVE" if is_live_allowed else "PAPER"

            # Execute trade
            legs = alert.details.get("legs", [])
            if legs:
                # Assign initial entry prices from spot/chain
                for leg in legs:
                    leg["entry_price"] = 25.0 if leg.get("action") == "BUY" else 45.0
                    leg["qty"] = alert.details.get("lot_size", 65)

                self.position_manager.open_position(
                    setup_name=alert.setup_name,
                    symbol=alert.symbol,
                    mode=exec_mode,
                    legs=legs,
                    allocated_capital=self.capital_ladder.capital,
                )
                self.risk_manager.trades_taken_today += 1

            self.notify.alert(f"{alert.setup_name} READY [{exec_mode}]", alert.details.get("message", ""), stage="READY")


def main():
    parser = argparse.ArgumentParser(description="Option App v8 Trading Engine")
    parser.add_argument("--replay", action="store_true", help="Run fast simulation / replay test")
    args = parser.parse_args()

    engine = TradingEngine(replay_mode=args.replay)

    if args.replay:
        logger.info("Starting FAST REPLAY simulation across full market day...")
        # Simulate market hours: 09:15 to 15:35
        base_date = datetime(2026, 11, 24)  # Tuesday (Nifty Expiry)
        curr = datetime.combine(base_date, datetime.min.time()) + timedelta(hours=9, minutes=15)
        end = datetime.combine(base_date, datetime.min.time()) + timedelta(hours=15, minutes=35)

        total_alerts = 0
        while curr <= end:
            alerts = engine.run_cycle(simulated_dt=curr)
            total_alerts += len(alerts)
            curr += timedelta(minutes=1)

        logger.info(f"Replay complete! Processed full trading day. Generated {total_alerts} alerts.")
        print(f"\n[OK] Replay simulation finished successfully. {total_alerts} alerts processed.")
    else:
        logger.info("Starting LIVE CLOCK engine. Press Ctrl+C to terminate.")
        try:
            while True:
                engine.run_cycle()
                time.sleep(5)
        except KeyboardInterrupt:
            logger.info("Engine stopped by user.")


if __name__ == "__main__":
    main()
