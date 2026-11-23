"""Position Manager and Live MTM Tracking (Part C4, C8)."""

import json
from datetime import datetime
from typing import Any, Dict, List, Optional
from optionapp.app.db import get_db
from optionapp.app.log import get_logger
from optionapp.app.analytics.costs import calculate_structure_costs

logger = get_logger("positions")


class PositionManager:
    def __init__(self):
        self.open_positions: List[Dict[str, Any]] = []
        self._load_open_positions()

    def _load_open_positions(self):
        """Load OPEN positions from SQLite."""
        with get_db() as conn:
            rows = conn.execute(
                "SELECT * FROM positions WHERE status = 'OPEN'"
            ).fetchall()
        self.open_positions = []
        for r in rows:
            pos = dict(r)
            pos["legs"] = json.loads(pos["legs_json"]) if pos.get("legs_json") else []
            pos["metadata"] = json.loads(pos["metadata_json"]) if pos.get("metadata_json") else {}
            pos["unrealized_pnl"] = 0.0
            self.open_positions.append(pos)

    def open_position(
        self,
        setup_name: str,
        symbol: str,
        mode: str,
        legs: List[Dict[str, Any]],
        allocated_capital: float = 50000.0,
    ) -> Dict[str, Any]:
        """Create a new OPEN position and persist to DB."""
        ts = datetime.now().isoformat()
        metadata = {"allocated_capital": allocated_capital, "entry_time": ts}

        with get_db() as conn:
            cur = conn.execute(
                """
                INSERT INTO positions (setup_name, symbol, mode, status, entry_ts, legs_json, metadata_json)
                VALUES (?, ?, ?, 'OPEN', ?, ?, ?)
                """,
                (setup_name, symbol.upper(), mode.upper(), ts, json.dumps(legs), json.dumps(metadata)),
            )
            pos_id = cur.lastrowid

        new_pos = {
            "id": pos_id,
            "setup_name": setup_name,
            "symbol": symbol.upper(),
            "mode": mode.upper(),
            "status": "OPEN",
            "entry_ts": ts,
            "legs": legs,
            "metadata": metadata,
            "unrealized_pnl": 0.0,
            "allocated_capital": allocated_capital,
        }
        self.open_positions.append(new_pos)
        logger.info(f"Opened {mode} Position #{pos_id} [{setup_name}] on {symbol} with {len(legs)} legs.")
        return new_pos

    def update_mtm(self, current_chain: Dict[str, Any]) -> float:
        """
        Update current mark-to-market prices and unrealized P&L across all open positions.
        Returns total portfolio unrealized P&L.
        """
        rows = current_chain.get("data", [])
        prices_by_strike = {}
        for r in rows:
            k = float(r.get("strike_price", 0))
            prices_by_strike[(k, "CE")] = float(r.get("CALL", {}).get("ltp", 0.0))
            prices_by_strike[(k, "PE")] = float(r.get("PUT", {}).get("ltp", 0.0))

        total_unrealized = 0.0

        for pos in self.open_positions:
            pos_pnl = 0.0
            for leg in pos["legs"]:
                k = float(leg.get("strike", 0))
                opt_type = leg.get("type", "CE").upper()
                action = leg.get("action", "SELL").upper()
                qty = int(leg.get("qty", 1))
                entry_price = float(leg.get("entry_price", 0.0))

                current_price = prices_by_strike.get((k, opt_type), entry_price)
                leg["current_price"] = current_price

                # P&L calculation:
                # Long (BUY): (Current - Entry) * Qty
                # Short (SELL): (Entry - Current) * Qty
                if action == "BUY":
                    leg_pnl = (current_price - entry_price) * qty
                else:
                    leg_pnl = (entry_price - current_price) * qty

                leg["unrealized_pnl"] = round(leg_pnl, 2)
                pos_pnl += leg_pnl

            pos["unrealized_pnl"] = round(pos_pnl, 2)
            total_unrealized += pos_pnl

        return total_unrealized

    def close_position(self, position_id: int, reason: str = "MANUAL_CLOSE") -> Optional[Dict[str, Any]]:
        """Close an active position, calculate net P&L and transaction costs, and persist."""
        pos = next((p for p in self.open_positions if p["id"] == position_id), None)
        if not pos:
            return None

        ts = datetime.now().isoformat()
        realized_pnl = pos.get("unrealized_pnl", 0.0)

        # Calculate transaction costs
        exchange = "BSE" if pos["symbol"] == "SENSEX" else "NSE"
        costs = calculate_structure_costs(pos["legs"], exchange=exchange)
        net_pnl = realized_pnl - costs

        pos["metadata"]["exit_reason"] = reason
        pos["metadata"]["costs"] = round(costs, 2)
        pos["metadata"]["net_pnl"] = round(net_pnl, 2)

        with get_db() as conn:
            conn.execute(
                """
                UPDATE positions
                SET status = 'CLOSED', exit_ts = ?, exit_pnl = ?, metadata_json = ?
                WHERE id = ?
                """,
                (ts, round(net_pnl, 2), json.dumps(pos["metadata"]), position_id),
            )

        self.open_positions = [p for p in self.open_positions if p["id"] != position_id]
        logger.info(f"Closed Position #{position_id} [{pos['setup_name']}]. Net P&L: Rs {net_pnl:,.2f} (Reason: {reason})")
        return pos
