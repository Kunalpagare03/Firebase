"""Setup promotion and demotion governance (Part F1)."""

from datetime import datetime
from typing import Dict, Any
from optionapp.app.db import get_db
from optionapp.app.research.readiness import evaluate_setup_readiness
from optionapp.app.log import get_logger

logger = get_logger("promotion")


def promote_setup(setup_name: str, reason: str = "BENCHMARK_CRITERIA_PASSED") -> Dict[str, Any]:
    """Promote a setup from PAPER to LIVE after passing acceptance criteria."""
    readiness = evaluate_setup_readiness(setup_name)
    if not readiness["is_ready_for_live"]:
        return {
            "status": "REJECTED",
            "message": f"Setup {setup_name} does not meet all live criteria.",
            "criteria": readiness["criteria"],
        }

    ts = datetime.now().isoformat()
    with get_db() as conn:
        conn.execute(
            """
            INSERT INTO setup_changes (ts, setup_name, old_mode, new_mode, reason)
            VALUES (?, ?, 'PAPER', 'LIVE', ?)
            """,
            (ts, setup_name, reason),
        )

    logger.info(f"PROMOTION: Setup {setup_name} promoted from PAPER to LIVE!")
    return {"status": "PROMOTED", "setup_name": setup_name, "mode": "LIVE"}


def demote_setup(setup_name: str, reason: str = "MAX_DRAWDOWN_EXCEEDED") -> Dict[str, Any]:
    """Demote a setup from LIVE back to PAPER mode."""
    ts = datetime.now().isoformat()
    with get_db() as conn:
        conn.execute(
            """
            INSERT INTO setup_changes (ts, setup_name, old_mode, new_mode, reason)
            VALUES (?, ?, 'LIVE', 'PAPER', ?)
            """,
            (ts, setup_name, reason),
        )

    logger.warning(f"DEMOTION: Setup {setup_name} demoted to PAPER. Reason: {reason}")
    return {"status": "DEMOTED", "setup_name": setup_name, "mode": "PAPER"}
