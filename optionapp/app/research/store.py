"""Research data store for backtests and historical datasets (Part C8)."""

import json
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional
import pandas as pd
from optionapp.app.db import get_db
from optionapp.app.log import get_logger

logger = get_logger("store")


def record_backtest_trade(
    setup_name: str,
    symbol: str,
    entry_date: str,
    exit_date: str,
    entry_price: float,
    exit_price: float,
    pnl: float,
    costs: float,
    legs: Optional[List[Dict[str, Any]]] = None,
):
    """Store simulated backtest trade in SQLite."""
    net_pnl = pnl - costs
    legs_json = json.dumps(legs or [])
    with get_db() as conn:
        conn.execute(
            """
            INSERT INTO backtest_trades (setup_name, symbol, entry_date, exit_date, entry_price, exit_price, pnl, costs, net_pnl, legs_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (setup_name, symbol.upper(), entry_date, exit_date, entry_price, exit_price, pnl, costs, net_pnl, legs_json),
        )


def load_backtest_trades(setup_name: Optional[str] = None) -> pd.DataFrame:
    """Load backtest trades for analysis."""
    with get_db() as conn:
        if setup_name:
            rows = conn.execute(
                "SELECT * FROM backtest_trades WHERE setup_name = ? ORDER BY id ASC",
                (setup_name,),
            ).fetchall()
        else:
            rows = conn.execute("SELECT * FROM backtest_trades ORDER BY id ASC").fetchall()

    if not rows:
        return pd.DataFrame()
    return pd.DataFrame([dict(r) for r in rows])
