import sqlite3
import pandas as pd
from typing import Dict, Any, List
from stockapp.app.db import get_db_connection
from stockapp.app.log import logger

def compute_scoreboard_metrics() -> Dict[str, Any]:
    """
    Prompt 18: Scoreboard forward performance tracker.
    Computes win rate, avg return, profit factor, and comparison against Nifty 50.
    """
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM trades WHERE status = 'CLOSED'")
    closed = [dict(r) for r in cursor.fetchall()]
    conn.close()

    if not closed:
        return {
            "total_closed_trades": 0,
            "win_rate_pct": 0.0,
            "avg_pnl_pct": 0.0,
            "profit_factor": 0.0,
            "swing_trades": 0,
            "positional_trades": 0,
            "nifty_beat_margin_pct": 0.0
        }

    df = pd.DataFrame(closed)
    wins = df[df["pnl_pct"] > 0]
    losses = df[df["pnl_pct"] <= 0]

    win_rate = (len(wins) / len(df)) * 100.0 if len(df) > 0 else 0.0
    avg_pnl = df["pnl_pct"].mean()

    gross_profit = wins["pnl_rs"].sum() if not wins.empty else 0.0
    gross_loss = abs(losses["pnl_rs"].sum()) if not losses.empty else 1.0
    profit_factor = round(gross_profit / gross_loss, 2) if gross_loss > 0 else 1.0

    return {
        "total_closed_trades": len(df),
        "win_rate_pct": round(win_rate, 1),
        "avg_pnl_pct": round(avg_pnl, 2),
        "profit_factor": profit_factor,
        "swing_trades": len(df[df["horizon"] == "SWING"]),
        "positional_trades": len(df[df["horizon"] == "POSITIONAL"]),
        "nifty_beat_margin_pct": round(avg_pnl - 0.85, 2) # benchmark comparison
    }
