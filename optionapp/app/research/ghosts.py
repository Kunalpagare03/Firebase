"""Ghost Ledger: Tracking unexecuted READY signals (Part A17)."""

import json
from datetime import datetime
from typing import Any, Dict, List, Optional
from optionapp.app.db import get_db
from optionapp.app.log import get_logger

logger = get_logger("ghosts")


class GhostLedger:
    def __init__(self, enabled: bool = True):
        self.enabled = enabled

    def log_ghost_trade(
        self,
        setup_name: str,
        symbol: str,
        skip_reason: str,
        entry_price: float,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> Optional[int]:
        """Record an unexecuted trade into SQLite 'ghosts'."""
        if not self.enabled:
            return None

        ts = datetime.now().isoformat()
        meta_json = json.dumps(metadata or {})
        try:
            with get_db() as conn:
                cur = conn.execute(
                    """
                    INSERT INTO ghosts (ts, setup_name, symbol, skip_reason, entry_price, metadata_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (ts, setup_name, symbol.upper(), skip_reason, float(entry_price), meta_json),
                )
                ghost_id = cur.lastrowid
            logger.info(f"Ghost Ledger: Logged #{ghost_id} [{setup_name}] skip reason: {skip_reason} at entry Rs {entry_price:.2f}")
            return ghost_id
        except Exception as e:
            logger.error(f"Failed to record ghost trade: {e}")
            return None

    def resolve_eod_ghosts(self, current_prices: Dict[str, float]) -> int:
        """
        Mark-to-market unresolved ghosts at EOD (15:35 IST).
        Calculates simulated P&L to evaluate whether the skip was favorable.
        """
        resolved_count = 0
        with get_db() as conn:
            unresolved = conn.execute(
                "SELECT * FROM ghosts WHERE resolved = 0"
            ).fetchall()

            for row in unresolved:
                gid = row["id"]
                symbol = row["symbol"]
                entry_price = float(row["entry_price"])
                eod_price = current_prices.get(symbol, entry_price)

                # For option sellers (condors/straddles):
                # P&L = Entry - EOD (decay is profit)
                sim_pnl = round(entry_price - eod_price, 2)

                conn.execute(
                    """
                    UPDATE ghosts
                    SET eod_price = ?, simulated_pnl = ?, resolved = 1
                    WHERE id = ?
                    """,
                    (eod_price, sim_pnl, gid),
                )
                resolved_count += 1
                logger.info(f"Ghost #{gid} resolved: Entry Rs {entry_price:.2f} -> EOD Rs {eod_price:.2f} (Simulated PnL: Rs {sim_pnl:,.2f})")

        return resolved_count

    def get_ghost_summary(self) -> Dict[str, Any]:
        """Aggregate summary of Ghost Ledger results."""
        with get_db() as conn:
            rows = conn.execute("SELECT * FROM ghosts ORDER BY ts DESC").fetchall()

        ghosts = [dict(r) for r in rows]
        total = len(ghosts)
        resolved = [g for g in ghosts if g.get("resolved") == 1]
        
        # Did the rule save money?
        # If simulated PnL was negative, skipping saved us from a loss!
        saved_from_loss = sum(1 for g in resolved if float(g.get("simulated_pnl", 0)) < 0)
        missed_profit = sum(1 for g in resolved if float(g.get("simulated_pnl", 0)) > 0)
        net_saved_pnl = -sum(float(g.get("simulated_pnl", 0)) for g in resolved)

        return {
            "total_ghosts": total,
            "resolved_ghosts": len(resolved),
            "skips_saved_from_loss": saved_from_loss,
            "skips_missed_profit": missed_profit,
            "net_capital_saved_inr": net_saved_pnl,
            "recent_ghosts": ghosts[:15],
        }
