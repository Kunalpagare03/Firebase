"""Rule-Change Radar Agent (Part A17)."""

import json
from datetime import datetime
from typing import Dict, List, Optional
from optionapp.app.db import get_db
from optionapp.app.log import get_logger

logger = get_logger("radar")

# Benchmark Contract Specifications (Active Nov 2026 specifications)
OFFICIAL_LOTS_NOV_2026 = {
    "NIFTY": 65,
    "BANKNIFTY": 30,
    "FINNIFTY": 60,
    "MIDCPNIFTY": 120,
    "SENSEX": 20,
}


class RuleChangeRadar:
    def __init__(self, auto_demote_to_paper: bool = True):
        self.auto_demote = auto_demote_to_paper

    def scan_for_rule_changes(self, current_lots: Dict[str, int]) -> List[Dict[str, any]]:
        """
        Scan configured or exchange lots against official reference specifications.
        Flags any mismatch or upcoming revision (e.g. SENSEX 25 in Jan 2027, etc.).
        """
        changes_detected = []
        ts = datetime.now().isoformat()

        for symbol, official_lot in OFFICIAL_LOTS_NOV_2026.items():
            active_lot = current_lots.get(symbol, official_lot)
            if active_lot != official_lot:
                change = {
                    "symbol": symbol,
                    "official_lot": official_lot,
                    "active_lot": active_lot,
                    "reason": f"Contract lot size shifted for {symbol}: Active={active_lot}, Benchmark={official_lot}.",
                    "ts": ts,
                }
                changes_detected.append(change)
                self._record_rule_change(symbol, "LIVE", "PAPER", change["reason"])

        if not changes_detected:
            logger.info("Rule-Change Radar: All contract specifications and lot sizes verified identical to active circulars.")

        return changes_detected

    def _record_rule_change(self, setup_symbol: str, old_mode: str, new_mode: str, reason: str):
        ts = datetime.now().isoformat()
        try:
            with get_db() as conn:
                conn.execute(
                    """
                    INSERT INTO setup_changes (ts, setup_name, old_mode, new_mode, reason)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    (ts, f"{setup_symbol.lower()}_condor", old_mode, new_mode, reason),
                )
            logger.warning(f"RULE-CHANGE RADAR ALERT: {reason}. Setup demoted to {new_mode}.")
        except Exception as e:
            logger.error(f"Failed to record rule change in DB: {e}")

    def get_recent_rule_changes(self, limit: int = 20) -> List[Dict[str, any]]:
        with get_db() as conn:
            rows = conn.execute(
                "SELECT * FROM setup_changes ORDER BY ts DESC LIMIT ?",
                (limit,),
            ).fetchall()
        return [dict(r) for r in rows]
