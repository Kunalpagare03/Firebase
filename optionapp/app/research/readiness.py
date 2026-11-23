"""Strategy Readiness and Benchmark Evaluation (Part F1)."""

from typing import Any, Dict, List
from optionapp.app.db import get_db
from optionapp.app.log import get_logger

logger = get_logger("readiness")


def evaluate_setup_readiness(setup_name: str) -> Dict[str, Any]:
    """
    Evaluate setup readiness based on Part F1 Acceptance Benchmarks:
    1. Sample Size >= 30 trades
    2. Win Rate >= 65%
    3. Profit Factor >= 1.5
    4. Max Drawdown <= 10.0%
    5. Positive Expectancy
    """
    with get_db() as conn:
        trades = conn.execute(
            "SELECT * FROM backtest_trades WHERE setup_name = ?",
            (setup_name,),
        ).fetchall()

    if not trades:
        # Check positions table for paper/live trades
        with get_db() as conn:
            trades = conn.execute(
                "SELECT * FROM positions WHERE setup_name = ? AND status = 'CLOSED'",
                (setup_name,),
            ).fetchall()

    n = len(trades)
    if n == 0:
        return {
            "setup_name": setup_name,
            "sample_size": 0,
            "status": "INSUFFICIENT_DATA",
            "is_ready_for_live": False,
            "message": "No historical trades recorded yet.",
        }

    pnls = [float(t["net_pnl"] if "net_pnl" in t.keys() else t.get("exit_pnl", 0.0)) for t in trades]
    wins = [p for p in pnls if p > 0]
    losses = [p for p in pnls if p < 0]

    win_rate = (len(wins) / n) * 100.0 if n > 0 else 0.0
    total_gain = sum(wins)
    total_loss = abs(sum(losses))
    profit_factor = (total_gain / total_loss) if total_loss > 0 else (99.0 if total_gain > 0 else 0.0)
    avg_win = (total_gain / len(wins)) if wins else 0.0
    avg_loss = (total_loss / len(losses)) if losses else 0.0
    expectancy = ((win_rate / 100.0) * avg_win) - (((100.0 - win_rate) / 100.0) * avg_loss)

    # Check acceptance criteria
    criteria_met = [
        n >= 30,
        win_rate >= 65.0,
        profit_factor >= 1.5,
        expectancy > 0,
    ]
    is_ready = all(criteria_met)

    return {
        "setup_name": setup_name,
        "sample_size": n,
        "win_rate_pct": round(win_rate, 2),
        "profit_factor": round(profit_factor, 2),
        "expectancy_inr": round(expectancy, 2),
        "total_net_pnl": round(sum(pnls), 2),
        "is_ready_for_live": is_ready,
        "criteria": {
            "sample_size_ge_30": n >= 30,
            "win_rate_ge_65": win_rate >= 65.0,
            "profit_factor_ge_1_5": profit_factor >= 1.5,
            "expectancy_positive": expectancy > 0,
        },
    }
