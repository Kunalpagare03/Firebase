"""Calm Mode for Expiry Iron Condors (Part A17)."""

from datetime import datetime, time
from typing import Dict, List, Optional
from optionapp.app.db import get_db
from optionapp.app.log import get_logger

logger = get_logger("calm")


class CalmModeManager:
    def __init__(self, enabled: bool = True, mask_until: str = "15:35"):
        self.enabled = enabled
        self.mask_until_time = datetime.strptime(mask_until, "%H:%M").time()

    def is_calm_active(self, now: Optional[datetime] = None) -> bool:
        if not self.enabled:
            return False
        current_time = (now or datetime.now()).time()
        # Active from 09:15 to 15:35
        return time(9, 15) <= current_time < self.mask_until_time

    def mask_position_view(self, position: Dict[str, any], now: Optional[datetime] = None) -> Dict[str, any]:
        """
        Mask P&L for display in Calm Mode.
        Shows structure status, current strikes, and leg stop status, but conceals fluctuating MTM.
        """
        if not self.is_calm_active(now):
            return position

        setup_name = position.get("setup_name", "").lower()
        if "condor" not in setup_name:
            return position

        masked = dict(position)
        masked["unrealized_pnl"] = "CALM MODE ACTIVE"
        masked["pnl_pct"] = "Reveals at 15:35 IST"
        masked["calm_shield"] = True
        return masked

    def log_peek(self, position_id: int, command: str = "/peek", user_id: str = "local") -> Dict[str, any]:
        """Record an explicit user peek into the SQLite peeks table."""
        ts = datetime.now().isoformat()
        try:
            with get_db() as conn:
                conn.execute(
                    "INSERT INTO peeks (ts, position_id, command, user_id) VALUES (?, ?, ?, ?)",
                    (ts, position_id, command, user_id),
                )
            logger.warning(f"Calm Mode PEEK logged for Position #{position_id} by {user_id}")
            return {"status": "LOGGED", "timestamp": ts, "position_id": position_id}
        except Exception as e:
            logger.error(f"Failed to log calm peek: {e}")
            return {"status": "ERROR", "error": str(e)}

    def get_peek_count_today(self, position_id: Optional[int] = None) -> int:
        """Count how many times the user peeked today."""
        today_prefix = datetime.now().strftime("%Y-%m-%d")
        with get_db() as conn:
            if position_id is not None:
                row = conn.execute(
                    "SELECT COUNT(*) as cnt FROM peeks WHERE position_id = ? AND ts LIKE ?",
                    (position_id, f"{today_prefix}%"),
                ).fetchone()
            else:
                row = conn.execute(
                    "SELECT COUNT(*) as cnt FROM peeks WHERE ts LIKE ?",
                    (f"{today_prefix}%",),
                ).fetchone()
        return int(row["cnt"]) if row else 0
